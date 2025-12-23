package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.common.utils.WebhookTimestampUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolRecordDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceToolRecordRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceLockService;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.WebhookFailLogService;
import com.weili.iot_portal.service.ingestion.WebhookProcessingStrategy;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceToolEventFields;
import com.weili.iot_portal.service.ingestion.handler.support.WebhookHandlerUtils;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import com.weili.iot_portal.service.shift.model.ShiftDateAndCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.weili.iot_portal.service.ingestion.handler.support.WebhookHandlerUtils.DeviceIdentity;

/**
 * 刀具换刀事件处理器
 * <p>
 * 处理 DEVICE_TOOL_CHANGE 事件，逻辑类似加工状态事件：比对上一个刀具号，关闭旧记录，插入新记录
 * </p>
 * <p>
 * 事件数据要求（eventData）：
 * - previousToolNo: 上一个刀号（TB端通过VALUE_CHANGE检测提供）
 * - currentToolNo: 当前刀号（必填，TB端通过VALUE_CHANGE检测提供）
 * - toolHolderNumber/toolMagazineNo（可选）
 * - toolId/toolType/compensationSnapshot（可选）
 * - timestamp 或 dataTimestamp（毫秒）
 * </p>
 * <p>
 * 字段定义请参考：{@link DeviceToolEventFields}
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceToolChangeEventHandler implements WebhookEventHandler {

    private final DeviceToolRecordRepository deviceToolRecordRepository;
    private final DeviceLockService deviceLockService;
    private final WebhookHandlerUtils webhookHandlerUtils;
    private final WebhookFailLogService webhookFailLogService;
    private final IShiftCalculationService shiftCalculationService;

    @Override
    public boolean supports(String eventType) {
        return DeviceToolEventFields.EVENT_TYPE_CHANGE.equals(eventType);
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.DEVICE_TOOL_CHANGE;
    }

    @Override
    public WebhookProcessingStrategy getProcessingStrategy() {
        // 业务持久化处理：需要写数据库，经过收件箱，支持重试
        return WebhookProcessingStrategy.BUSINESS_PERSISTENT;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        log.info("[Webhook-Handler-DeviceToolChange] 处理设备换刀事件: messageId={}, eventType={}, deviceCode={}",
                request.getMessageId(), request.getEventType(), request.getDeviceCode());

        // 1. 解析事件数据
        EventData eventData = parseEventData(request);

        // 2. 解析设备信息
        DeviceIdentity identity = webhookHandlerUtils.resolveDeviceIdentity(request);

        // 3. 使用分布式锁处理换刀事件
        processToolChangeWithLock(eventData, identity, request);
    }

    // ==================== 数据解析 ====================

    /**
     * 解析事件数据
     * <p>
     * - TB端通过VALUE_CHANGE检测，会提供 previousToolNo/currentToolNo（如果TB端正确设置）
     * - 首次发送时，oldValue为null，newValue有值，TB端可能只设置newValue而不设置currentToolNo
     * - 容错处理：优先使用currentToolNo，如果为空则从newValue或toolNumber中提取
     * </p>
     */
    private EventData parseEventData(WebhookRequest request) {
        Map<String, Object> eventDataMap = request.getEventData();
        if (eventDataMap == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_DATA_EMPTY);
        }

        // 提取刀具编号（参考DeviceStateEventHandler的逻辑）
        // 优先使用TB端提供的currentToolNo，如果为空则从newValue或toolNumber中提取
        Object currentToolNoObj = eventDataMap.get(DeviceToolEventFields.CURRENT_TOOL_NO);
        if (currentToolNoObj == null) {
            // 容错处理：首次发送时，TB端可能只设置newValue而不设置currentToolNo
            currentToolNoObj = eventDataMap.get("newValue");
            if (currentToolNoObj == null) {
                // 尝试从toolNumber字段提取（支持多种命名）
                currentToolNoObj = eventDataMap.get(DeviceToolEventFields.TOOL_NUMBER);
                if (currentToolNoObj == null) {
                    currentToolNoObj = eventDataMap.get(DeviceToolEventFields.TOOL_NO);
                }
                // 如果还是为空，尝试从telemetryData中提取
                if (currentToolNoObj == null && request.getTelemetryData() != null) {
                    currentToolNoObj = request.getTelemetryData().get(DeviceToolEventFields.TOOL_NUMBER);
                    if (currentToolNoObj == null) {
                        currentToolNoObj = request.getTelemetryData().get(DeviceToolEventFields.TOOL_NO);
                    }
                }
            }
        }

        if (currentToolNoObj == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_TOOL_NUMBER_EMPTY);
        }

        // 提取previousToolNo（首次发送时为null是正常的）
        Object previousToolNoObj = eventDataMap.get(DeviceToolEventFields.PREVIOUS_TOOL_NO);
        if (previousToolNoObj == null) {
            // 容错处理：尝试从oldValue中提取
            previousToolNoObj = eventDataMap.get("oldValue");
        }

        // 提取并验证刀具编号（支持数字和字符串）
        String previousToolNo = extractToolNumber(previousToolNoObj);
        String currentToolNo = extractToolNumber(currentToolNoObj);

        if (StringUtils.isBlank(currentToolNo)) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_TOOL_NUMBER_EMPTY);
        }

        // 提取时间戳（毫秒级）
        Long eventTimestamp = WebhookTimestampUtils.extractDeviceTimestamp(
                eventDataMap, request.getTelemetryData(), request.getDataTimestamp(), request.getTimestamp());
        if (eventTimestamp == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_TIMESTAMP_EMPTY);
        }

        // 提取其他可选字段
        String toolHolderNumber = getString(eventDataMap, DeviceToolEventFields.TOOL_HOLDER_NUMBER);
        if (StringUtils.isBlank(toolHolderNumber)) {
            toolHolderNumber = getString(eventDataMap, DeviceToolEventFields.TOOL_MAGAZINE_NO);
        }
        String toolId = getString(eventDataMap, DeviceToolEventFields.TOOL_ID);
        String toolType = getString(eventDataMap, DeviceToolEventFields.TOOL_TYPE);

        // 提取程序名（从 eventData 或 telemetryData 中提取）
        String programName = getString(eventDataMap, "programName");
        if (StringUtils.isBlank(programName)) {
            programName = getString(eventDataMap, "program");
        }
        if (StringUtils.isBlank(programName) && request.getTelemetryData() != null) {
            programName = getString(request.getTelemetryData(), "programName");
            if (StringUtils.isBlank(programName)) {
                programName = getString(request.getTelemetryData(), "program");
            }
        }

        // 提取刀补数据（提取所有以 offset/comp/tool/holder 开头的字段）
        Map<String, Object> compensationSnapshot = extractCompensationSnapshot(eventDataMap, request.getTelemetryData());

        return new EventData(previousToolNo, currentToolNo, eventTimestamp,
                toolHolderNumber, toolId, toolType, programName, compensationSnapshot);
    }

    /**
     * 提取刀具编号
     * 支持数字类型和字符串类型
     *
     * @param toolNoObj 刀具编号对象
     * @return 刀具编号字符串，如果无法识别则返回null
     */
    private String extractToolNumber(Object toolNoObj) {
        if (toolNoObj == null) {
            return null;
        }

        // 如果是字符串，直接返回（去除前后空格）
        if (toolNoObj instanceof String) {
            String toolNoStr = ((String) toolNoObj).trim();
            return toolNoStr.isEmpty() ? null : toolNoStr;
        }

        // 如果是数字类型，转换为字符串
        if (toolNoObj instanceof Number) {
            return String.valueOf(((Number) toolNoObj).longValue());
        }

        // 其他类型，尝试转换为字符串
        String toolNoStr = toolNoObj.toString().trim();
        return toolNoStr.isEmpty() ? null : toolNoStr;
    }

    /**
     * 提取刀补数据快照
     * <p>
     * 提取所有以 offset/comp/tool/holder 开头的字段，保存为 JSON Map
     * 参考 DeviceToolEventHandler.extractCompensationValue 的逻辑
     * </p>
     *
     * @param eventData     事件数据
     * @param telemetryData 遥测数据
     * @return 刀补数据快照（Map），如果没有则返回空 Map
     */
    private Map<String, Object> extractCompensationSnapshot(Map<String, Object> eventData, Map<String, Object> telemetryData) {
        Map<String, Object> snapshot = new HashMap<>();

        // 从 eventData 中提取
        if (eventData != null) {
            eventData.forEach((k, v) -> {
                if (k == null || v == null) {
                    return;
                }
                String key = k.trim();
                // 提取所有补偿相关字段（offset/comp/tool/holder 开头）
                if (DeviceToolEventFields.isCompensationField(key)) {
                    snapshot.put(key, v);
                }
            });
        }

        // 从 telemetryData 中提取（如果 eventData 中没有）
        if (telemetryData != null && snapshot.isEmpty()) {
            telemetryData.forEach((k, v) -> {
                if (k == null || v == null) {
                    return;
                }
                String key = k.trim();
                // 提取所有补偿相关字段（offset/comp/tool/holder 开头）
                if (DeviceToolEventFields.isCompensationField(key)) {
                    snapshot.put(key, v);
                }
            });
        }

        return snapshot.isEmpty() ? null : snapshot;
    }

    // ==================== 换刀处理 ====================

    /**
     * 使用分布式锁处理换刀事件
     */
    private void processToolChangeWithLock(EventData eventData, DeviceIdentity identity, WebhookRequest request) {
        Long deviceInfoId = identity.deviceInfoId();

        // 获取分布式锁
        if (!deviceLockService.tryLockToolChange(deviceInfoId, DeviceToolEventFields.LOCK_TIMEOUT_SECONDS_TOOL_CHANGE)) {
            log.warn("[Webhook-Handler-DeviceToolChange] 获取设备换刀锁失败: deviceInfoId={}, messageId={}",
                    deviceInfoId, request.getMessageId());
            throw new IotPortalException(IotPortalErrorCode.EVENT_TOOL_CHANGE_PROCESSING);
        }

        try {
            // 查询数据库最新记录
            DeviceToolRecordDO latestOngoing = deviceToolRecordRepository.findLatestOngoing(deviceInfoId);
            Optional<DeviceToolRecordDO> latestOngoingOpt = Optional.ofNullable(latestOngoing);

            // 处理换刀事件
            processToolChangeTransition(latestOngoingOpt, eventData, identity, request);
        } finally {
            deviceLockService.unlockToolChange(deviceInfoId);
        }
    }

    /**
     * 处理换刀转换
     */
    private void processToolChangeTransition(Optional<DeviceToolRecordDO> latestOngoingOpt,
                                             EventData eventData,
                                             DeviceIdentity identity,
                                             WebhookRequest request) {
        Long deviceInfoId = identity.deviceInfoId();
        Long orgFactoryId = identity.orgFactoryId();

        String previousToolNo = eventData.previousToolNo();
        String currentToolNo = eventData.currentToolNo();
        long eventTimestamp = eventData.eventTimestamp();

        log.debug("[DeviceToolChangeEventHandler] 处理换刀转换: deviceInfoId={}, previousToolNo={}, currentToolNo={}, timestamp={}",
                deviceInfoId, previousToolNo, currentToolNo, eventTimestamp);

        // 判断处理场景
        TransitionType transitionType = determineTransitionType(latestOngoingOpt, eventData);

        switch (transitionType) {
            case FIRST_RECORD:
                // 数据库无记录（首次记录）
                handleFirstRecord(deviceInfoId, orgFactoryId, eventData);
                break;

            case NORMAL_CHANGE:
                // 正常换刀：previousToolNo匹配数据库记录
                handleNormalToolChange(latestOngoingOpt.get(), orgFactoryId, eventData);
                break;

            case TOOL_UNCHANGED:
                // 刀具未变化（重复的相同刀具事件）
                log.debug("[DeviceToolChangeEventHandler] 刀具未变化，跳过处理: deviceInfoId={}, toolNo={}",
                        deviceInfoId, currentToolNo);
                break;

            case TOOL_MISMATCH:
                // 刀具不匹配（异常情况）
                handleToolMismatch(latestOngoingOpt, eventData, identity, request);
                break;

            case FIRST_CONNECTION:
                // 首次连接（previousToolNo = NULL，但数据库有记录）
                handleFirstConnection(deviceInfoId, orgFactoryId, eventData);
                break;

            default:
                throw new IllegalStateException("未知的换刀转换类型: " + transitionType);
        }
    }

    /**
     * 判断换刀转换类型
     * <p>
     * 1. 先检查数据库是否有记录
     * 2. 如果数据库无记录，判断为 FIRST_RECORD（不管 previousToolNo 是否为空）
     * 3. 如果数据库有记录但 previousToolNo 为空，判断为 FIRST_CONNECTION
     * 4. 如果数据库有记录且 previousToolNo 匹配，判断为 NORMAL_CHANGE
     * 5. 如果数据库有记录但 previousToolNo 不匹配，判断为 TOOL_MISMATCH
     * </p>
     */
    private TransitionType determineTransitionType(Optional<DeviceToolRecordDO> latestOngoingOpt,
                                                   EventData eventData) {
        String previousToolNo = eventData.previousToolNo();
        String currentToolNo = eventData.currentToolNo();

        // 先检查数据库是否有记录
        if (latestOngoingOpt.isEmpty()) {
            // 数据库无记录（首次记录）
            log.debug("[DeviceToolChangeEventHandler] 判断转换类型: FIRST_RECORD (数据库无记录)");
            return TransitionType.FIRST_RECORD;
        }

        // 数据库有记录，检查 previousToolNo
        if (StringUtils.isBlank(previousToolNo)) {
            // 数据库有记录但 previousToolNo 为空（首次连接）
            log.debug("[DeviceToolChangeEventHandler] 判断转换类型: FIRST_CONNECTION (previousToolNo为空)");
            return TransitionType.FIRST_CONNECTION;
        }

        // 刀具未变化
        if (previousToolNo.equals(currentToolNo)) {
            log.debug("[DeviceToolChangeEventHandler] 判断转换类型: TOOL_UNCHANGED (刀具未变化)");
            return TransitionType.TOOL_UNCHANGED;
        }

        DeviceToolRecordDO latestOngoing = latestOngoingOpt.get();
        String dbToolNo = latestOngoing.getToolNo();

        // 检查是否正常匹配
        if (previousToolNo.equals(dbToolNo)) {
            log.debug("[DeviceToolChangeEventHandler] 判断转换类型: NORMAL_CHANGE (正常换刀)");
            return TransitionType.NORMAL_CHANGE;
        }

        // 刀具不匹配
        log.warn("[DeviceToolChangeEventHandler] 判断转换类型: TOOL_MISMATCH (previousToolNo={}, DB记录toolNo={})",
                previousToolNo, dbToolNo);
        return TransitionType.TOOL_MISMATCH;
    }

    // ==================== 换刀处理场景 ====================

    /**
     * 数据库无记录（首次记录）
     * <p>
     * 参考 DeviceStateEventHandler.handleFirstRecord 的逻辑：
     * 直接插入新记录，不管 previousToolNo 是否为空
     * </p>
     */
    private void handleFirstRecord(Long deviceInfoId, Long orgFactoryId, EventData eventData) {
        String currentToolNo = eventData.currentToolNo();
        long eventTimestamp = eventData.eventTimestamp();

        log.info("[DeviceToolChangeEventHandler] 数据库无记录，插入首次刀具记录: deviceInfoId={}, toolNo={}, timestamp={}",
                deviceInfoId, currentToolNo, eventTimestamp);

        // 直接插入新记录
        DeviceToolRecordDO newRecord = createToolRecord(deviceInfoId, orgFactoryId, eventData, eventTimestamp);
        deviceToolRecordRepository.insert(newRecord);

        log.debug("[DeviceToolChangeEventHandler] 插入首次刀具记录: 刀号={}, startTs={}",
                currentToolNo, newRecord.getStartTs());
    }

    /**
     * 正常换刀：previousToolNo匹配数据库记录
     */
    private void handleNormalToolChange(DeviceToolRecordDO latestOngoing, Long orgFactoryId, EventData eventData) {
        Long deviceInfoId = latestOngoing.getDeviceInfoId();
        String dbToolNo = latestOngoing.getToolNo();
        String currentToolNo = eventData.currentToolNo();
        long eventTimestamp = eventData.eventTimestamp();

        log.info("[DeviceToolChangeEventHandler] 正常换刀: deviceInfoId={}, 旧刀号={}, 新刀号={}, timestamp={}",
                deviceInfoId, dbToolNo, currentToolNo, eventTimestamp);

        // 检查时间戳异常
        if (latestOngoing.getStartTs() != null && eventTimestamp < latestOngoing.getStartTs()) {
            handleTimestampAnomaly(latestOngoing, eventData, deviceInfoId, orgFactoryId);
            return;
        }

        // 更新旧刀具记录（时间戳使用毫秒级）
        latestOngoing.setEndTs(eventTimestamp);
        if (latestOngoing.getStartTs() != null) {
            // durationS 使用毫秒级（直接使用毫秒差值）
            long durationMs = eventTimestamp - latestOngoing.getStartTs();
            latestOngoing.setDurationS(durationMs < 0 ? 0L : durationMs);
        }
        // 补充班次信息（如果缺失）
        fillShiftInfoIfMissing(latestOngoing, orgFactoryId);
        deviceToolRecordRepository.updateById(latestOngoing);
        log.debug("[DeviceToolChangeEventHandler] 更新旧刀具记录: 刀号={}, endTs={}, durationS={}",
                dbToolNo, latestOngoing.getEndTs(), latestOngoing.getDurationS());

        // 插入新刀具记录
        DeviceToolRecordDO newRecord = createToolRecord(deviceInfoId, orgFactoryId, eventData, eventTimestamp);
        deviceToolRecordRepository.insert(newRecord);

        log.debug("[DeviceToolChangeEventHandler] 插入新刀具记录: 刀号={}, startTs={}",
                currentToolNo, newRecord.getStartTs());
    }

    /**
     * 首次连接处理
     */
    private void handleFirstConnection(Long deviceInfoId, Long orgFactoryId,
                                       EventData eventData) {
        String currentToolNo = eventData.currentToolNo();
        long eventTimestamp = eventData.eventTimestamp();

        log.info("[DeviceToolChangeEventHandler] 首次连接，开始使用刀具: deviceInfoId={}, toolNo={}, timestamp={}",
                deviceInfoId, currentToolNo, eventTimestamp);

        // 检查是否有未结束的记录（异常情况）
        DeviceToolRecordDO latestOngoing = deviceToolRecordRepository.findLatestOngoing(deviceInfoId);
        if (latestOngoing != null) {
            log.warn("[DeviceToolChangeEventHandler] 首次连接但存在未结束的记录: deviceInfoId={}, 将先结束该记录, toolNo={}, startTs={}",
                    deviceInfoId, latestOngoing.getToolNo(), latestOngoing.getStartTs());
            // 先结束未完成的记录（时间戳使用毫秒级）
            latestOngoing.setEndTs(eventTimestamp);
            if (latestOngoing.getStartTs() != null) {
                // durationS 使用毫秒级（直接使用毫秒差值）
                long durationMs = eventTimestamp - latestOngoing.getStartTs();
                latestOngoing.setDurationS(durationMs < 0 ? 0L : durationMs);
            }
            // 补充班次信息（如果缺失）
            fillShiftInfoIfMissing(latestOngoing, orgFactoryId);
            deviceToolRecordRepository.updateById(latestOngoing);
        }

        // 插入新记录
        DeviceToolRecordDO newRecord = createToolRecord(deviceInfoId, orgFactoryId, eventData, eventTimestamp);
        deviceToolRecordRepository.insert(newRecord);

        log.debug("[DeviceToolChangeEventHandler] 插入新刀具记录: 刀号={}, startTs={}",
                currentToolNo, newRecord.getStartTs());
    }

    /**
     * 刀具不匹配处理
     */
    private void handleToolMismatch(Optional<DeviceToolRecordDO> latestOngoingOpt,
                                    EventData eventData,
                                    DeviceIdentity identity,
                                    WebhookRequest request) {
        Long deviceInfoId = identity.deviceInfoId();
        Long orgFactoryId = identity.orgFactoryId();
        String previousToolNo = eventData.previousToolNo();
        String currentToolNo = eventData.currentToolNo();
        long eventTimestamp = eventData.eventTimestamp();

        log.warn("[DeviceToolChangeEventHandler] 刀具不匹配: deviceInfoId={}, previousToolNo={}, currentToolNo={}",
                deviceInfoId, previousToolNo, currentToolNo);

        if (latestOngoingOpt.isPresent()) {
            // 有进行中的记录
            DeviceToolRecordDO ongoing = latestOngoingOpt.get();
            String dbToolNo = ongoing.getToolNo();

            // 检查时间戳异常
            if (ongoing.getStartTs() != null && eventTimestamp < ongoing.getStartTs()) {
                handleTimestampAnomaly(ongoing, eventData, deviceInfoId, orgFactoryId);
                return;
            }

            // 如果previousToolNo与currentToolNo不同，但数据库记录不匹配，可能是状态不同步
            // 先结束该记录（时间戳使用毫秒级）
            log.warn("[DeviceToolChangeEventHandler] 刀具不匹配，结束进行中记录: DB刀号={}, previousToolNo={}",
                    dbToolNo, previousToolNo);
            ongoing.setEndTs(eventTimestamp);
            if (ongoing.getStartTs() != null) {
                // durationS 使用毫秒级（直接使用毫秒差值）
                long durationMs = eventTimestamp - ongoing.getStartTs();
                ongoing.setDurationS(durationMs < 0 ? 0L : durationMs);
            }
            deviceToolRecordRepository.updateById(ongoing);

            String errorMessage = String.format("刀具不匹配但已修复: DB刀号=%s, previousToolNo=%s, currentToolNo=%s, 已结束进行中记录",
                    dbToolNo, previousToolNo, currentToolNo);
            webhookFailLogService.saveFailLog(request, DeviceToolEventFields.ERROR_TYPE_TOOL_MISMATCH,
                    errorMessage, false);
        } else {
            // 没有进行中的记录
            String errorMessage = String.format("刀具不匹配且无进行中记录: previousToolNo=%s, currentToolNo=%s",
                    previousToolNo, currentToolNo);
            webhookFailLogService.saveFailLog(request, DeviceToolEventFields.ERROR_TYPE_TOOL_MISMATCH,
                    errorMessage, true);
        }

        // 插入新记录
        DeviceToolRecordDO newRecord = createToolRecord(deviceInfoId, orgFactoryId, eventData, eventTimestamp);
        deviceToolRecordRepository.insert(newRecord);

        log.debug("[DeviceToolChangeEventHandler] 插入新刀具记录: 刀号={}, startTs={}",
                currentToolNo, newRecord.getStartTs());
    }

    /**
     * 时间戳异常处理
     */
    private void handleTimestampAnomaly(DeviceToolRecordDO ongoing,
                                        EventData eventData,
                                        Long deviceInfoId,
                                        Long orgFactoryId) {
        long eventTimestamp = eventData.eventTimestamp();
        long gapMs = ongoing.getStartTs() - eventTimestamp;
        long gapSeconds = gapMs / DeviceToolEventFields.MILLIS_TO_SECONDS;

        log.warn("[DeviceToolChangeEventHandler] 时间戳异常: deviceInfoId={}, 事件时间={}, 记录开始时间={}, 差距={}毫秒({}秒)",
                deviceInfoId, eventTimestamp, ongoing.getStartTs(), gapMs, gapSeconds);

        // 先结束异常记录（时间戳使用毫秒级）
        if (ongoing.getEndTs() == null) {
            ongoing.setEndTs(eventTimestamp);
            if (ongoing.getStartTs() != null) {
                // durationS 使用毫秒级（直接使用毫秒差值）
                long durationMs = eventTimestamp - ongoing.getStartTs();
                ongoing.setDurationS(durationMs < 0 ? 0L : durationMs);
            }
            deviceToolRecordRepository.updateById(ongoing);
        }

        // 插入新记录，使用事件时间戳（毫秒级）
        DeviceToolRecordDO newRecord = createToolRecord(deviceInfoId, orgFactoryId, eventData, eventTimestamp);
        deviceToolRecordRepository.insert(newRecord);
    }

    // ==================== 记录创建方法 ====================

    /**
     * 创建刀具记录
     * <p>
     * 参考 DeviceProductionRecordDO.createProductionRecord 的逻辑：
     * - 设置 orgFactoryId（工厂ID）
     * - 时间戳使用毫秒级（startTimestamp 是毫秒级时间戳）
     * - 设置班次信息（根据开始时间计算）
     * </p>
     */
    private DeviceToolRecordDO createToolRecord(Long deviceInfoId, Long orgFactoryId, EventData eventData, long startTimestamp) {
        DeviceToolRecordDO record = new DeviceToolRecordDO();
        record.setDeviceInfoId(deviceInfoId);
        record.setOrgFactoryId(orgFactoryId);
        record.setToolNo(eventData.currentToolNo());
        record.setToolMagazineNo(eventData.toolHolderNumber());
        record.setToolId(eventData.toolId());
        record.setToolType(eventData.toolType());
        // 时间戳使用毫秒级
        // 例如：1704067220000 毫秒
        record.setStartTs(startTimestamp);
        record.setEndTs(null);
        record.setDurationS(null);

        // 设置程序名（如果提供）
        if (StringUtils.isNotBlank(eventData.programName())) {
            record.setProgramName(eventData.programName());
        }

        // 设置刀补数据快照（如果提供）
        if (eventData.compensationSnapshot() != null && !eventData.compensationSnapshot().isEmpty()) {
            record.setCompensationSnapshot(eventData.compensationSnapshot());
        }

        // 设置班次信息
        if (startTimestamp != 0L) {
            try {
                ShiftDateAndCode shiftInfo = shiftCalculationService.getShiftDateAndCode(orgFactoryId, deviceInfoId, startTimestamp);
                record.setShiftDate(shiftInfo.shiftDate());
                record.setShiftCode(shiftInfo.shiftCode());
            } catch (Exception e) {
                log.warn("[DeviceToolChangeEventHandler] 计算班次信息失败: deviceInfoId={}, startTs={}, error={}",
                        deviceInfoId, startTimestamp, e.getMessage());
            }
        }

        return record;
    }

    // ==================== 辅助方法 ====================

    /**
     * 如果班次信息缺失，根据开始时间补充
     * <p>
     * 参考 DeviceStateEventHandler.fillShiftInfoIfMissing 的逻辑
     * </p>
     */
    private void fillShiftInfoIfMissing(DeviceToolRecordDO record, Long factoryId) {
        if (record.getStartTs() != null
                && (record.getShiftDate() == null || record.getShiftCode() == null)) {
            try {
                ShiftDateAndCode shiftInfo = shiftCalculationService.getShiftDateAndCode(
                        factoryId, record.getDeviceInfoId(), record.getStartTs());
                if (record.getShiftDate() == null) {
                    record.setShiftDate(shiftInfo.shiftDate());
                }
                if (record.getShiftCode() == null) {
                    record.setShiftCode(shiftInfo.shiftCode());
                }
            } catch (Exception e) {
                log.warn("[DeviceToolChangeEventHandler] 补充班次信息失败: deviceInfoId={}, startTs={}, error={}",
                        record.getDeviceInfoId(), record.getStartTs(), e.getMessage());
            }
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    // ==================== 内部数据类 ====================

    /**
     * 事件数据
     */
    private record EventData(
            String previousToolNo,      // 上一个刀具编号
            String currentToolNo,      // 当前刀具编号
            long eventTimestamp,        // 事件时间戳（毫秒）
            String toolHolderNumber,   // 刀架编号
            String toolId,             // 刀具ID
            String toolType,           // 刀具类型
            String programName,         // 程序名
            Map<String, Object> compensationSnapshot  // 刀补数据快照（JSON）
    ) {
    }

    /**
     * 换刀转换类型
     */
    private enum TransitionType {
        FIRST_RECORD,       // 数据库无记录（首次记录）
        NORMAL_CHANGE,      // 正常换刀
        TOOL_UNCHANGED,     // 刀具未变化
        TOOL_MISMATCH,      // 刀具不匹配
        FIRST_CONNECTION    // 首次连接（数据库有记录但previousToolNo为空）
    }
}
