package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.common.enums.DeviceStateEnum;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.common.utils.DeviceStateUtils;
import com.weili.iot_portal.common.utils.StateValidationResult;
import com.weili.iot_portal.common.utils.WebhookTimestampUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.domain.ingestion.DeviceIdentity;
import com.weili.iot_portal.domain.ingestion.ShiftDateAndCode;
import com.weili.iot_portal.domain.ingestion.ShiftTimeRange;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceLockService;
import com.weili.iot_portal.service.cache.DeviceStateCacheService;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.WebhookFailLogService;
import com.weili.iot_portal.service.ingestion.WebhookProcessingStrategy;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceStateEventFields;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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

    // ==================== 常量定义 ====================
    /**
     * 过期数据的结束时间标记
     * 使用特殊值-1标记过期数据，避免被正常查询扫描到
     * 注意：此常量仅用于日志输出，实际过期检查使用 TimeRangeRecordHandler.isExpired()
     */
    private static final long EXPIRED_END_TIMESTAMP = -1L;

    // ==================== 依赖注入 ====================
    private final DeviceStateRecordRepository stateTimelineRepository;
    private final WebhookFailLogService webhookFailLogService;
    private final DeviceLockService deviceLockService;
    private final DeviceStateCacheService deviceStateCacheService;
    private final IShiftCalculationService shiftCalculationService;
    private final WebhookHandlerUtils webhookHandlerUtils;
    private final com.weili.iot_portal.service.record.TimeRangeRecordHandler timeRangeRecordHandler;
    private final RecordHandlerUtils recordHandlerUtils;

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
        log.debug("[Webhook-Handler-DeviceState] 处理设备状态事件: messageId={}, eventType={}, deviceCode={}",
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
        StateValidationResult currentStateResult = new StateValidationResult(
                currentState, false, null);
        StateValidationResult previousStateResult = previousState != null
                ? new StateValidationResult(previousState, false, null)
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
        String timestampSource = (eventTimestamp.equals(eventDataTs)) ? "eventData" :
                ((eventTimestamp.equals(telemetryDataTs)) ? "telemetryData" :
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
        
        if (deviceLockService.tryLockState(deviceInfoId, DeviceStateEventFields.LOCK_TIMEOUT_SECONDS)) {
            log.debug("[Webhook-Handler-DeviceState] 获取设备状态锁失败: deviceInfoId={}, messageId={}",
                    deviceInfoId, request.getMessageId());
            throw new IotPortalException(IotPortalErrorCode.EVENT_DEVICE_STATE_PROCESSING);
        }

        boolean needUpdateCache;
        boolean dbOperationSuccess;

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
                    // 如果开始时间存在且超过过期阈值，标记为过期并创建新状态（避免计算超长持续时间）
                    if (timeRangeRecordHandler.isExpired(latestState, eventData.eventTimestamp())) {
                        handleExpiredState(latestState, eventData, orgFactoryId);
                        // 已在 handleExpiredState 中创建新状态，直接返回
                        return new StateTransitionResult(true, true);
                    }

                    // 先结束未完成的状态（正常短期场景）
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
        List<DeviceStateRecordDO> records = createStateRecords(deviceInfoId, orgFactoryId,
                eventData.currentStateCode(), eventData.eventTimestamp(), null, true, properties);
        for (DeviceStateRecordDO record : records) {
            stateTimelineRepository.insert(record);
        }
    }

    /**
     * 情况B：正常匹配（DB最新状态 = previousState）
     */
    private void handleNormalTransition(DeviceStateRecordDO latestState, Long orgFactoryId, EventData eventData) {
        DeviceStateEnum latestStateEnum = DeviceStateEnum.fromCode(latestState.getStateCode());
        log.debug("[DeviceStateEventHandler] 处理正常状态转换: 结束状态={}({}), startTs={}, endTs={}, 新状态={}, 新startTs={}",
                latestStateEnum.name(), latestState.getStateCode(), latestState.getStartTs(), eventData.eventTimestamp(),
                eventData.currentState(), eventData.currentState());

        // 过期检查：如果状态记录持续时间超过阈值，直接标记为过期
        if (timeRangeRecordHandler.isExpired(latestState, eventData.eventTimestamp())) {
            log.warn("[DeviceStateEventHandler] 检测到过期状态记录，直接标记为过期: deviceInfoId={}, stateCode={}, " +
                    "startTs={}, currentTs={}",
                    latestState.getDeviceInfoId(), latestState.getStateCode(),
                    latestState.getStartTs(), eventData.eventTimestamp());

            handleExpiredState(latestState, eventData, orgFactoryId);
            return;
        }

        Long oldStartTs = latestState.getStartTs();
        Long newEndTs = eventData.eventTimestamp();
        
        // 使用通用服务更新记录（自动处理跨班次）
        Map<String, Object> oldProperties = latestState.getProperties();
        if (oldProperties == null) {
            oldProperties = new HashMap<>();
        }
        final Map<String, Object> finalProperties = oldProperties;
        final Integer stateCode = latestState.getStateCode();
        
        boolean createdNewRecord = timeRangeRecordHandler.updateOngoingRecord(
                latestState,
                newEndTs,
                orgFactoryId,
                // RecordFactory: 创建拆分后的记录
                (deviceId, factoryId, startTs, endTs) -> {
                    DeviceStateRecordDO record = new DeviceStateRecordDO();
                    record.setDeviceInfoId(deviceId);
                    record.setOrgFactoryId(factoryId);
                    record.setStateCode(stateCode);
                    record.setStartTs(startTs);
                    record.setEndTs(endTs);
                    record.setDurationS(endTs - startTs);
                    record.setProperties(finalProperties);
                    record.setIsComplete(true);  // 拆分后的记录标记为完整
                    return record;
                },
                // RecordUpdater: 更新/删除/插入数据库
                new com.weili.iot_portal.service.record.TimeRangeRecordHandler.RecordUpdater<DeviceStateRecordDO>() {
                    @Override
                    public void update(DeviceStateRecordDO record) {
                        // 更新时设置 isComplete
                        record.setIsComplete(true);
                        stateTimelineRepository.update(record);
                    }
                    
                    @Override
                    public void delete(DeviceStateRecordDO record) {
                        stateTimelineRepository.deleteById(record.getId());
                    }
                    
                    @Override
                    public void insert(DeviceStateRecordDO record) {
                        stateTimelineRepository.insert(record);
                        log.debug("[DeviceStateEventHandler] 插入截断后的旧状态记录: 状态={}({}), shiftDate={}, shiftCode={}, startTs={}, endTs={}",
                                latestStateEnum.name(), record.getStateCode(), record.getShiftDate(), record.getShiftCode(),
                                record.getStartTs(), record.getEndTs());
                    }
                }
        );
        
        if (!createdNewRecord) {
            // 如果未创建新记录（未过期），记录已更新
            log.debug("[DeviceStateEventHandler] 更新旧状态记录: 状态={}({}), endTs={}, durationS={}",
                    latestStateEnum.name(), latestState.getStateCode(), latestState.getEndTs(), latestState.getDurationS());
        }

        // 插入新状态记录
        Map<String, Object> properties = DeviceStateUtils.createPropertiesWithOriginalState(
                eventData.currentStateResult(), null, eventData.eventTimestamp());
        List<DeviceStateRecordDO> newRecords = createStateRecords(
                latestState.getDeviceInfoId(), orgFactoryId, eventData.currentStateCode(),
                eventData.eventTimestamp(), null, true, properties);
        for (DeviceStateRecordDO record : newRecords) {
            stateTimelineRepository.insert(record);
            DeviceStateEnum newStateEnum = DeviceStateEnum.fromCode(record.getStateCode());
            log.debug("[DeviceStateEventHandler] 插入新状态记录: 状态={}({}), shiftDate={}, shiftCode={}, startTs={}, endTs={}",
                    newStateEnum.name(), record.getStateCode(), record.getShiftDate(), record.getShiftCode(),
                    record.getStartTs(), record.getEndTs());
        }
    }

    /**
     * 情况C：状态不匹配（异常情况）
     */
    private void handleStateMismatch(DeviceStateRecordDO latestState, EventData eventData,
                                     DeviceIdentity identity, WebhookRequest request) {
        log.debug("状态不匹配异常: deviceInfoId={}, DB状态={}, 事件previousState={}, 事件currentState={}, timestamp={}",
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
            log.debug("检测到状态间隙，插入UNKNOWN状态: deviceInfoId={}, gap=[{} -> {}]",
                    deviceInfoId, latestEndTs, eventData.eventTimestamp());

            Map<String, Object> gapProperties = new HashMap<>();
            gapProperties.put(DeviceStateEventFields.GAP_REASON, "状态不匹配导致间隙");
            gapProperties.put(DeviceStateEventFields.EXPECTED_PREVIOUS, eventData.previousState());
            DeviceStateEnum dbStateEnum = DeviceStateEnum.fromCode(latestState.getStateCode());
            gapProperties.put(DeviceStateEventFields.ACTUAL_DB_STATE, dbStateEnum.name());

            List<DeviceStateRecordDO> unknownRecords = createStateRecords(deviceInfoId, orgFactoryId,
                    DeviceStateEnum.UNKNOWN.getCode(), latestEndTs, eventData.eventTimestamp(), false, gapProperties);
            for (DeviceStateRecordDO record : unknownRecords) {
                stateTimelineRepository.insert(record);
            }
        }

        // 插入新状态记录
        Map<String, Object> properties = DeviceStateUtils.createPropertiesWithOriginalState(
                eventData.currentStateResult(), null, eventData.eventTimestamp());
        List<DeviceStateRecordDO> newRecords = createStateRecords(deviceInfoId, orgFactoryId, eventData.currentStateCode(),
                eventData.eventTimestamp(), null, true, properties);
        for (DeviceStateRecordDO record : newRecords) {
            stateTimelineRepository.insert(record);
        }

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

        // 如果数据库中的进行中状态已过期，直接标记为过期并创建新状态（避免计算超长持续时间）
        if (timeRangeRecordHandler.isExpired(latestState, eventData.eventTimestamp())) {
            handleExpiredState(latestState, eventData, orgFactoryId);
            // 已在 handleExpiredState 中创建新状态，直接返回
            return;
        }

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

            List<DeviceStateRecordDO> previousRecords = createStateRecords(deviceInfoId, orgFactoryId, eventData.previousStateCode(),
                    eventData.eventTimestamp(), eventData.eventTimestamp(), false, recoveryProperties);
            for (DeviceStateRecordDO record : previousRecords) {
                stateTimelineRepository.insert(record);
            }
        }

        // 插入新状态记录
        Map<String, Object> properties = DeviceStateUtils.createPropertiesWithOriginalState(
                eventData.currentStateResult(), null, eventData.eventTimestamp());
        List<DeviceStateRecordDO> newRecords = createStateRecords(deviceInfoId, orgFactoryId, eventData.currentStateCode(),
                eventData.eventTimestamp(), null, true, properties);
        for (DeviceStateRecordDO record : newRecords) {
            stateTimelineRepository.insert(record);
        }

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

        List<DeviceStateRecordDO> newRecords = createStateRecords(deviceInfoId, orgFactoryId, eventData.currentStateCode(),
                eventData.eventTimestamp(), null, false, anomalyProperties);
        for (DeviceStateRecordDO record : newRecords) {
            stateTimelineRepository.insert(record);
        }

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
        List<DeviceStateRecordDO> newRecords = createStateRecords(deviceInfoId, orgFactoryId, eventData.currentStateCode(),
                eventData.eventTimestamp(), null, true, properties);
        for (DeviceStateRecordDO record : newRecords) {
            stateTimelineRepository.insert(record);
        }
    }

    // ==================== 记录创建方法 ====================

    /**
     * 创建状态记录（支持跨班次截断）
     * 如果状态跨班次，会自动按班次截断为多条记录
     * <p>
     * 改进点：
     * 1. 对进行中的状态（endTs = null）也进行跨班次检查，避免插入跨多个班次的记录
     * 2. 最大持续时间限制为一个班次时长，超过后自动截断到班次结束时间
     * 3. 增强数据验证，确保数据质量
     * </p>
     *
     * @param deviceInfoId 设备ID
     * @param orgFactoryId 工厂ID
     * @param stateCode 状态编码
     * @param startTs 开始时间（毫秒）
     * @param endTs 结束时间（毫秒，可为null表示进行中）
     * @param isComplete 是否完整（业务层面的完整性，跨班次截断后每条记录都是完整的）
     * @param properties 扩展属性
     * @return 记录列表（如果跨班次则多条，否则一条）
     */
    private List<DeviceStateRecordDO> createStateRecords(Long deviceInfoId, Long orgFactoryId,
                                                           Integer stateCode, Long startTs, Long endTs,
                                                           boolean isComplete, Map<String, Object> properties) {
        // 数据验证
        if (startTs == null) {
            log.error("[DeviceStateEventHandler] startTs cannot be null: deviceId={}, stateCode={}",
                    deviceInfoId, stateCode);
            throw new IllegalArgumentException("startTs cannot be null");
        }

        // 获取状态开始时间所在的班次，用于计算班次时长
        ShiftTimeRange startShift = shiftCalculationService.calculateShiftRange(orgFactoryId, deviceInfoId, startTs);
        if (startShift == null || startShift.getEndTs() == null) {
            log.warn("[DeviceStateEventHandler] 无法计算班次范围，使用默认处理: deviceId={}, stateCode={}, startTs={}",
                    deviceInfoId, stateCode, startTs);
            // 如果无法计算班次，回退到原有逻辑（不进行班次时长限制）
            return createStateRecordsWithoutShiftLimit(deviceInfoId, orgFactoryId, stateCode, startTs, endTs, isComplete, properties);
        }

        // 增强边界验证：确保班次时间范围有效
        if (!recordHandlerUtils.validateShiftBoundary(startShift)) {
            log.warn("[DeviceStateEventHandler] 班次边界验证失败，使用默认处理: deviceId={}, stateCode={}, shiftStart={}, shiftEnd={}",
                    deviceInfoId, stateCode, startShift.getStartTs(), startShift.getEndTs());
            return createStateRecordsWithoutShiftLimit(deviceInfoId, orgFactoryId, stateCode, startTs, endTs, isComplete, properties);
        }

        long shiftDurationMs = startShift.getEndTs() - startShift.getStartTs();
        long shiftEndTs = startShift.getEndTs();

        long currentTime = System.currentTimeMillis();
        long effectiveEndTs = endTs != null ? endTs : currentTime;
        long duration = effectiveEndTs - startTs;
        boolean isOngoing = (endTs == null);

        // 统一处理进行中和结束状态的班次截断逻辑
        return handleStateWithShiftLimit(deviceInfoId, orgFactoryId, stateCode, startTs, effectiveEndTs,
                shiftEndTs, shiftDurationMs, duration, isOngoing, isComplete, properties);
    }
    
    /**
     * 创建状态记录（不进行班次时长限制，用于无法计算班次时的回退处理）
     */
    private List<DeviceStateRecordDO> createStateRecordsWithoutShiftLimit(Long deviceInfoId, Long orgFactoryId,
                                                                           Integer stateCode, Long startTs, Long endTs,
                                                                           boolean isComplete, Map<String, Object> properties) {
        if (endTs == null) {
            DeviceStateRecordDO record = createSingleStateRecord(deviceInfoId, orgFactoryId, stateCode,
                    startTs, null, false, properties);
            return Collections.singletonList(record);
        }

        boolean crossesShift = timeRangeRecordHandler.checkIfCrossesShift(orgFactoryId, deviceInfoId, startTs, endTs);
        if (!crossesShift) {
            DeviceStateRecordDO record = createSingleStateRecord(deviceInfoId, orgFactoryId, stateCode,
                    startTs, endTs, isComplete, properties);
            return Collections.singletonList(record);
        }

        return splitByShift(deviceInfoId, orgFactoryId, stateCode, startTs, endTs, properties);
    }

    /**
     * 按班次截断状态记录
     * 如果记录跨班次，拆分为多条记录，每条记录属于一个班次
     * <p>
     * 使用通用服务 TimeRangeRecordHandler.splitByShift()
     * </p>
     *
     * @param deviceInfoId 设备ID
     * @param orgFactoryId 工厂ID
     * @param stateCode 状态编码
     * @param startTs 开始时间（毫秒）
     * @param endTs 结束时间（毫秒）
     * @param properties 扩展属性
     * @return 拆分后的记录列表
     */
    private List<DeviceStateRecordDO> splitByShift(Long deviceInfoId, Long orgFactoryId, Integer stateCode,
                                                    Long startTs, Long endTs, Map<String, Object> properties) {
        log.debug("[DeviceStateEventHandler] 开始按班次截断: deviceInfoId={}, stateCode={}, startTs={}, endTs={}",
                deviceInfoId, stateCode, startTs, endTs);

        // 使用通用服务拆分记录
        final Integer finalStateCode = stateCode;
        final Map<String, Object> finalProperties = properties != null ? properties : new HashMap<>();
        
        List<DeviceStateRecordDO> records = timeRangeRecordHandler.splitByShift(
                deviceInfoId,
                orgFactoryId,
                startTs,
                endTs,
                // RecordFactory: 创建带有 stateCode 和 properties 的记录
                (deviceId, factoryId, recordStartTs, recordEndTs) -> {
                    DeviceStateRecordDO record = new DeviceStateRecordDO();
                    record.setDeviceInfoId(deviceId);
                    record.setOrgFactoryId(factoryId);
                    record.setStateCode(finalStateCode);
                    record.setStartTs(recordStartTs);
                    record.setEndTs(recordEndTs);
                    record.setDurationS(recordEndTs - recordStartTs);
                    record.setProperties(finalProperties);
                    record.setIsComplete(true);  // 拆分后的记录标记为完整
                    return record;
                }
        );

        log.info("[DeviceStateEventHandler] 按班次截断完成: deviceInfoId={}, stateCode={}, 原始记录1条, 截断后{}条",
                deviceInfoId, stateCode, records.size());

        return records;
    }


    /**
     * 创建单条状态记录（不跨班次）
     */
    private DeviceStateRecordDO createSingleStateRecord(Long deviceInfoId, Long orgFactoryId,
                                                         Integer stateCode, Long startTs, Long endTs,
                                                         boolean isComplete, Map<String, Object> properties) {
        DeviceStateRecordDO record = new DeviceStateRecordDO();
        record.setDeviceInfoId(deviceInfoId);
        record.setOrgFactoryId(orgFactoryId);
        record.setStateCode(stateCode);
        record.setStartTs(startTs);
        record.setEndTs(endTs);
        if (endTs != null && startTs != null) {
            record.setDurationS(endTs - startTs);
        }
        
        // 计算 is_complete 字段
        if (endTs == null) {
            record.setIsComplete(false);
        } else {
            // 不跨班次的情况下，is_complete 由业务逻辑决定
            record.setIsComplete(isComplete);
        }
        
        record.setProperties(properties);
        
        // 设置班次信息（使用通用工具类）
        recordHandlerUtils.fillShiftInfoIfMissing(record, orgFactoryId);
        
        return record;
    }

    /**
     * 创建状态记录（兼容旧接口，返回单条记录）
     * @deprecated 请使用 createStateRecords 方法，支持跨班次截断
     */
    @Deprecated
    private DeviceStateRecordDO createStateRecord(Long deviceInfoId, Long orgFactoryId,
                                                   Integer stateCode, Long startTs, Long endTs, boolean isComplete,
                                                   Map<String, Object> properties) {
        List<DeviceStateRecordDO> records = createStateRecords(deviceInfoId, orgFactoryId, stateCode,
                startTs, endTs, isComplete, properties);
        // 如果跨班次截断后有多条记录，只返回第一条（兼容旧代码）
        if (records.size() > 1) {
            log.warn("[DeviceStateEventHandler] createStateRecord返回多条记录，只返回第一条: deviceInfoId={}, recordsCount={}",
                    deviceInfoId, records.size());
        }
        return records.get(0);
    }

    /**
     * 如果班次信息缺失，根据开始时间补充
     * <p>
     * 使用通用工具类 RecordHandlerUtils
     * </p>
     */
    private void fillShiftInfoIfMissing(DeviceStateRecordDO record, Long factoryId) {
        recordHandlerUtils.fillShiftInfoIfMissing(record, factoryId);
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
            StateValidationResult currentStateResult,
            StateValidationResult previousStateResult
    ) {}


    /**
     * 状态转换结果
     */
    private record StateTransitionResult(boolean needUpdateCache, boolean dbOperationSuccess) {}

    /**
     * 统一的班次截断处理逻辑
     * <p>
     * 统一处理进行中和结束状态的班次截断，解决逻辑重复和不一致的问题。
     * 处理策略：
     * 1. 先检查是否跨班次（无论是否超时长）
     * 2. 如果跨班次，按班次拆分
     * 3. 如果不跨班次但超时长，截断到班次结束
     * 4. 否则创建单条记录
     * </p>
     *
     * @param deviceInfoId 设备ID
     * @param orgFactoryId 工厂ID
     * @param stateCode 状态编码
     * @param startTs 开始时间
     * @param effectiveEndTs 有效结束时间（进行中状态使用当前时间）
     * @param shiftEndTs 班次结束时间
     * @param shiftDurationMs 班次时长（毫秒）
     * @param duration 状态持续时间（毫秒）
     * @param isOngoing 是否为进行中状态
     * @param isComplete 是否为完整状态（仅结束状态有效）
     * @param properties 扩展属性
     * @return 状态记录列表
     */
    private List<DeviceStateRecordDO> handleStateWithShiftLimit(Long deviceInfoId, Long orgFactoryId,
                                                                Integer stateCode, Long startTs, Long effectiveEndTs,
                                                                Long shiftEndTs, Long shiftDurationMs, Long duration,
                                                                boolean isOngoing, boolean isComplete,
                                                                Map<String, Object> properties) {
        // 先检查是否跨班次（无论是否超时长，都需要先检查跨班次，避免丢失数据）
        boolean crossesShift = timeRangeRecordHandler.checkIfCrossesShift(
                orgFactoryId, deviceInfoId, startTs, effectiveEndTs);

        if (crossesShift) {
            // 跨班次：按班次拆分（统一处理逻辑）
            log.info("[DeviceStateEventHandler] 状态跨班次，按班次拆分: deviceId={}, stateCode={}, " +
                            "startTs={}, effectiveEndTs={}, isOngoing={}",
                    deviceInfoId, stateCode, startTs, effectiveEndTs, isOngoing);
            return splitByShiftWithOngoingFlag(deviceInfoId, orgFactoryId, stateCode,
                    startTs, effectiveEndTs, isOngoing, properties);
        }

        // 不跨班次但超时长：截断到班次结束
        if (duration > shiftDurationMs) {
            log.warn("[DeviceStateEventHandler] 状态持续时间超过班次时长，截断到班次结束: deviceId={}, stateCode={}, " +
                            "startTs={}, originalEndTs={}, duration={}ms ({}小时), shiftDuration={}ms ({}小时), shiftEndTs={}, isOngoing={}",
                    deviceInfoId, stateCode, startTs, effectiveEndTs, duration,
                    duration / (60L * 60 * 1000), shiftDurationMs, shiftDurationMs / (60L * 60 * 1000), shiftEndTs, isOngoing);
            
            if (isOngoing) {
                // 进行中状态：创建结束记录和新的进行中记录
                DeviceStateRecordDO endedRecord = createSingleStateRecord(deviceInfoId, orgFactoryId, stateCode,
                        startTs, shiftEndTs, true, properties);
                DeviceStateRecordDO ongoingRecord = createSingleStateRecord(deviceInfoId, orgFactoryId, stateCode,
                        shiftEndTs, null, false, properties);
                return Arrays.asList(endedRecord, ongoingRecord);
            } else {
                // 结束状态：截断到班次结束
                DeviceStateRecordDO record = createSingleStateRecord(deviceInfoId, orgFactoryId, stateCode,
                        startTs, shiftEndTs, isComplete, properties);
                return Collections.singletonList(record);
            }
        }

        // 正常情况：不跨班次且未超班次时长
        DeviceStateRecordDO record = createSingleStateRecord(deviceInfoId, orgFactoryId, stateCode,
                startTs, isOngoing ? null : effectiveEndTs, !isOngoing && isComplete, properties);
        return Collections.singletonList(record);
    }

    /**
     * 按班次拆分状态记录，支持进行中状态标记
     *
     * @param deviceInfoId 设备ID
     * @param orgFactoryId 工厂ID
     * @param stateCode 状态编码
     * @param startTs 开始时间
     * @param endTs 结束时间
     * @param isOngoing 是否为进行中状态
     * @param properties 扩展属性
     * @return 拆分后的记录列表
     */
    private List<DeviceStateRecordDO> splitByShiftWithOngoingFlag(Long deviceInfoId, Long orgFactoryId,
                                                                   Integer stateCode, Long startTs, Long endTs,
                                                                   boolean isOngoing, Map<String, Object> properties) {
        List<DeviceStateRecordDO> records = splitByShift(deviceInfoId, orgFactoryId, stateCode,
                startTs, endTs, properties);

        // 如果是进行中状态，将最后一条记录设为进行中状态
        if (isOngoing && !records.isEmpty()) {
            DeviceStateRecordDO lastRecord = records.get(records.size() - 1);
            lastRecord.setEndTs(null);
            lastRecord.setDurationS(null);
            lastRecord.setIsComplete(false);
        }

        return records;
    }


    /**
     * 处理过期状态记录
     * 将超过阈值的时间标记为过期，避免数据爆炸和性能问题
     * <p>
     * 使用通用服务 TimeRangeRecordHandler.handleExpiredRecord()
     * </p>
     */
    private void handleExpiredState(DeviceStateRecordDO latestState, EventData eventData, Long orgFactoryId) {
        DeviceStateEnum stateEnum = DeviceStateEnum.fromCode(latestState.getStateCode());

        log.info("[DeviceStateEventHandler] 处理过期状态记录: deviceInfoId={}, state={}({}), startTs={}, currentTs={}",
                latestState.getDeviceInfoId(), stateEnum.name(), latestState.getStateCode(),
                latestState.getStartTs(), eventData.eventTimestamp());

        // 使用通用服务处理过期记录
        timeRangeRecordHandler.handleExpiredRecord(
                latestState,
                eventData.eventTimestamp(),
                orgFactoryId,
                // RecordUpdater: 更新数据库
                new com.weili.iot_portal.service.record.TimeRangeRecordHandler.RecordUpdater<DeviceStateRecordDO>() {
                    @Override
                    public void update(DeviceStateRecordDO record) {
                        // 设置 isComplete 为 false（状态表特有字段）
                        record.setIsComplete(false);
                        stateTimelineRepository.update(record);
                    }
                    
                    @Override
                    public void delete(DeviceStateRecordDO record) {
                        stateTimelineRepository.deleteById(record.getId());
                    }
                    
                    @Override
                    public void insert(DeviceStateRecordDO record) {
                        stateTimelineRepository.insert(record);
                    }
                }
        );

        log.info("[DeviceStateEventHandler] 已标记过期状态记录: deviceInfoId={}, state={}, endTs={}, duration=null",
                latestState.getDeviceInfoId(), stateEnum.name(), EXPIRED_END_TIMESTAMP);

        // 创建新的状态记录
        createNewStateAfterExpiry(latestState, eventData, orgFactoryId);
    }

    /**
     * 在处理过期状态后创建新状态记录
     * <p>
     * 使用统一的 createStateRecords 方法，会自动处理班次截断逻辑
     * </p>
     */
    private void createNewStateAfterExpiry(DeviceStateRecordDO expiredState, EventData eventData, Long orgFactoryId) {
        log.debug("[DeviceStateEventHandler] 为过期状态创建新状态记录: deviceInfoId={}, newState={}",
                expiredState.getDeviceInfoId(), eventData.currentState());

        // 创建新状态记录属性
        Map<String, Object> properties = DeviceStateUtils.createPropertiesWithOriginalState(
                eventData.currentStateResult(), null, eventData.eventTimestamp());

        // 保证 properties 非空
        if (properties == null) {
            properties = new HashMap<>();
        }

        // 添加过期处理的上下文信息
        properties.put("previous_expired", true);
        properties.put("previous_state_id", expiredState.getId());

        // 使用统一的创建逻辑，会自动处理班次截断
        List<DeviceStateRecordDO> newRecords = createStateRecords(
                expiredState.getDeviceInfoId(), orgFactoryId, eventData.currentStateCode(),
                eventData.eventTimestamp(), null, true, properties);

        // 插入新记录
        for (DeviceStateRecordDO record : newRecords) {
            stateTimelineRepository.insert(record);
            DeviceStateEnum newStateEnum = DeviceStateEnum.fromCode(record.getStateCode());
            log.debug("[DeviceStateEventHandler] 创建新状态记录: 状态={}({}), startTs={}, 关联过期记录ID={}",
                    newStateEnum.name(), record.getStateCode(), record.getStartTs(), expiredState.getId());
        }
    }

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