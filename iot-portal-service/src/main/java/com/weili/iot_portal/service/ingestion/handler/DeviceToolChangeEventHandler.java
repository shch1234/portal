package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.common.enums.TransitionType;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.common.utils.WebhookTimestampUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolRecordDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceToolRecordRepository;
import com.weili.iot_portal.domain.ingestion.DeviceIdentity;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceLockService;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.WebhookFailLogService;
import com.weili.iot_portal.service.ingestion.WebhookProcessingStrategy;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceToolEventFields;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 刀具换刀事件处理器
 * <p>
 * 处理 DEVICE_TOOL_CHANGE 事件，逻辑类似加工状态事件：比对上一个刀具号，关闭旧记录，插入新记录
 * </p>
 * <p>
 * 事件数据要求（eventData）：
 * - previousToolNo: 上一个刀号（TB端通过VALUE_CHANGE检测提供）
 * - currentToolNo: 当前刀号（必填，TB端通过VALUE_CHANGE检测提供）
 * - toolMagazineNo（可选，用于记录刀套号，如果为空则使用 toolNo）
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
    private final com.weili.iot_portal.service.record.TimeRangeRecordHandler timeRangeRecordHandler;
    private final RecordHandlerUtils recordHandlerUtils;

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
        log.debug("[Webhook-Handler-DeviceToolChange] 处理设备换刀事件: messageId={}, eventType={}, deviceCode={}",
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
     * - 容错处理：优先使用currentToolNo，如果为空则从newValue或toolNo中提取
     * </p>
     */
    private EventData parseEventData(WebhookRequest request) {
        Map<String, Object> eventDataMap = request.getEventData();
        if (eventDataMap == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_DATA_EMPTY);
        }

        // 提取刀具编号（参考DeviceStateEventHandler的逻辑）
        // 优先使用TB端提供的currentToolNo，如果为空则从newValue或toolNo中提取
        Object currentToolNoObj = eventDataMap.get(DeviceToolEventFields.CURRENT_TOOL_NO);
        if (currentToolNoObj == null) {
            // 容错处理：首次发送时，TB端可能只设置newValue而不设置currentToolNo
            currentToolNoObj = eventDataMap.get("newValue");
            if (currentToolNoObj == null) {
                // 尝试从toolNo字段提取（统一使用toolNo，与数据库保持一致）
                currentToolNoObj = eventDataMap.get(DeviceToolEventFields.TOOL_NO);
                // 如果还是为空，尝试从telemetryData中提取
                if (currentToolNoObj == null && request.getTelemetryData() != null) {
                    currentToolNoObj = request.getTelemetryData().get(DeviceToolEventFields.TOOL_NO);
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

        // 提取刀补数据（提取所有以 offset/comp 开头的字段）
        Map<String, Object> compensationSnapshot = extractCompensationSnapshot(eventDataMap, request.getTelemetryData());

        // 优先从顶层字段中提取 holderNumber（按优先级 hNo > toolEdgeNumber > dNo > holderNumber）
        // 注意：holderNumber 和 toolMagazineNo 是不同的概念，应该分别提取
        String extractedHolderNumber = null;
        Object holderObj = eventDataMap.get(DeviceToolEventFields.H_NO);
        if (holderObj == null || isZeroValue(String.valueOf(holderObj))) {
            holderObj = eventDataMap.get(DeviceToolEventFields.TOOL_EDGE_NUMBER);
        }
        if (holderObj == null || isZeroValue(String.valueOf(holderObj))) {
            holderObj = eventDataMap.get(DeviceToolEventFields.D_NO);
        }
        if (holderObj == null || isZeroValue(String.valueOf(holderObj))) {
            holderObj = eventDataMap.get(DeviceToolEventFields.HOLDER_NUMBER);
        }
        if (holderObj != null && !isZeroValue(String.valueOf(holderObj))) {
            String holderStr = String.valueOf(holderObj).trim();
            if (!holderStr.isEmpty()) {
                extractedHolderNumber = holderStr;
            }
        }

        // 如果从顶层字段提取到了 holderNumber，添加到 compensationSnapshot
        // 这样在 createToolRecord 中就能正确设置 toolHolderNo
        if (extractedHolderNumber != null) {
            if (compensationSnapshot == null) {
                compensationSnapshot = new HashMap<>();
            }
            if (!compensationSnapshot.containsKey(DeviceToolEventFields.HOLDER_NUMBER)) {
                compensationSnapshot.put(DeviceToolEventFields.HOLDER_NUMBER, extractedHolderNumber);
            }
        }

        // 提取 toolMagazineNo（刀套号）
        String toolMagazineNo = getString(eventDataMap, DeviceToolEventFields.TOOL_MAGAZINE_NO);
        // 如果 toolMagazineNo 为空，使用 currentToolNo 作为备选
        if (StringUtils.isBlank(toolMagazineNo)) {
            toolMagazineNo = currentToolNo;
        }

        return new EventData(previousToolNo, currentToolNo, eventTimestamp,
                toolMagazineNo, toolId, toolType, programName, compensationSnapshot);
    }

    /**
     * 提取刀具编号
     * <p>
     * 支持数字类型和字符串类型
     * 注意：允许值为0，0表示"未使用刀具"，这是一个有效的状态，需要记录到数据库
     * </p>
     *
     * @param toolNoObj 刀具编号对象
     * @return 刀具编号字符串（包括"0"），如果无法识别则返回null
     */
    private String extractToolNumber(Object toolNoObj) {
        if (toolNoObj == null) {
            return null;
        }

        String toolNoStr = null;

        // 如果是字符串，直接使用（去除前后空格）
        if (toolNoObj instanceof String) {
            toolNoStr = ((String) toolNoObj).trim();
            if (toolNoStr.isEmpty()) {
                return null;
            }
        } else if (toolNoObj instanceof Number) {
            // 如果是数字类型，转换为字符串（包括0）
            toolNoStr = String.valueOf(((Number) toolNoObj).longValue());
        } else {
            // 其他类型，尝试转换为字符串
            toolNoStr = toolNoObj.toString().trim();
            if (toolNoStr.isEmpty()) {
                return null;
            }
        }

        // 注意：不再过滤0值，0是一个有效的刀具状态（表示未使用刀具）
        return toolNoStr;
    }

    /**
     * 判断值是否为0（表示未使用）
     * <p>
     * 支持字符串"0"和数字0的判断
     * </p>
     *
     * @param value 值（字符串格式）
     * @return true 如果值为0
     */
    private boolean isZeroValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String trimmed = value.trim();
        // 判断是否为字符串"0"
        if ("0".equals(trimmed)) {
            return true;
        }
        // 尝试解析为数字，判断是否为0
        try {
            double numValue = Double.parseDouble(trimmed);
            return numValue == 0.0;
        } catch (NumberFormatException e) {
            // 不是数字，返回false
            return false;
        }
    }

    /**
     * 提取刀补数据快照
     * <p>
     * 提取策略（优先级从高到低）：
     * 1. 如果存在 compensation 对象，提取 toolNo、holderNumber 和完整的 compensation 对象（结构化格式）
     * 2. 否则，提取所有以 offset/comp 开头的字段（扁平化格式）
     * </p>
     * <p>
     * 存储格式：
     * - 结构化：{"toolNo": "2", "holderNumber": "11", "compensation": {"geom": {...}, "wear": {...}}}
     * - 扁平化：{"offsetX": 0.5, "offsetY": -0.3, ...}
     * </p>
     *
     * @param eventData     事件数据
     * @param telemetryData 遥测数据
     * @return 刀补数据快照（Map），如果没有则返回 null
     */
    private Map<String, Object> extractCompensationSnapshot(Map<String, Object> eventData, Map<String, Object> telemetryData) {
        Map<String, Object> snapshot = new HashMap<>();
        Map<String, Object> sourceData = eventData != null ? eventData : telemetryData;

        if (sourceData == null) {
            return null;
        }

        // 优先检查是否存在 compensation 对象（结构化格式）
        Object compensationObj = sourceData.get(DeviceToolEventFields.COMPENSATION_FIELD);
        if (compensationObj != null && compensationObj instanceof Map) {
            // 结构化格式：提取 toolNo、holderNumber 和 compensation 对象
            @SuppressWarnings("unchecked")
            Map<String, Object> compensationMap = (Map<String, Object>) compensationObj;

            // 提取 toolNo（统一使用toolNo，与数据库保持一致）
            // 注意：允许值为0，0表示"未使用刀具"，这是一个有效的状态
            Object toolNumberObj = sourceData.get(DeviceToolEventFields.TOOL_NO);
            if (toolNumberObj != null) {
                snapshot.put(DeviceToolEventFields.TOOL_NO, toolNumberObj);
            }

            // 提取 holderNumber（按优先级：hNo > toolEdgeNumber > dNo > holderNumber）
            // 过滤0值：如果值为0，表示未使用刀补，不提取
            Object holderNumberObj = sourceData.get(DeviceToolEventFields.H_NO);
            if (holderNumberObj == null || isZeroValue(String.valueOf(holderNumberObj))) {
                holderNumberObj = sourceData.get(DeviceToolEventFields.TOOL_EDGE_NUMBER);
            }
            if (holderNumberObj == null || isZeroValue(String.valueOf(holderNumberObj))) {
                holderNumberObj = sourceData.get(DeviceToolEventFields.D_NO);
            }
            if (holderNumberObj == null || isZeroValue(String.valueOf(holderNumberObj))) {
                holderNumberObj = sourceData.get(DeviceToolEventFields.HOLDER_NUMBER);
            }
            // 最终检查：如果值不为null且不为0，才添加到快照
            if (holderNumberObj != null && !isZeroValue(String.valueOf(holderNumberObj))) {
                snapshot.put(DeviceToolEventFields.HOLDER_NUMBER, holderNumberObj);
            }

            // 提取完整的 compensation 对象
            snapshot.put(DeviceToolEventFields.COMPENSATION_FIELD, compensationMap);

            log.debug("[DeviceToolChangeEventHandler] 提取结构化补偿快照: toolNo={}, holderNumber={}, compensation keys={}",
                    toolNumberObj, holderNumberObj, compensationMap.keySet());
            return snapshot;
        }

        // 降级到扁平化提取：提取所有以 offset/comp 开头的字段
        if (eventData != null) {
            eventData.forEach((k, v) -> {
                if (k == null || v == null) {
                    return;
                }
                String key = k.trim();
                // 提取所有补偿相关字段（offset/comp 开头）
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
                // 提取所有补偿相关字段（offset/comp 开头）
                if (DeviceToolEventFields.isCompensationField(key)) {
                    snapshot.put(key, v);
                }
            });
        }

        if (!snapshot.isEmpty()) {
            log.debug("[DeviceToolChangeEventHandler] 提取扁平化补偿快照: keys={}", snapshot.keySet());
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
            log.debug("[Webhook-Handler-DeviceToolChange] 获取设备换刀锁失败: deviceInfoId={}, messageId={}",
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
            case FIRST_RECORD ->
                // 数据库无记录（首次记录）
                    handleFirstRecord(deviceInfoId, orgFactoryId, eventData);
            case NORMAL_CHANGE ->
                // 正常换刀：previousToolNo匹配数据库记录
                    handleNormalToolChange(latestOngoingOpt.get(), orgFactoryId, eventData);
            case TOOL_UNCHANGED ->
                // 刀具未变化（重复的相同刀具事件）
                    log.debug("[DeviceToolChangeEventHandler] 刀具未变化，跳过处理: deviceInfoId={}, toolNo={}",
                            deviceInfoId, currentToolNo);
            case TOOL_MISMATCH ->
                // 刀具不匹配（异常情况）
                    handleToolMismatch(latestOngoingOpt, eventData, identity, request);
            case FIRST_CONNECTION ->
                // 首次连接（previousToolNo = NULL，但数据库有记录）
                // 修复：使用锁内已查询的结果，避免重复查询导致的并发问题
                    handleFirstConnection(latestOngoingOpt, deviceInfoId, orgFactoryId, eventData);
            default -> throw new IllegalStateException("未知的换刀转换类型: " + transitionType);
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
        log.debug("[DeviceToolChangeEventHandler] 判断转换类型: TOOL_MISMATCH (previousToolNo={}, DB记录toolNo={})",
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

        log.debug("[DeviceToolChangeEventHandler] 数据库无记录，插入首次刀具记录: deviceInfoId={}, toolNo={}, timestamp={}",
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

        log.debug("[DeviceToolChangeEventHandler] 正常换刀: deviceInfoId={}, 旧刀号={}, 新刀号={}, timestamp={}",
                deviceInfoId, dbToolNo, currentToolNo, eventTimestamp);

        // 检查时间戳异常
        if (latestOngoing.getStartTs() != null && eventTimestamp < latestOngoing.getStartTs()) {
            handleTimestampAnomaly(latestOngoing, eventData, deviceInfoId, orgFactoryId);
            return;
        }

        // 使用通用服务更新记录（自动处理过期和跨班次）
        boolean createdNewRecord = timeRangeRecordHandler.updateOngoingRecord(
                latestOngoing,
                eventTimestamp,
                orgFactoryId,
                createToolRecordFactory(latestOngoing),
                createToolRecordUpdater()
        );
        
        if (!createdNewRecord) {
            // 如果未创建新记录（未过期），记录已更新
            log.debug("[DeviceToolChangeEventHandler] 更新旧刀具记录: 刀号={}, endTs={}, durationS={}",
                    dbToolNo, latestOngoing.getEndTs(), latestOngoing.getDurationS());
        }

        // 插入新刀具记录
        DeviceToolRecordDO newRecord = createToolRecord(deviceInfoId, orgFactoryId, eventData, eventTimestamp);
        deviceToolRecordRepository.insert(newRecord);

        log.debug("[DeviceToolChangeEventHandler] 插入新刀具记录: 刀号={}, startTs={}",
                currentToolNo, newRecord.getStartTs());
    }

    /**
     * 首次连接处理
     * <p>
     * 修复并发问题：使用锁内已查询的结果，避免重复查询导致的竞态条件
     * </p>
     */
    private void handleFirstConnection(Optional<DeviceToolRecordDO> latestOngoingOpt,
                                       Long deviceInfoId, Long orgFactoryId,
                                       EventData eventData) {
        String currentToolNo = eventData.currentToolNo();
        long eventTimestamp = eventData.eventTimestamp();

        log.debug("[DeviceToolChangeEventHandler] 首次连接，开始使用刀具: deviceInfoId={}, toolNo={}, timestamp={}",
                deviceInfoId, currentToolNo, eventTimestamp);

        // 使用锁内已查询的结果，避免重复查询导致的并发问题
        if (latestOngoingOpt.isPresent()) {
            DeviceToolRecordDO latestOngoing = latestOngoingOpt.get();
            log.warn("[DeviceToolChangeEventHandler] 首次连接但存在未结束的记录: deviceInfoId={}, 将先结束该记录, toolNo={}, startTs={}",
                    deviceInfoId, latestOngoing.getToolNo(), latestOngoing.getStartTs());
            
            // 使用通用服务更新记录（自动处理过期和跨班次）
            timeRangeRecordHandler.updateOngoingRecord(
                    latestOngoing,
                    eventTimestamp,
                    orgFactoryId,
                    createToolRecordFactory(latestOngoing),
                    createToolRecordUpdater()
            );
        } else {
            // 防御性检查：如果锁内查询为空，但可能存在其他未结束的记录（异常情况）
            // 查询所有未结束的相同刀具号记录，防止数据不一致
            List<DeviceToolRecordDO> allOngoing = deviceToolRecordRepository.findAllOngoingByToolNo(deviceInfoId, currentToolNo);
            if (!allOngoing.isEmpty()) {
                log.debug("[DeviceToolChangeEventHandler] 首次连接但发现{}条未结束的相同刀具记录（异常情况），将全部结束: deviceInfoId={}, toolNo={}",
                        allOngoing.size(), deviceInfoId, currentToolNo);
                for (DeviceToolRecordDO record : allOngoing) {
                    // 使用通用服务更新记录（自动处理过期和跨班次）
                    timeRangeRecordHandler.updateOngoingRecord(
                            record,
                            eventTimestamp,
                            orgFactoryId,
                            createToolRecordFactory(record),
                            createToolRecordUpdater()
                    );
                }
            }
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
            // 使用通用服务更新记录（自动处理过期和跨班次）
            log.warn("[DeviceToolChangeEventHandler] 刀具不匹配，结束进行中记录: DB刀号={}, previousToolNo={}",
                    dbToolNo, previousToolNo);
            timeRangeRecordHandler.updateOngoingRecord(
                    ongoing,
                    eventTimestamp,
                    orgFactoryId,
                    createToolRecordFactory(ongoing),
                    createToolRecordUpdater()
            );

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
        record.setToolMagazineNo(eventData.toolMagazineNo());
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
        // 设置刀补号冗余字段（tool_holder_no）
        // 优先从 compensationSnapshot 中提取 holderNumber
        String holderNumber = null;
        if (eventData.compensationSnapshot() != null) {
            Object holderObj = eventData.compensationSnapshot().get(DeviceToolEventFields.HOLDER_NUMBER);
            if (holderObj != null) {
                holderNumber = String.valueOf(holderObj).trim();
            }
        }
        // 如果 compensationSnapshot 中没有，尝试从 toolMagazineNo 中提取（因为 toolMagazineNo 可能就是 holderNumber）
        if ((holderNumber == null || isZeroValue(holderNumber)) && StringUtils.isNotBlank(eventData.toolMagazineNo())) {
            String toolMagazineNo = eventData.toolMagazineNo();
            if (!isZeroValue(toolMagazineNo)) {
                holderNumber = toolMagazineNo;
            }
        }
        if (holderNumber != null && !isZeroValue(holderNumber)) {
            record.setToolHolderNo(holderNumber);
        }

        // 设置班次信息（使用通用工具类）
        recordHandlerUtils.fillShiftInfoIfMissing(record, orgFactoryId);

        return record;
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建刀具记录的 RecordFactory
     * <p>
     * 用于在跨班次拆分时创建新的刀具记录，保留原记录的所有业务字段
     * </p>
     *
     * @param source 源记录（用于复制业务字段）
     * @return RecordFactory 实例
     */
    private com.weili.iot_portal.service.record.TimeRangeRecordHandler.RecordFactory<DeviceToolRecordDO>
            createToolRecordFactory(DeviceToolRecordDO source) {
        return (deviceId, factoryId, startTs, endTs) -> {
            DeviceToolRecordDO record = new DeviceToolRecordDO();
            record.setDeviceInfoId(deviceId);
            record.setOrgFactoryId(factoryId);
            record.setToolNo(source.getToolNo());
            record.setToolMagazineNo(source.getToolMagazineNo());
            record.setToolId(source.getToolId());
            record.setToolType(source.getToolType());
            record.setProgramName(source.getProgramName());
            record.setCompensationSnapshot(source.getCompensationSnapshot());
            record.setToolHolderNo(source.getToolHolderNo());
            record.setStartTs(startTs);
            record.setEndTs(endTs);
            record.setDurationS(endTs - startTs);
            return record;
        };
    }

    /**
     * 创建刀具记录的 RecordUpdater
     * <p>
     * 用于更新/删除/插入数据库记录
     * </p>
     *
     * @return RecordUpdater 实例
     */
    private com.weili.iot_portal.service.record.TimeRangeRecordHandler.RecordUpdater<DeviceToolRecordDO>
            createToolRecordUpdater() {
        return new com.weili.iot_portal.service.record.TimeRangeRecordHandler.RecordUpdater<DeviceToolRecordDO>() {
            @Override
            public void update(DeviceToolRecordDO record) {
                deviceToolRecordRepository.updateById(record);
            }

            @Override
            public void delete(DeviceToolRecordDO record) {
                deviceToolRecordRepository.deleteById(record.getId());
            }

            @Override
            public void insert(DeviceToolRecordDO record) {
                deviceToolRecordRepository.insert(record);
                log.debug("[DeviceToolChangeEventHandler] 插入截断后的旧刀具记录: toolNo={}, shiftDate={}, shiftCode={}, startTs={}, endTs={}",
                        record.getToolNo(), record.getShiftDate(), record.getShiftCode(),
                        record.getStartTs(), record.getEndTs());
            }
        };
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
            String toolMagazineNo,     // 刀套号（刀具在刀库中的位置，通常与 toolNo 相同）
            String toolId,             // 刀具ID
            String toolType,           // 刀具类型
            String programName,         // 程序名
            Map<String, Object> compensationSnapshot  // 刀补数据快照（JSON）
    ) {
    }
}
