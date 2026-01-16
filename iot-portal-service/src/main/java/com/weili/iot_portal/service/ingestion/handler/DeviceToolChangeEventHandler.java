package com.weili.iot_portal.service.ingestion.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.weili.basic.common.util.JsonUtils;
import com.weili.iot_portal.common.enums.TransitionType;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.common.utils.WebhookTimestampUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolCompensationDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolRecordDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceToolCompensationRepository;
import com.weili.iot_portal.dal.repository.device.DeviceToolRecordRepository;
import com.weili.iot_portal.domain.ingestion.DeviceIdentity;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceLockService;
import com.weili.iot_portal.service.cache.DeviceToolCacheService;
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
import java.util.Objects;
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

    /**
     * 去重检查的时间范围（毫秒）
     * 如果存在相同设备ID、相同刀具号、时间戳在此范围内的记录，视为重复记录
     */
    private static final long DEDUPLICATION_TIME_RANGE_MS = 3000L;

    private final DeviceToolRecordRepository deviceToolRecordRepository;
    private final DeviceToolCompensationRepository deviceToolCompensationRepository;
    private final DeviceToolCacheService deviceToolCacheService;
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
        log.info("[Webhook-Handler-DeviceToolChange] 处理设备换刀事件: messageId={}, eventType={}, deviceCode={}",
                request.getMessageId(), request.getEventType(), request.getDeviceCode());

        // 1. 解析事件数据
        EventData eventData = parseEventData(request);
        
        log.info("[DeviceToolChangeEventHandler] 解析事件数据完成: deviceCode={}, currentToolNo={}, previousToolNo={}, compensationSnapshot={}",
                request.getDeviceCode(), eventData.currentToolNo(), eventData.previousToolNo(), 
                eventData.compensationSnapshot() != null ? eventData.compensationSnapshot().keySet() : "null");

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

        // 如果从顶层字段提取到了 holderNumber，且 compensationSnapshot 不为空（有补偿数据），才添加到 compensationSnapshot
        // 注意：只有当 compensationSnapshot 有补偿数据时，才添加 holderNumber，避免创建只有 holderNumber 而没有补偿数据的快照
        // 这样在 createToolRecord 中就能正确设置 toolHolderNo，同时在 tryWriteCompensation 中也能正确提取补偿数据
        if (extractedHolderNumber != null && compensationSnapshot != null && !compensationSnapshot.isEmpty()) {
            // 检查 compensationSnapshot 是否包含补偿数据（不仅仅是 holderNumber）
            boolean hasCompensationData = compensationSnapshot.containsKey(DeviceToolEventFields.COMPENSATION_FIELD) ||
                    compensationSnapshot.keySet().stream().anyMatch(key -> 
                            key != null && DeviceToolEventFields.isCompensationField(key));
            
            if (hasCompensationData && !compensationSnapshot.containsKey(DeviceToolEventFields.HOLDER_NUMBER)) {
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
            log.warn("[DeviceToolChangeEventHandler] 提取补偿快照: eventData和telemetryData都为空");
            return null;
        }
        
        log.info("[DeviceToolChangeEventHandler] 提取补偿快照: sourceData字段={}", sourceData.keySet());

        // 优先检查是否存在 compensation 对象（结构化格式）
        Object compensationObj = sourceData.get(DeviceToolEventFields.COMPENSATION_FIELD);
        if (compensationObj != null) {
            Map<String, Object> compensationMap = null;
            
            if (compensationObj instanceof Map) {
                // 结构化格式：compensation 是对象
                @SuppressWarnings("unchecked")
                Map<String, Object> compMap = (Map<String, Object>) compensationObj;
                compensationMap = compMap;
            } else if (compensationObj instanceof String) {
                // 结构化格式：compensation 是JSON字符串（需要解析）
                try {
                    String jsonStr = ((String) compensationObj).trim();
                    if (!jsonStr.isEmpty() && jsonStr.startsWith("{")) {
                        compensationMap = JsonUtils.parseObject(jsonStr, new TypeReference<Map<String, Object>>() {});
                        if (compensationMap == null || compensationMap.isEmpty()) {
                            log.warn("[DeviceToolChangeEventHandler] 解析compensation JSON字符串为空: {}", jsonStr);
                            compensationMap = null;
                        }
                    } else {
                        log.warn("[DeviceToolChangeEventHandler] compensation JSON字符串格式不正确，期望对象格式: {}", jsonStr);
                    }
                } catch (Exception e) {
                    log.warn("[DeviceToolChangeEventHandler] 解析compensation JSON字符串失败: {}, error={}", 
                            compensationObj, e.getMessage());
                }
            } else {
                log.warn("[DeviceToolChangeEventHandler] compensation字段格式不正确，期望Map或String，实际类型: {}", 
                        compensationObj.getClass().getName());
            }
            
            // 如果成功提取到compensation对象，构建快照
            if (compensationMap != null && !compensationMap.isEmpty()) {
                // 提取 toolNo（统一使用toolNo，与数据库保持一致）
                // 注意：允许值为0，0表示"未使用刀具"，这是一个有效的状态
                Object toolNumberObj = sourceData.get(DeviceToolEventFields.TOOL_NO);
                if (toolNumberObj != null) {
                    snapshot.put(DeviceToolEventFields.TOOL_NO, toolNumberObj);
                }

                // 提取 holderNumber（优先级：compensationMap内部 > sourceData顶层字段）
                // 1. 优先从 compensationMap 内部提取（如果 compensation 是JSON字符串，holderNumber可能在内部）
                Object holderNumberObj = null;
                if (compensationMap.containsKey(DeviceToolEventFields.HOLDER_NUMBER)) {
                    holderNumberObj = compensationMap.get(DeviceToolEventFields.HOLDER_NUMBER);
                }
                
                // 2. 如果 compensationMap 中没有，从 sourceData 顶层字段提取（按优先级：hNo > toolEdgeNumber > dNo > holderNumber）
                if (holderNumberObj == null || isZeroValue(String.valueOf(holderNumberObj))) {
                    holderNumberObj = sourceData.get(DeviceToolEventFields.H_NO);
                }
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

                // 提取完整的 compensation 对象（已解析的Map）
                snapshot.put(DeviceToolEventFields.COMPENSATION_FIELD, compensationMap);

                log.info("[DeviceToolChangeEventHandler] 提取结构化补偿快照: toolNo={}, holderNumber={}, compensation keys={}",
                        toolNumberObj, holderNumberObj, compensationMap.keySet());
                return snapshot;
            }
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

        // 如果提取到了扁平化补偿数据，尝试提取 holderNumber（从 eventData 或 telemetryData 顶层字段）
        if (!snapshot.isEmpty()) {
            // 如果快照中没有 holderNumber，尝试从顶层字段提取
            if (!snapshot.containsKey(DeviceToolEventFields.HOLDER_NUMBER)) {
                Map<String, Object> sourceForHolder = eventData != null ? eventData : telemetryData;
                if (sourceForHolder != null) {
                    Object holderNumberObj = sourceForHolder.get(DeviceToolEventFields.H_NO);
                    if (holderNumberObj == null || isZeroValue(String.valueOf(holderNumberObj))) {
                        holderNumberObj = sourceForHolder.get(DeviceToolEventFields.TOOL_EDGE_NUMBER);
                    }
                    if (holderNumberObj == null || isZeroValue(String.valueOf(holderNumberObj))) {
                        holderNumberObj = sourceForHolder.get(DeviceToolEventFields.D_NO);
                    }
                    if (holderNumberObj == null || isZeroValue(String.valueOf(holderNumberObj))) {
                        holderNumberObj = sourceForHolder.get(DeviceToolEventFields.HOLDER_NUMBER);
                    }
                    if (holderNumberObj != null && !isZeroValue(String.valueOf(holderNumberObj))) {
                        snapshot.put(DeviceToolEventFields.HOLDER_NUMBER, holderNumberObj);
                    }
                }
            }
            log.info("[DeviceToolChangeEventHandler] 提取扁平化补偿快照: keys={}", snapshot.keySet());
        }

        if (snapshot.isEmpty()) {
            log.warn("[DeviceToolChangeEventHandler] 提取补偿快照: 未找到补偿数据，返回null");
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

        log.info("[DeviceToolChangeEventHandler] 处理换刀转换: deviceInfoId={}, previousToolNo={}, currentToolNo={}, timestamp={}",
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
     * <p>
     * 特殊处理：
     * - 如果 previousToolNo 为 "0"（未使用刀具），视为数据库无记录（因为0不会写入记录），判断为 FIRST_RECORD
     * - 如果 currentToolNo 为 "0"（未使用刀具），在各处理方法中不创建新记录，只终止旧记录
     * </p>
     */
    private TransitionType determineTransitionType(Optional<DeviceToolRecordDO> latestOngoingOpt,
                                                   EventData eventData) {
        String previousToolNo = eventData.previousToolNo();
        String currentToolNo = eventData.currentToolNo();

        // 特殊处理：如果 previousToolNo 为 "0"（未使用刀具），视为数据库无记录（因为0不会写入记录）
        if (DeviceToolEventFields.isUnusedTool(previousToolNo)) {
            log.debug("[DeviceToolChangeEventHandler] 判断转换类型: FIRST_RECORD (previousToolNo为0，视为无记录)");
            return TransitionType.FIRST_RECORD;
        }

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
     * 去重检查：检查是否存在相同时间戳的记录，避免并发创建重复记录
     *
     * @param deviceInfoId 设备ID
     * @param currentToolNo 当前刀具号
     * @param eventTimestamp 事件时间戳（毫秒）
     * @param logPrefix 日志前缀（用于区分不同场景，如"时间戳异常但"）
     * @return 如果存在重复记录则返回true，否则返回false
     */
    private boolean checkAndSkipIfDuplicate(Long deviceInfoId, String currentToolNo, long eventTimestamp, String logPrefix) {
        DeviceToolRecordDO existing = deviceToolRecordRepository.findByDeviceIdAndToolNoAndTimeRange(
                deviceInfoId, currentToolNo, eventTimestamp, DEDUPLICATION_TIME_RANGE_MS);
        if (existing != null) {
            long timeDiff = Math.abs(existing.getStartTs() - eventTimestamp);
            log.warn("[DeviceToolChangeEventHandler] {}存在相同时间戳的记录，跳过插入以避免重复: " +
                     "deviceId={}, toolNo={}, eventTimestamp={}, existing.id={}, existing.startTs={}, " +
                     "时间差={}ms",
                     logPrefix, deviceInfoId, currentToolNo, eventTimestamp, existing.getId(), existing.getStartTs(), timeDiff);
            return true;
        }
        return false;
    }

    /**
     * 数据库无记录（首次记录）
     * <p>
     * 参考 DeviceStateEventHandler.handleFirstRecord 的逻辑：
     * 直接插入新记录，不管 previousToolNo 是否为空
     * </p>
     * <p>
     * 特殊处理：
     * - 如果 currentToolNo 为 "0"（未使用刀具），不创建新记录（因为0表示未使用刀具，不会写入device_tool_record表）
     * - 增加去重检查：在插入前检查是否存在相同时间戳的记录（±3秒内），避免并发创建重复记录
     * </p>
     */
    private void handleFirstRecord(Long deviceInfoId, Long orgFactoryId, EventData eventData) {
        String currentToolNo = eventData.currentToolNo();
        long eventTimestamp = eventData.eventTimestamp();

        // 如果 currentToolNo 为 "0"（未使用刀具），不创建新记录
        if (DeviceToolEventFields.isUnusedTool(currentToolNo)) {
            log.debug("[DeviceToolChangeEventHandler] 数据库无记录但刀具号为0（未使用刀具），跳过创建记录: deviceInfoId={}, timestamp={}",
                    deviceInfoId, eventTimestamp);
            return;
        }

        // 去重检查：检查是否存在相同时间戳的记录，避免并发创建重复记录
        if (checkAndSkipIfDuplicate(deviceInfoId, currentToolNo, eventTimestamp, "")) {
            return;
        }

        log.debug("[DeviceToolChangeEventHandler] 数据库无记录，插入首次刀具记录: deviceInfoId={}, toolNo={}, timestamp={}",
                deviceInfoId, currentToolNo, eventTimestamp);

        // 直接插入新记录
        DeviceToolRecordDO newRecord = createToolRecord(deviceInfoId, orgFactoryId, eventData, eventTimestamp);
        deviceToolRecordRepository.insert(newRecord);

        log.debug("[DeviceToolChangeEventHandler] 插入首次刀具记录: 刀号={}, startTs={}",
                currentToolNo, newRecord.getStartTs());
        
        // 尝试写入补偿数据
        tryWriteCompensation(deviceInfoId, orgFactoryId, eventData, eventTimestamp);
    }

    /**
     * 正常换刀：previousToolNo匹配数据库记录
     * <p>
     * 特殊处理：
     * - 如果 currentToolNo 为 "0"（未使用刀具），只终止旧记录，不创建新记录
     * </p>
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

        // 如果 currentToolNo 为 "0"（未使用刀具），不创建新记录
        if (DeviceToolEventFields.isUnusedTool(currentToolNo)) {
            log.debug("[DeviceToolChangeEventHandler] 新刀号为0（未使用刀具），只终止旧记录，不创建新记录: deviceInfoId={}, 旧刀号={}",
                    deviceInfoId, dbToolNo);
            return;
        }

        // 去重检查：检查是否存在相同时间戳的记录，避免并发创建重复记录
        if (checkAndSkipIfDuplicate(deviceInfoId, currentToolNo, eventTimestamp, "")) {
            return;
        }

        // 插入新刀具记录
        DeviceToolRecordDO newRecord = createToolRecord(deviceInfoId, orgFactoryId, eventData, eventTimestamp);
        deviceToolRecordRepository.insert(newRecord);

        log.debug("[DeviceToolChangeEventHandler] 插入新刀具记录: 刀号={}, startTs={}",
                currentToolNo, newRecord.getStartTs());
        
        // 尝试写入补偿数据
        tryWriteCompensation(deviceInfoId, orgFactoryId, eventData, eventTimestamp);
    }

    /**
     * 首次连接处理
     * <p>
     * 修复并发问题：使用锁内已查询的结果，避免重复查询导致的竞态条件
     * </p>
     * <p>
     * 特殊处理：
     * - 如果 currentToolNo 为 "0"（未使用刀具），只终止旧记录，不创建新记录
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

        // 如果 currentToolNo 为 "0"（未使用刀具），不创建新记录
        if (DeviceToolEventFields.isUnusedTool(currentToolNo)) {
            log.debug("[DeviceToolChangeEventHandler] 新刀号为0（未使用刀具），只终止旧记录，不创建新记录: deviceInfoId={}",
                    deviceInfoId);
            return;
        }

        // 去重检查：检查是否存在相同时间戳的记录，避免并发创建重复记录
        if (checkAndSkipIfDuplicate(deviceInfoId, currentToolNo, eventTimestamp, "")) {
            return;
        }

        // 插入新记录
        DeviceToolRecordDO newRecord = createToolRecord(deviceInfoId, orgFactoryId, eventData, eventTimestamp);
        deviceToolRecordRepository.insert(newRecord);

        log.debug("[DeviceToolChangeEventHandler] 插入新刀具记录: 刀号={}, startTs={}",
                currentToolNo, newRecord.getStartTs());
        
        // 尝试写入补偿数据
        tryWriteCompensation(deviceInfoId, orgFactoryId, eventData, eventTimestamp);
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

        // 如果 currentToolNo 为 "0"（未使用刀具），不创建新记录
        if (DeviceToolEventFields.isUnusedTool(currentToolNo)) {
            log.debug("[DeviceToolChangeEventHandler] 新刀号为0（未使用刀具），只终止旧记录，不创建新记录: deviceInfoId={}",
                    deviceInfoId);
            return;
        }

        // 去重检查：检查是否存在相同时间戳的记录，避免并发创建重复记录
        if (checkAndSkipIfDuplicate(deviceInfoId, currentToolNo, eventTimestamp, "")) {
            return;
        }

        // 插入新记录
        DeviceToolRecordDO newRecord = createToolRecord(deviceInfoId, orgFactoryId, eventData, eventTimestamp);
        deviceToolRecordRepository.insert(newRecord);

        log.debug("[DeviceToolChangeEventHandler] 插入新刀具记录: 刀号={}, startTs={}",
                currentToolNo, newRecord.getStartTs());
        
        // 尝试写入补偿数据
        tryWriteCompensation(deviceInfoId, orgFactoryId, eventData, eventTimestamp);
    }

    /**
     * 时间戳异常处理
     * <p>
     * 特殊处理：
     * - 如果 currentToolNo 为 "0"（未使用刀具），只终止旧记录，不创建新记录
     * </p>
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

        // 如果 currentToolNo 为 "0"（未使用刀具），不创建新记录
        String currentToolNo = eventData.currentToolNo();
        if (DeviceToolEventFields.isUnusedTool(currentToolNo)) {
            log.debug("[DeviceToolChangeEventHandler] 时间戳异常但新刀号为0（未使用刀具），只终止旧记录，不创建新记录: deviceInfoId={}",
                    deviceInfoId);
            return;
        }

        // 去重检查：检查是否存在相同时间戳的记录，避免并发创建重复记录
        if (checkAndSkipIfDuplicate(deviceInfoId, currentToolNo, eventTimestamp, "时间戳异常但")) {
            return;
        }

        // 插入新记录，使用事件时间戳（毫秒级）
        DeviceToolRecordDO newRecord = createToolRecord(deviceInfoId, orgFactoryId, eventData, eventTimestamp);
        deviceToolRecordRepository.insert(newRecord);
        
        // 尝试写入补偿数据
        tryWriteCompensation(deviceInfoId, orgFactoryId, eventData, eventTimestamp);
    }

    // ==================== 补偿数据写入方法 ====================

    /**
     * 从 compensationSnapshot 中提取补偿数据
     * <p>
     * 支持的数据格式：
     * 1. 结构化格式（compensation 是对象）：{"compensation": {"geom": {...}, "wear": {...}}, "holderNumber": "29-1"}
     * 2. 结构化格式（compensation 是JSON字符串）：{"compensation": "{\"geom\":{...},\"wear\":{...}}", "holderNumber": "29-1"}
     * 3. 扁平化格式：{"offsetX": 0.5, "offsetY": -0.3, "offsetZ": 0.1, "compX": 0.05, ...}
     * </p>
     *
     * @param compensationSnapshot 补偿数据快照
     * @return 补偿值映射，如果不存在补偿数据返回空Map
     */
    private Map<String, Object> extractCompensationFromSnapshot(Map<String, Object> compensationSnapshot) {
        Map<String, Object> compensation = new HashMap<>();
        
        if (compensationSnapshot == null || compensationSnapshot.isEmpty()) {
            return compensation;
        }
        
        // 1. 优先查找 compensation 字段（结构化格式）
        Object compensationObj = compensationSnapshot.get(DeviceToolEventFields.COMPENSATION_FIELD);
        
        if (compensationObj != null) {
            // 处理结构化补偿数据
            if (compensationObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> compMap = (Map<String, Object>) compensationObj;
                compensation.putAll(compMap);
                log.debug("[DeviceToolChangeEventHandler] 从快照中提取补偿对象格式: {}", compensation.keySet());
                return compensation;
            } else if (compensationObj instanceof String) {
                // 如果补偿数据是JSON字符串，需要先解析
                try {
                    String jsonStr = ((String) compensationObj).trim();
                    if (!jsonStr.isEmpty() && jsonStr.startsWith("{")) {
                        Map<String, Object> compMap = JsonUtils.parseObject(jsonStr, new TypeReference<Map<String, Object>>() {});
                        if (compMap != null && !compMap.isEmpty()) {
                            compensation.putAll(compMap);
                            log.debug("[DeviceToolChangeEventHandler] 从快照中解析JSON字符串补偿对象格式: {}", compensation.keySet());
                            return compensation;
                        }
                    }
                } catch (Exception e) {
                    log.warn("[DeviceToolChangeEventHandler] 解析快照中补偿对象JSON字符串失败: {}, error={}", 
                            compensationObj, e.getMessage());
                }
            } else {
                log.warn("[DeviceToolChangeEventHandler] 快照中补偿数据格式不正确，期望Map或String，实际类型: {}", 
                        compensationObj.getClass().getName());
            }
        }
        
        // 2. 降级到扁平化提取：提取所有以 offset/comp 开头的字段
        compensationSnapshot.forEach((k, v) -> {
            if (k == null || v == null) {
                return;
            }
            String key = k.trim();
            // 排除 holderNumber 字段（不是补偿数据）
            if (!DeviceToolEventFields.HOLDER_NUMBER.equalsIgnoreCase(key) 
                    && !DeviceToolEventFields.TOOL_NO.equalsIgnoreCase(key)
                    && DeviceToolEventFields.isCompensationField(key)) {
                compensation.put(key, v);
            }
        });
        
        if (!compensation.isEmpty()) {
            log.debug("[DeviceToolChangeEventHandler] 从快照中提取扁平化补偿格式: {}", compensation.keySet());
        }
        
        return compensation;
    }

    /**
     * 版本化覆盖：刀补补偿数据的写入逻辑
     * <p>
     * 复用 DeviceToolEventHandler.upsertCompensation 的逻辑和约束
     * </p>
     * 
     * @param deviceId 设备ID
     * @param factoryId 工厂ID
     * @param holderNumber 刀补号
     * @param compValue 刀补值（JSON Map）
     * @param eventTimestamp 事件时间戳（毫秒）
     */
    private void upsertCompensation(Long deviceId, Long factoryId,
                                    String holderNumber, Map<String, Object> compValue, Long eventTimestamp) {
        // 1. 先查Redis缓存（性能优化：减少数据库查询）
        Map<String, Object> cachedCompValue = deviceToolCacheService.getActiveCompensation(deviceId, holderNumber);
        if (cachedCompValue != null && Objects.equals(cachedCompValue, compValue)) {
            log.debug("[DeviceToolChangeEventHandler] 刀补值未变化（缓存命中），跳过写入: deviceId={}, holderNumber={}", 
                    deviceId, holderNumber);
            return;
        }
        
        // 2. 缓存未命中或值不同，查询数据库
        DeviceToolCompensationDO active = deviceToolCompensationRepository.findActive(deviceId, holderNumber);
        
        // 3. 如果找到活跃记录且补偿值相同，更新缓存并跳过写入
        if (active != null && Objects.equals(active.getCompValueJson(), compValue)) {
            // 缓存可能过期或不存在，更新缓存
            deviceToolCacheService.cacheActiveCompensation(deviceId, holderNumber, compValue);
            log.debug("[DeviceToolChangeEventHandler] 刀补值未变化（数据库确认），跳过写入: deviceId={}, holderNumber={}", 
                    deviceId, holderNumber);
            return;
        }

        // 时间戳使用毫秒（数据库存储单位为毫秒）
        long ts = eventTimestamp != null 
                ? eventTimestamp 
                : System.currentTimeMillis();
        
        int nextVersion = DeviceToolEventFields.INITIAL_VERSION;
        
        // 4. 如果找到活跃记录但补偿值不同，关闭旧记录
        if (active != null) {
            log.debug("[DeviceToolChangeEventHandler] 刀补值变化，关闭旧记录并创建新记录: deviceId={}, holderNumber={}, oldVersion={}", 
                    deviceId, holderNumber, active.getVersion());
            // 使用 LambdaUpdateWrapper 仅更新 active 和 end_ts 字段，避免更新其他字段导致唯一约束冲突
            deviceToolCompensationRepository.deactivateById(active.getId(), ts, DeviceToolEventFields.ACTIVE_STATUS_DISABLED);
            nextVersion = (active.getVersion() != null ? active.getVersion() + 1 : DeviceToolEventFields.INITIAL_VERSION);
            // 删除旧缓存（补偿值已变化）
            deviceToolCacheService.deleteActiveCompensation(deviceId, holderNumber);
        } else {
            log.debug("[DeviceToolChangeEventHandler] 首次写入刀补数据: deviceId={}, holderNumber={}", 
                    deviceId, holderNumber);
        }

        // 5. 创建新记录
        DeviceToolCompensationDO record = new DeviceToolCompensationDO();
        record.setDeviceInfoId(deviceId);
        record.setOrgFactoryId(factoryId);
        record.setToolHolderNo(holderNumber);
        record.setCompValueJson(compValue);
        record.setVersion(nextVersion);
        record.setStartTs(ts);
        record.setEndTs(null);  // NULL 表示当前有效
        record.setActive(DeviceToolEventFields.ACTIVE_STATUS_ENABLED);
        deviceToolCompensationRepository.insert(record);
        
        // 6. 同步更新缓存（写入成功后）
        deviceToolCacheService.cacheActiveCompensation(deviceId, holderNumber, compValue);
        
        log.info("[DeviceToolChangeEventHandler] 刀补数据写入成功: deviceId={}, holderNumber={}, version={}", 
                deviceId, holderNumber, nextVersion);
    }

    /**
     * 尝试写入补偿数据（如果满足条件）
     * <p>
     * 写入条件：
     * 1. currentToolNo 不为 "0"（未使用刀具）
     * 2. holderNumber 不为空且不为 "0"
     * 3. compensationSnapshot 不为空
     * 4. 补偿数据不为空（提取后不为空Map）
     * </p>
     *
     * @param deviceInfoId 设备ID
     * @param orgFactoryId 工厂ID
     * @param eventData 事件数据
     * @param eventTimestamp 事件时间戳
     */
    private void tryWriteCompensation(Long deviceInfoId, Long orgFactoryId, EventData eventData, long eventTimestamp) {
        String currentToolNo = eventData.currentToolNo();
        Map<String, Object> compensationSnapshot = eventData.compensationSnapshot();
        
        // 条件1：currentToolNo 不为 "0"（未使用刀具）
        if (DeviceToolEventFields.isUnusedTool(currentToolNo)) {
            log.debug("[DeviceToolChangeEventHandler] 刀具号为0（未使用刀具），跳过补偿表写入: deviceId={}", deviceInfoId);
            return;
        }
        
        // 条件2：compensationSnapshot 不为空
        if (compensationSnapshot == null || compensationSnapshot.isEmpty()) {
            log.warn("[DeviceToolChangeEventHandler] 补偿快照为空，跳过补偿表写入: deviceId={}, currentToolNo={}", 
                    deviceInfoId, currentToolNo);
            return;
        }
        
        log.info("[DeviceToolChangeEventHandler] 尝试写入补偿数据: deviceId={}, currentToolNo={}, compensationSnapshot字段={}", 
                deviceInfoId, currentToolNo, compensationSnapshot.keySet());
        
        // 提取 holderNumber（优先级：compensationSnapshot.holderNumber > toolMagazineNo）
        String holderNumber = null;
        Object holderObj = compensationSnapshot.get(DeviceToolEventFields.HOLDER_NUMBER);
        if (holderObj != null && !isZeroValue(String.valueOf(holderObj))) {
            holderNumber = String.valueOf(holderObj).trim();
        }
        
        // 如果 compensationSnapshot 中没有，尝试从 toolMagazineNo 中提取
        if ((holderNumber == null || isZeroValue(holderNumber)) && StringUtils.isNotBlank(eventData.toolMagazineNo())) {
            String toolMagazineNo = eventData.toolMagazineNo();
            if (!isZeroValue(toolMagazineNo)) {
                holderNumber = toolMagazineNo;
            }
        }
        
        // 条件3：holderNumber 不为空且不为 "0"
        if (StringUtils.isBlank(holderNumber) || isZeroValue(holderNumber)) {
            log.warn("[DeviceToolChangeEventHandler] 刀补号为空或为0，跳过补偿表写入: deviceId={}, currentToolNo={}, compensationSnapshot字段={}", 
                    deviceInfoId, currentToolNo, compensationSnapshot.keySet());
            return;
        }
        
        // 提取补偿数据
        Map<String, Object> compValue = extractCompensationFromSnapshot(compensationSnapshot);
        
        log.info("[DeviceToolChangeEventHandler] 从快照中提取补偿数据: deviceId={}, holderNumber={}, compValue字段={}", 
                deviceInfoId, holderNumber, compValue != null ? compValue.keySet() : "null");
        
        // 条件4：补偿数据不为空
        if (compValue == null || compValue.isEmpty()) {
            log.warn("[DeviceToolChangeEventHandler] 补偿数据为空，跳过补偿表写入: deviceId={}, holderNumber={}, compensationSnapshot字段={}", 
                    deviceInfoId, holderNumber, compensationSnapshot.keySet());
            return;
        }
        
        // 写入补偿表
        // 注意：如果写入失败，抛出异常让事务回滚，确保刀具记录和补偿数据的一致性
        upsertCompensation(deviceInfoId, orgFactoryId, holderNumber, compValue, eventTimestamp);
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
