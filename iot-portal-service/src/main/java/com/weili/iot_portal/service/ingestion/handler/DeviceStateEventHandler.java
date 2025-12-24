package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.common.enums.DeviceStateEnum;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.common.utils.DeviceStateUtils;
import com.weili.iot_portal.common.utils.WebhookTimestampUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceLockService;
import com.weili.iot_portal.service.cache.DeviceStateCacheService;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.WebhookFailLogService;
import com.weili.iot_portal.service.ingestion.WebhookProcessingStrategy;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceStateEventFields;
import com.weili.iot_portal.service.ingestion.handler.support.WebhookHandlerUtils;
import com.weili.iot_portal.service.ingestion.support.WebhookInboxService;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import com.weili.iot_portal.service.shift.model.ShiftDateAndCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.weili.iot_portal.service.ingestion.handler.support.WebhookHandlerUtils.DeviceIdentity;

/**
 * 设备状态事件处理器
 * <p>
 * 处理设备状态变化事件，更新设备状态时间线记录。
 * 支持的事件类型：DEVICE_STATE、DEVICE_STATE_HEARTBEAT
 * </p>
 * 
 * <p>
 * 处理流程：
 * 1. 解析事件数据（状态、时间戳等）
 * 2. 获取分布式锁
 * 3. 查询数据库最新状态
 * 4. 根据状态匹配情况处理（首次连接、正常匹配、状态不匹配等）
 * 5. 更新缓存
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceStateEventHandler implements WebhookEventHandler {

    private final DeviceStateRecordRepository stateTimelineRepository;
    private final WebhookFailLogService webhookFailLogService;
    private final DeviceLockService deviceLockService;
    private final DeviceStateCacheService deviceStateCacheService;
    private final IShiftCalculationService shiftCalculationService;
    private final WebhookHandlerUtils webhookHandlerUtils;

    @Override
    public boolean supports(String eventType) {
        return DeviceStateEventFields.EVENT_TYPE.equals(eventType)
                || DeviceStateEventFields.EVENT_TYPE_HEARTBEAT.equals(eventType);
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.DEVICE_STATE;
    }

    // ==================== 主处理方法 ====================

    @Override
    public WebhookProcessingStrategy getProcessingStrategy() {
        // 业务持久化处理：需要写数据库，经过收件箱，支持重试
        return WebhookProcessingStrategy.BUSINESS_PERSISTENT;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        log.info("[Webhook-Handler-DeviceState] 处理设备状态事件: messageId={}, eventType={}, deviceCode={}",
                request.getMessageId(), request.getEventType(), request.getDeviceCode());

        // 心跳事件单独处理
        if (DeviceStateEventFields.EVENT_TYPE_HEARTBEAT.equals(request.getEventType())) {
            handleHeartbeat(request);
            return;
        }

        // 1. 解析事件数据
        EventData eventData = parseEventData(request);
        
        // 2. 解析设备信息
        DeviceIdentity identity = webhookHandlerUtils.resolveDeviceIdentity(request);
        
        // 3. 使用分布式锁处理状态更新
        processStateTransitionWithLock(eventData, identity, request);
    }

    // ==================== 数据解析 ====================

    /**
     * 解析事件数据
     * <p>
     * Portal 只接受 TB 发送的数字编码（0-3）或数字字符串（"0"-"3"）
     * 不接受字符串状态名称（如 "working", "WORKING" 等）
     * </p>
     * <p>
     * 容错处理：
     * - 如果 TB 发送了字符串状态名称（如 "working"），会被转换为 "UNKNOWN"
     * - 如果 TB 发送了超出范围的数字（如 10, 1000），会被转换为 "UNKNOWN"
     * </p>
     */
    private EventData parseEventData(WebhookRequest request) {
        Map<String, Object> eventDataMap = request.getEventData();
        if (eventDataMap == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_DATA_EMPTY);
        }

        // 提取状态值（可能是数字编码或字符串）
        Object previousStateObj = eventDataMap.get(DeviceStateEventFields.PREVIOUS_STATE);
        Object currentStateObj = eventDataMap.get(DeviceStateEventFields.CURRENT_STATE);
        
        if (currentStateObj == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_CURRENT_STATE_EMPTY);
        }

        // 直接使用数字编码进行处理（TB 发送的是数字编码 0-3）
        // 提取并验证数字编码
        Integer previousStateCode = DeviceStateUtils.extractAndValidateStateCode(previousStateObj);
        Integer currentStateCode = DeviceStateUtils.extractAndValidateStateCode(currentStateObj);
        
        if (currentStateCode == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_CURRENT_STATE_EMPTY);
        }

        // 转换为状态名称字符串（用于缓存、日志等需要字符串的地方）
        String previousState = previousStateCode != null 
                ? DeviceStateEnum.fromCode(previousStateCode).name() 
                : null;
        String currentState = DeviceStateEnum.fromCode(currentStateCode).name();

        // convertStateCodeToName 已经处理了验证和转换，直接使用结果
        DeviceStateUtils.StateValidationResult currentStateResult = new DeviceStateUtils.StateValidationResult(
                currentState, false, null);
        DeviceStateUtils.StateValidationResult previousStateResult = previousState != null
                ? new DeviceStateUtils.StateValidationResult(previousState, false, null)
                : null;

        // 提取时间戳
        Long eventTimestamp = WebhookTimestampUtils.extractDeviceTimestamp(
                eventDataMap, request.getTelemetryData(), request.getDataTimestamp(), request.getTimestamp());
        if (eventTimestamp == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_TIMESTAMP_EMPTY);
        }

        // 记录时间戳提取结果（用于调试）
        logTimestampExtraction(eventDataMap, request, eventTimestamp);

        return new EventData(previousState, currentState, previousStateCode, currentStateCode, 
                eventTimestamp, currentStateResult, previousStateResult);
    }


    /**
     * 记录时间戳提取结果（用于调试）
     */
    private void logTimestampExtraction(Map<String, Object> eventDataMap, WebhookRequest request, Long eventTimestamp) {
        Long eventDataTs = WebhookTimestampUtils.extractTimestamp(eventDataMap);
        Long telemetryDataTs = WebhookTimestampUtils.extractTimestamp(request.getTelemetryData());
        String timestampSource = (eventDataTs != null && eventTimestamp.equals(eventDataTs)) ? "eventData" :
                ((telemetryDataTs != null && eventTimestamp.equals(telemetryDataTs)) ? "telemetryData" :
                ((request.getDataTimestamp() != null && eventTimestamp.equals(request.getDataTimestamp())) ? "dataTimestamp" : "timestamp"));
        log.debug("[DeviceStateEventHandler] 时间戳提取结果: eventTimestamp={}, 来源={}, eventData.timestamp={}, telemetryData.timestamp={}, request.dataTimestamp={}, request.timestamp={}",
                eventTimestamp, timestampSource, eventDataTs, telemetryDataTs, request.getDataTimestamp(), request.getTimestamp());
    }

    // ==================== 状态转换处理 ====================

    /**
     * 使用分布式锁处理状态转换
     */
    private void processStateTransitionWithLock(EventData eventData, DeviceIdentity identity, WebhookRequest request) {
        Long deviceInfoId = identity.deviceInfoId();
        
        // 获取分布式锁
        if (!deviceLockService.tryLockState(deviceInfoId, DeviceStateEventFields.LOCK_TIMEOUT_SECONDS)) {
            log.warn("[Webhook-Handler-DeviceState] 获取设备状态锁失败: deviceInfoId={}, messageId={}",
                    deviceInfoId, request.getMessageId());
            throw new IotPortalException(IotPortalErrorCode.EVENT_DEVICE_STATE_PROCESSING);
        }

        boolean needUpdateCache = false;
        boolean dbOperationSuccess = false;

        try {
            // 查询数据库最新状态
            Optional<DeviceStateRecordDO> latestStateOpt = stateTimelineRepository.findLatestState(deviceInfoId);
            
            // 处理状态转换
            StateTransitionResult result = processStateTransition(
                    latestStateOpt, eventData, identity, request);
            
            needUpdateCache = result.needUpdateCache();
            dbOperationSuccess = result.dbOperationSuccess();
        } finally {
            deviceLockService.unlockState(deviceInfoId);
        }

        // 更新缓存（事务外执行）
        if (dbOperationSuccess) {
            updateCacheAfterStateTransition(identity, eventData, needUpdateCache, request);
        }
    }

    /**
     * 处理状态转换
     */
    private StateTransitionResult processStateTransition(Optional<DeviceStateRecordDO> latestStateOpt,
                                                          EventData eventData,
                                                          DeviceIdentity identity,
                                                          WebhookRequest request) {
        Long deviceInfoId = identity.deviceInfoId();
        Long orgFactoryId = identity.orgFactoryId();
        
        // 判断处理场景
        TransitionType transitionType = determineTransitionType(latestStateOpt, eventData);
        
        log.debug("[DeviceStateEventHandler] 处理状态转换: 类型={}, deviceInfoId={}, currentState={}, previousState={}",
                transitionType, deviceInfoId, eventData.currentState(), eventData.previousState());
        
        switch (transitionType) {
            case FIRST_RECORD:
                // 情况D：数据库无记录（首次记录）
                handleFirstRecord(deviceInfoId, orgFactoryId, eventData);
                return new StateTransitionResult(true, true);
                
            case FIRST_CONNECTION:
                // 情况A：首次连接（previousState = NULL）
                // 注意：如果数据库中有未结束的状态记录，需要先结束它
                if (latestStateOpt.isPresent() && latestStateOpt.get().getEndTs() == null) {
                    DeviceStateRecordDO latestState = latestStateOpt.get();
                    DeviceStateEnum dbStateEnum = DeviceStateEnum.fromCode(latestState.getStateCode());
                    log.warn("[DeviceStateEventHandler] 首次连接但数据库中有未结束的状态: deviceInfoId={}, DB状态={}({}), 将先结束该状态",
                            deviceInfoId, dbStateEnum.name(), latestState.getStateCode());
                    // 先结束未完成的状态
                    latestState.setEndTs(eventData.eventTimestamp());
                    if (latestState.getStartTs() != null) {
                        latestState.setDurationS(eventData.eventTimestamp() - latestState.getStartTs());
                    }
                    latestState.setIsComplete(false); // 标记为不完整，因为previousState不匹配
                    fillShiftInfoIfMissing(latestState, orgFactoryId);
                    stateTimelineRepository.update(latestState);
                }
                handleFirstConnection(deviceInfoId, orgFactoryId, eventData);
                return new StateTransitionResult(true, true);
                
            case STATE_UNCHANGED:
                // 状态未变化（重复的相同状态事件）
                return new StateTransitionResult(false, true);
                
            case NORMAL_TRANSITION:
                // 情况B：正常匹配（previousState == DB最新状态）
                DeviceStateRecordDO latestState = latestStateOpt.get();
                handleNormalTransition(latestState, orgFactoryId, eventData);
                return new StateTransitionResult(true, true);
                
            case STATE_MISMATCH:
                // 情况C：状态不匹配（异常情况）
                latestState = latestStateOpt.get();
                DeviceStateEnum dbStateEnum = DeviceStateEnum.fromCode(latestState.getStateCode());
                log.warn("[Webhook-Handler-DeviceState] 状态不匹配: DB状态={}({}), 事件previousState={}, 事件currentState={}, deviceInfoId={}",
                        dbStateEnum.name(), latestState.getStateCode(), eventData.previousState(), eventData.currentState(), deviceInfoId);
                handleStateMismatch(latestState, eventData, identity, request);
                return new StateTransitionResult(true, true);
                
            default:
                throw new IllegalStateException("未知的状态转换类型: " + transitionType);
        }
    }

    /**
     * 判断状态转换类型
     */
    private TransitionType determineTransitionType(Optional<DeviceStateRecordDO> latestStateOpt,
                                                     EventData eventData) {
        if (latestStateOpt.isEmpty()) {
            log.debug("[DeviceStateEventHandler] 判断转换类型: FIRST_RECORD (数据库无记录)");
            return TransitionType.FIRST_RECORD;
        }
        
        if (eventData.previousStateCode() == null) {
            log.debug("[DeviceStateEventHandler] 判断转换类型: FIRST_CONNECTION (previousState为空)");
            return TransitionType.FIRST_CONNECTION;
        }
        
        DeviceStateRecordDO latestState = latestStateOpt.get();
        Integer currentStateCode = eventData.currentStateCode();
        Integer previousStateCode = eventData.previousStateCode();
        
        // 直接使用数字编码进行比较（避免字符串转换）
        DeviceStateEnum latestStateEnum = DeviceStateEnum.fromCode(latestState.getStateCode());
        
        log.debug("[DeviceStateEventHandler] 判断转换类型: DB最新状态={}({}), 事件previousState={}({}), 事件currentState={}({}), DB状态endTs={}",
                latestStateEnum.name(), latestState.getStateCode(), 
                eventData.previousState(), previousStateCode,
                eventData.currentState(), currentStateCode,
                latestState.getEndTs());
        
        // 检查状态是否真的未变化（直接比较数字编码）
        boolean isStateUnchanged = currentStateCode != null 
                && currentStateCode.equals(latestState.getStateCode())
                && previousStateCode != null
                && previousStateCode.equals(latestState.getStateCode());
        
        if (isStateUnchanged) {
            log.debug("[DeviceStateEventHandler] 判断转换类型: STATE_UNCHANGED (状态未变化)");
            return TransitionType.STATE_UNCHANGED;
        }
        
        // 检查是否正常匹配（直接比较数字编码）
        if (previousStateCode != null && previousStateCode.equals(latestState.getStateCode())) {
            log.debug("[DeviceStateEventHandler] 判断转换类型: NORMAL_TRANSITION (正常匹配)");
            return TransitionType.NORMAL_TRANSITION;
        }
        
        log.debug("[DeviceStateEventHandler] 判断转换类型: STATE_MISMATCH (状态不匹配)");
        return TransitionType.STATE_MISMATCH;
    }

    // ==================== 状态处理场景 ====================

    /**
     * 情况A：首次连接（previousState = NULL）
     */
    private void handleFirstConnection(Long deviceInfoId, Long orgFactoryId, EventData eventData) {
        Map<String, Object> properties = DeviceStateUtils.createPropertiesWithOriginalState(
                eventData.currentStateResult(), null, eventData.eventTimestamp());
        DeviceStateRecordDO newRecord = createStateRecord(deviceInfoId, orgFactoryId,
                eventData.currentStateCode(), eventData.eventTimestamp(), null, true, properties);
        stateTimelineRepository.insert(newRecord);
    }

    /**
     * 情况B：正常匹配（DB最新状态 = previousState）
     */
    private void handleNormalTransition(DeviceStateRecordDO latestState, Long orgFactoryId, EventData eventData) {
        DeviceStateEnum latestStateEnum = DeviceStateEnum.fromCode(latestState.getStateCode());
        log.debug("[DeviceStateEventHandler] 处理正常状态转换: 结束状态={}({}), startTs={}, endTs={}, 新状态={}, 新startTs={}",
                latestStateEnum.name(), latestState.getStateCode(), latestState.getStartTs(), eventData.eventTimestamp(),
                eventData.currentState(), eventData.eventTimestamp());
        
        // 更新旧状态记录
        latestState.setEndTs(eventData.eventTimestamp());
        if (latestState.getStartTs() != null) {
            latestState.setDurationS(eventData.eventTimestamp() - latestState.getStartTs());
        }
        latestState.setIsComplete(true);
        fillShiftInfoIfMissing(latestState, orgFactoryId);
        
        stateTimelineRepository.update(latestState);
        log.debug("[DeviceStateEventHandler] 更新旧状态记录: 状态={}({}), endTs={}, durationS={}",
                latestStateEnum.name(), latestState.getStateCode(), latestState.getEndTs(), latestState.getDurationS());

        // 插入新状态记录
        Map<String, Object> properties = DeviceStateUtils.createPropertiesWithOriginalState(
                eventData.currentStateResult(), null, eventData.eventTimestamp());
        DeviceStateRecordDO newRecord = createStateRecord(
                latestState.getDeviceInfoId(), orgFactoryId, eventData.currentStateCode(),
                eventData.eventTimestamp(), null, true, properties);
        stateTimelineRepository.insert(newRecord);
        
        DeviceStateEnum newStateEnum = DeviceStateEnum.fromCode(newRecord.getStateCode());
        log.debug("[DeviceStateEventHandler] 插入新状态记录: 状态={}({}), startTs={}, endTs={}",
                newStateEnum.name(), newRecord.getStateCode(), newRecord.getStartTs(), newRecord.getEndTs());
    }

    /**
     * 情况C：状态不匹配（异常情况）
     */
    private void handleStateMismatch(DeviceStateRecordDO latestState, EventData eventData,
                                     DeviceIdentity identity, WebhookRequest request) {
        log.warn("状态不匹配异常: deviceInfoId={}, DB状态={}, 事件previousState={}, 事件currentState={}, timestamp={}",
                identity.deviceInfoId(), latestState.getStateCode(),
                eventData.previousState(), eventData.currentState(), eventData.eventTimestamp());

        boolean isOngoing = latestState.getEndTs() == null;

        if (isOngoing) {
            // 子情况C2：数据库状态进行中（end_ts IS NULL）
            handleOngoingStateMismatch(latestState, eventData, identity, request);
        } else {
            // 子情况C1：数据库状态已结束（end_ts IS NOT NULL）
            handleEndedStateMismatch(latestState, eventData, identity, request);
        }
    }

    /**
     * 子情况C1：数据库状态已结束（end_ts IS NOT NULL）
     */
    private void handleEndedStateMismatch(DeviceStateRecordDO latestState, EventData eventData,
                                          DeviceIdentity identity, WebhookRequest request) {
        Long latestEndTs = latestState.getEndTs();
        Long deviceInfoId = identity.deviceInfoId();
        Long orgFactoryId = identity.orgFactoryId();

        // 检查是否存在状态间隙
        if (latestEndTs < eventData.eventTimestamp()) {
            // 存在间隙，插入 UNKNOWN 状态记录填充间隙
            log.warn("检测到状态间隙，插入UNKNOWN状态: deviceInfoId={}, gap=[{} -> {}]",
                    deviceInfoId, latestEndTs, eventData.eventTimestamp());

            Map<String, Object> gapProperties = new HashMap<>();
            gapProperties.put(DeviceStateEventFields.GAP_REASON, "状态不匹配导致间隙");
            gapProperties.put(DeviceStateEventFields.EXPECTED_PREVIOUS, eventData.previousState());
            DeviceStateEnum dbStateEnum = DeviceStateEnum.fromCode(latestState.getStateCode());
            gapProperties.put(DeviceStateEventFields.ACTUAL_DB_STATE, dbStateEnum.name());

            DeviceStateRecordDO unknownRecord = createStateRecord(deviceInfoId, orgFactoryId,
                    DeviceStateEnum.UNKNOWN.getCode(), latestEndTs, eventData.eventTimestamp(), false, gapProperties);
            stateTimelineRepository.insert(unknownRecord);
        }

        // 插入新状态记录
        Map<String, Object> properties = DeviceStateUtils.createPropertiesWithOriginalState(
                eventData.currentStateResult(), null, eventData.eventTimestamp());
        DeviceStateRecordDO newRecord = createStateRecord(deviceInfoId, orgFactoryId, eventData.currentStateCode(),
                eventData.eventTimestamp(), null, true, properties);
        stateTimelineRepository.insert(newRecord);

        // 记录异常日志（可以自动修复，不需要人工处理）
        String errorMessage = String.format("状态不匹配（已结束）: DB状态=%s, 事件previousState=%s, 间隙=%d毫秒",
                latestState.getStateCode(), eventData.previousState(),
                eventData.eventTimestamp() - latestEndTs);
        webhookFailLogService.saveFailLog(request, DeviceStateEventFields.ERROR_TYPE_STATE_MISMATCH, errorMessage, false);
    }

    /**
     * 子情况C2：数据库状态进行中（end_ts IS NULL）
     */
    private void handleOngoingStateMismatch(DeviceStateRecordDO latestState, EventData eventData,
                                            DeviceIdentity identity, WebhookRequest request) {
        // 检查时间戳异常
        if (latestState.getStartTs() != null && eventData.eventTimestamp() < latestState.getStartTs()) {
            // 子情况C3：时间戳异常
            handleTimestampAnomaly(latestState, eventData, identity, request);
            return;
        }

        Long deviceInfoId = identity.deviceInfoId();
        Long orgFactoryId = identity.orgFactoryId();

        // 将数据库中的进行中状态标记为 UNKNOWN
        Map<String, Object> mismatchProperties = new HashMap<>();
        mismatchProperties.put(DeviceStateEventFields.MISMATCH_REASON, "previousState不匹配");
        DeviceStateEnum dbStateEnum = DeviceStateEnum.fromCode(latestState.getStateCode());
        mismatchProperties.put(DeviceStateEventFields.EXPECTED, dbStateEnum.name());
        mismatchProperties.put(DeviceStateEventFields.ACTUAL_DB, dbStateEnum.name());
        mismatchProperties.put(DeviceStateEventFields.EVENT_PREVIOUS, eventData.previousState());

        latestState.setStateCode(DeviceStateEnum.UNKNOWN.getCode());
        latestState.setEndTs(eventData.eventTimestamp());
        if (latestState.getStartTs() != null) {
            latestState.setDurationS(eventData.eventTimestamp() - latestState.getStartTs());
        }
        latestState.setIsComplete(false);
        latestState.setProperties(mismatchProperties);
        fillShiftInfoIfMissing(latestState, orgFactoryId);
        stateTimelineRepository.update(latestState);

        // 如果 previousState 不为 NULL，插入 previousState 状态记录（用于修复时间线）
        if (eventData.previousStateCode() != null && eventData.previousStateResult() != null) {
            Map<String, Object> recoveryProperties = new HashMap<>();
            recoveryProperties.put(DeviceStateEventFields.RECOVERY, true);
            recoveryProperties.put(DeviceStateEventFields.RECOVERED_FROM, DeviceStateUtils.UNKNOWN_STATE);
            recoveryProperties = DeviceStateUtils.createPropertiesWithOriginalState(
                    eventData.previousStateResult(), recoveryProperties, eventData.eventTimestamp());

            DeviceStateRecordDO previousRecord = createStateRecord(deviceInfoId, orgFactoryId, eventData.previousStateCode(),
                    eventData.eventTimestamp(), eventData.eventTimestamp(), false, recoveryProperties);
            stateTimelineRepository.insert(previousRecord);
        }

        // 插入新状态记录
        Map<String, Object> properties = DeviceStateUtils.createPropertiesWithOriginalState(
                eventData.currentStateResult(), null, eventData.eventTimestamp());
        DeviceStateRecordDO newRecord = createStateRecord(deviceInfoId, orgFactoryId, eventData.currentStateCode(),
                eventData.eventTimestamp(), null, true, properties);
        stateTimelineRepository.insert(newRecord);

        // 记录异常日志（需要人工审核）
        DeviceStateEnum dbStateEnumForLog = DeviceStateEnum.fromCode(latestState.getStateCode());
        String errorMessage = String.format("状态不匹配（进行中）: DB状态=%s(%d), 事件previousState=%s, 已标记为UNKNOWN",
                dbStateEnumForLog.name(), latestState.getStateCode(), eventData.previousState());
        webhookFailLogService.saveFailLog(request, DeviceStateEventFields.ERROR_TYPE_STATE_MISMATCH, errorMessage, true);
    }

    /**
     * 子情况C3：时间戳异常（事件时间 < 数据库状态开始时间）
     */
    private void handleTimestampAnomaly(DeviceStateRecordDO latestState, EventData eventData,
                                         DeviceIdentity identity, WebhookRequest request) {
        long gapMs = latestState.getStartTs() - eventData.eventTimestamp();
        long gapSeconds = gapMs / 1000;
        log.warn("时间戳异常: deviceInfoId={}, 事件时间={}, DB状态开始时间={}, 差距={}毫秒 ({}秒)",
                identity.deviceInfoId(), eventData.eventTimestamp(), latestState.getStartTs(), gapMs, gapSeconds);

        Long deviceInfoId = identity.deviceInfoId();
        Long orgFactoryId = identity.orgFactoryId();

        // 先结束数据库中的异常记录
        if (latestState.getEndTs() == null) {
            latestState.setEndTs(eventData.eventTimestamp());
            if (latestState.getStartTs() != null) {
                long duration = eventData.eventTimestamp() - latestState.getStartTs();
                latestState.setDurationS(duration < 0 ? 0 : duration);
            }
            latestState.setIsComplete(false);

            // 添加异常标记到properties
            Map<String, Object> existingProperties = latestState.getProperties();
            if (existingProperties == null) {
                existingProperties = new HashMap<>();
            }
            existingProperties.put(DeviceStateEventFields.TIMESTAMP_ANOMALY, true);
            existingProperties.put(DeviceStateEventFields.EVENT_TIMESTAMP, eventData.eventTimestamp());
            existingProperties.put(DeviceStateEventFields.DB_START_TS, latestState.getStartTs());
            existingProperties.put(DeviceStateEventFields.ANOMALY_REASON,
                    String.format("时间戳异常: 事件时间(%d) < DB开始时间(%d), 差距=%d秒",
                            eventData.eventTimestamp(), latestState.getStartTs(), gapSeconds));
            latestState.setProperties(existingProperties);
            fillShiftInfoIfMissing(latestState, orgFactoryId);
            stateTimelineRepository.update(latestState);
        }

        // 插入新状态记录，使用设备时间戳
        Map<String, Object> anomalyProperties = new HashMap<>();
        anomalyProperties.put(DeviceStateEventFields.TIMESTAMP_ANOMALY, true);
        anomalyProperties.put(DeviceStateEventFields.EVENT_TIMESTAMP, eventData.eventTimestamp());
        anomalyProperties.put(DeviceStateEventFields.DB_START_TS, latestState.getStartTs());
        anomalyProperties.put(DeviceStateEventFields.ANOMALY_REASON,
                String.format("时间戳异常: 事件时间(%d) < DB开始时间(%d), 差距=%d秒",
                        eventData.eventTimestamp(), latestState.getStartTs(), gapSeconds));
        anomalyProperties = DeviceStateUtils.createPropertiesWithOriginalState(
                eventData.currentStateResult(), anomalyProperties, eventData.eventTimestamp());

        DeviceStateRecordDO newRecord = createStateRecord(deviceInfoId, orgFactoryId, eventData.currentStateCode(),
                eventData.eventTimestamp(), null, false, anomalyProperties);
        stateTimelineRepository.insert(newRecord);

        // 记录异常日志（需要人工审核）
        String errorMessage = String.format("时间戳异常: 事件时间=%d, DB状态开始时间=%d, 差距=%d秒 (已使用设备时间戳插入新记录)",
                eventData.eventTimestamp(), latestState.getStartTs(), gapSeconds);
        webhookFailLogService.saveFailLog(request, DeviceStateEventFields.ERROR_TYPE_TIMESTAMP_ANOMALY, errorMessage, true);
    }

    /**
     * 情况D：数据库无记录（首次记录）
     */
    private void handleFirstRecord(Long deviceInfoId, Long orgFactoryId, EventData eventData) {
        if (eventData.previousStateCode() != null) {
            log.warn("[Webhook-Handler-DeviceState] 数据库无记录但previousState不为NULL: deviceInfoId={}, previousState={}",
                    deviceInfoId, eventData.previousState());
        }

        Map<String, Object> properties = DeviceStateUtils.createPropertiesWithOriginalState(
                eventData.currentStateResult(), null, eventData.eventTimestamp());
        DeviceStateRecordDO newRecord = createStateRecord(deviceInfoId, orgFactoryId, eventData.currentStateCode(),
                eventData.eventTimestamp(), null, true, properties);
        stateTimelineRepository.insert(newRecord);
    }

    // ==================== 记录创建方法 ====================

    /**
     * 创建状态记录
     */
    private DeviceStateRecordDO createStateRecord(Long deviceInfoId, Long orgFactoryId,
                                                   Integer stateCode, Long startTs, Long endTs, boolean isComplete,
                                                   Map<String, Object> properties) {
        // 直接使用数字编码（无需转换）
        Integer stateCodeInt = stateCode;
        
        DeviceStateRecordDO record = new DeviceStateRecordDO();
        record.setDeviceInfoId(deviceInfoId);
        record.setOrgFactoryId(orgFactoryId);
        record.setStateCode(stateCodeInt);
        record.setStartTs(startTs);
        record.setEndTs(endTs);
        if (endTs != null && startTs != null) {
            record.setDurationS(endTs - startTs);
        }
        
        // 计算 is_complete 字段
        if (endTs == null) {
            record.setIsComplete(false);
        } else {
            boolean crossesShift = shiftCalculationService.checkIfCrossesShift(orgFactoryId, deviceInfoId, startTs, endTs);
            record.setIsComplete(!crossesShift && isComplete);
        }
        
        record.setProperties(properties);
        
        // 设置班次信息
        if (startTs != null) {
            try {
                ShiftDateAndCode shiftInfo = shiftCalculationService.getShiftDateAndCode(orgFactoryId, deviceInfoId, startTs);
                record.setShiftDate(shiftInfo.shiftDate());
                record.setShiftCode(shiftInfo.shiftCode());
            } catch (Exception e) {
                log.warn("[DeviceStateEventHandler] 计算班次信息失败: deviceInfoId={}, startTs={}, error={}",
                        deviceInfoId, startTs, e.getMessage());
            }
        }
        
        return record;
    }

    /**
     * 如果班次信息缺失，根据开始时间补充
     */
    private void fillShiftInfoIfMissing(DeviceStateRecordDO record, Long factoryId) {
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
                log.warn("[DeviceStateEventHandler] 补充班次信息失败: deviceInfoId={}, startTs={}, error={}",
                        record.getDeviceInfoId(), record.getStartTs(), e.getMessage());
            }
        }
    }

    // ==================== 缓存更新 ====================

    /**
     * 状态转换后更新缓存
     */
    private void updateCacheAfterStateTransition(DeviceIdentity identity, EventData eventData,
                                                 boolean needUpdateCache, WebhookRequest request) {
        Long orgFactoryId = identity.orgFactoryId();
        Long deviceInfoId = identity.deviceInfoId();
        
        if (needUpdateCache) {
            // 更新状态缓存（使用数字编码）
            updateStateCache(orgFactoryId, deviceInfoId, eventData.currentStateCode(),
                    eventData.eventTimestamp(), request.getMessageId());
        } else {
            // 状态未变化，只刷新缓存 TTL 和心跳
            refreshStateCacheAndHeartbeat(orgFactoryId, deviceInfoId, eventData.eventTimestamp(), request.getMessageId());
        }
    }

    /**
     * 更新实时状态缓存（事务外执行）
     */
    private void updateStateCache(Long factoryId, Long deviceId, Integer currentStateCode,
                                  Long eventTimestamp, String traceId) {
        // 将数字编码转换为字符串存储到缓存
        String stateStr = String.valueOf(currentStateCode);
        deviceStateCacheService.saveState(factoryId, deviceId, stateStr,
                eventTimestamp, DeviceStateEventFields.SOURCE_TB, traceId);
        deviceStateCacheService.saveHeartbeat(factoryId, deviceId, traceId);
    }

    /**
     * 状态未变化时，刷新状态缓存 TTL（不改值）并刷新心跳（事务外执行）
     */
    private void refreshStateCacheAndHeartbeat(Long factoryId, Long deviceId,
                                               Long eventTimestamp, String traceId) {
        deviceStateCacheService.refreshStateTtl(factoryId, deviceId);
        deviceStateCacheService.saveHeartbeat(factoryId, deviceId, traceId);
    }

    // ==================== 心跳处理 ====================

    /**
     * 设备状态心跳事件：不写时间线，仅续租实时缓存 TTL 并更新心跳
     * <p>
     * 支持接收数字编码（0-3）或字符串状态名称
     * </p>
     */
    private void handleHeartbeat(WebhookRequest request) {
        Map<String, Object> eventData = request.getEventData();
        if (eventData == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_DATA_EMPTY);
        }
        
        // 提取状态值（TB 发送的是数字编码 0-3）
        Object currentStateObj = eventData.get(DeviceStateEventFields.CURRENT_STATE);
        if (currentStateObj == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_CURRENT_STATE_EMPTY);
        }
        
        Integer currentStateCode = DeviceStateUtils.extractAndValidateStateCode(currentStateObj);
        if (currentStateCode == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_CURRENT_STATE_EMPTY);
        }
        
        DeviceIdentity identity = 
                webhookHandlerUtils.resolveDeviceIdentity(request);
        Long deviceInfoId = identity.deviceInfoId();
        Long orgFactoryId = identity.orgFactoryId();

        long ts = request.getDataTimestamp() != null
                ? request.getDataTimestamp()
                : (request.getTimestamp() != null ? request.getTimestamp()
                : System.currentTimeMillis());

        // 将数字编码转换为字符串存储到缓存
        String stateStr = String.valueOf(currentStateCode);
        deviceStateCacheService.saveState(orgFactoryId, deviceInfoId, stateStr,
                ts, DeviceStateEventFields.SOURCE_TB, request.getMessageId());
        deviceStateCacheService.saveHeartbeat(orgFactoryId, deviceInfoId, request.getMessageId());
    }


    // ==================== 内部数据类 ====================

    /**
     * 事件数据
     * <p>
     * 同时存储数字编码和字符串状态名称：
     * - 数字编码：用于状态比较、数据库存储、缓存（避免重复转换）
     * - 字符串状态名称：用于日志输出、属性设置（可读性）
     * </p>
     */
    private record EventData(
            String previousState,           // 字符串状态名称（用于日志输出、属性设置）
            String currentState,            // 字符串状态名称（用于日志输出、属性设置）
            Integer previousStateCode,      // 数字编码（用于状态比较、数据库存储、缓存）
            Integer currentStateCode,       // 数字编码（用于状态比较、数据库存储、缓存）
            Long eventTimestamp,
            DeviceStateUtils.StateValidationResult currentStateResult,
            DeviceStateUtils.StateValidationResult previousStateResult
    ) {}


    /**
     * 状态转换结果
     */
    private record StateTransitionResult(boolean needUpdateCache, boolean dbOperationSuccess) {}

    /**
     * 状态转换类型
     */
    private enum TransitionType {
        FIRST_RECORD,        // 情况D：数据库无记录
        FIRST_CONNECTION,     // 情况A：首次连接
        STATE_UNCHANGED,     // 状态未变化
        NORMAL_TRANSITION,   // 情况B：正常匹配
        STATE_MISMATCH      // 情况C：状态不匹配
    }
}
