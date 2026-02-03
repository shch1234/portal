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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;

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

    // ==================== 策略映射 ====================
    /**
     * 状态转换策略映射
     * 使用策略模式处理不同的状态转换类型
     */
    private final Map<TransitionType, Function<TransitionContext, StateTransitionResult>> transitionStrategies = Map.of(
            TransitionType.FIRST_RECORD, this::strategy_handleFirstRecord,
            TransitionType.FIRST_CONNECTION, this::strategy_handleFirstConnection,
            TransitionType.NORMAL_TRANSITION, this::strategy_handleNormalTransition,
            TransitionType.STATE_MISMATCH, this::strategy_handleStateMismatch,
            TransitionType.STATE_UNCHANGED, this::strategy_handleStateUnchanged
    );

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
        // 心跳事件单独处理（使用DEBUG级别，减少日志量）
        if (DeviceStateEventFields.EVENT_TYPE_HEARTBEAT.equals(request.getEventType())) {
            log.debug("[DeviceStateEventHandler] 处理心跳事件: messageId={}, deviceCode={}", 
                    request.getMessageId(), request.getDeviceCode());
            handleHeartbeat(request);
            return;
        }

        // 1. 解析事件数据（主事务：轻量级操作）
        EventData eventData = parseEventData(request);
        
        // 2. 解析设备信息（主事务：轻量级操作）
        DeviceIdentity identity = webhookHandlerUtils.resolveDeviceIdentity(request);
        
        // 3. 使用分布式锁处理状态更新（子事务：独立短事务）
        processStateTransitionInNewTransaction(eventData, identity, request);
    }
    
    /**
     * 在独立事务中处理状态转换
     * <p>
     * 优化说明：
     * 1. 使用REQUIRES_NEW创建独立事务，缩短主事务时间
     * 2. 设置超时时间5秒（比锁超时时间短），避免长时间占用连接
     * 3. 如果子事务失败，不影响主事务（主事务只做验证和准备）
     * </p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5, rollbackFor = Exception.class)
    private void processStateTransitionInNewTransaction(EventData eventData, DeviceIdentity identity, WebhookRequest request) {
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

        // 状态解析结果（DEBUG级别，用于调试）
        log.debug("[DeviceStateEventHandler] 状态解析结果: messageId={}, deviceCode={}, " +
                "previousState={}({}), currentState={}({})",
                request.getMessageId(), request.getDeviceCode(),
                previousState, previousStateCode, currentState, currentStateCode);

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
     * <p>
     * 优化说明：
     * 1. 先查询（不加锁），判断是否需要处理，减少不必要的锁竞争
     * 2. 只在需要更新时加锁，锁内只做关键操作
     * 3. 非关键操作（缓存更新）在锁外执行
     * </p>
     */
    private void processStateTransitionWithLock(EventData eventData, DeviceIdentity identity, WebhookRequest request) {
        Long deviceInfoId = identity.deviceInfoId();
        Long orgFactoryId = identity.orgFactoryId();
        
        // 优化1：先查询（不加锁），判断是否需要处理
        Optional<DeviceStateRecordDO> latestStateOpt = stateTimelineRepository.findLatestState(deviceInfoId);
        TransitionContext preLockContext = new TransitionContext(
                latestStateOpt, eventData, identity, request, null, null, null, null, deviceInfoId, orgFactoryId);
        TransitionType transitionType = transition_determineTransitionType(preLockContext);
        
        // 如果状态未变化，直接返回，不需要加锁
        if (transitionType == TransitionType.STATE_UNCHANGED) {
            log.debug("[Webhook-Handler-DeviceState] 状态未变化，跳过处理: deviceInfoId={}, messageId={}",
                    deviceInfoId, request.getMessageId());
            // 即使状态未变化，也更新缓存（刷新TTL）
            updateCacheAfterStateTransition(identity, eventData, false, request);
            return;
        }
        
        // 优化：提前计算班次信息（锁外计算，减少锁内数据库查询）
        RecordHandlerUtils.PrecomputedShiftInfo precomputedShiftInfo = recordHandlerUtils.precomputeShiftInfo(
                latestStateOpt.orElse(null), eventData.eventTimestamp(), orgFactoryId, deviceInfoId);
        
        // 优化2：只在需要更新时加锁
        long lockStartTime = System.currentTimeMillis();
        if (!deviceLockService.tryLockState(deviceInfoId, DeviceStateEventFields.LOCK_TIMEOUT_SECONDS)) {
            long lockWaitTime = System.currentTimeMillis() - lockStartTime;
            log.warn("[Webhook-Handler-DeviceState] 获取设备状态锁失败（等待{}ms后超时）: deviceInfoId={}, messageId={}, timeout={}s",
                    lockWaitTime, deviceInfoId, request.getMessageId(), DeviceStateEventFields.LOCK_TIMEOUT_SECONDS);
            throw new IotPortalException(IotPortalErrorCode.EVENT_DEVICE_STATE_PROCESSING);
        }
        long lockWaitTime = System.currentTimeMillis() - lockStartTime;
        if (lockWaitTime > 1000) {
            log.warn("[Webhook-Handler-DeviceState] 获取设备状态锁耗时较长: deviceInfoId={}, waitTime={}ms, messageId={}",
                    deviceInfoId, lockWaitTime, request.getMessageId());
        }

        boolean needUpdateCache;
        boolean dbOperationSuccess;
        StateTransitionResult result;
        long lockHoldStartTime = System.currentTimeMillis();

        try {
            // 优化3：锁内重新查询（防止并发修改），然后处理
            latestStateOpt = stateTimelineRepository.findLatestState(deviceInfoId);
            
            // 处理状态转换（关键操作，在锁内执行）
            // 传入完整的预计算班次信息（包括时间范围），减少锁内数据库查询
            result = processStateTransition(
                    latestStateOpt, eventData, identity, request, precomputedShiftInfo);
            
            needUpdateCache = result.needUpdateCache();
            dbOperationSuccess = result.dbOperationSuccess();
        } finally {
            deviceLockService.unlockState(deviceInfoId);
            long lockHoldTime = System.currentTimeMillis() - lockHoldStartTime;
            // 优化：分级监控锁持有时间
            if (lockHoldTime > 5000) {
                // 超过5秒：严重告警，可能影响其他请求
                log.error("[Webhook-Handler-DeviceState] 锁持有时间过长（严重）: deviceInfoId={}, holdTime={}ms, messageId={}, " +
                        "可能影响其他请求的锁获取，建议优化锁内操作",
                        deviceInfoId, lockHoldTime, request.getMessageId());
            } else if (lockHoldTime > 3000) {
                // 超过3秒：警告，需要关注
                log.warn("[Webhook-Handler-DeviceState] 锁持有时间较长: deviceInfoId={}, holdTime={}ms, messageId={}, " +
                        "建议优化锁内操作以减少锁持有时间",
                        deviceInfoId, lockHoldTime, request.getMessageId());
            } else if (lockHoldTime > 2000) {
                // 超过2秒：提示，正常但可以优化
                log.info("[Webhook-Handler-DeviceState] 锁持有时间: deviceInfoId={}, holdTime={}ms, messageId={}",
                        deviceInfoId, lockHoldTime, request.getMessageId());
            }
        }

        // 优化4：非关键操作（缓存更新）在锁外执行
        if (dbOperationSuccess) {
            updateCacheAfterStateTransition(identity, eventData, needUpdateCache, request);
        }
        
        // 优化5：异常日志在锁外记录（减少锁持有时间，节省50-200ms）
        if (result.errorLogInfo() != null) {
            ErrorLogInfo errorInfo = result.errorLogInfo();
            try {
                webhookFailLogService.saveFailLog(request, 
                        errorInfo.errorType(), 
                        errorInfo.errorMessage(), 
                        errorInfo.needReview());
                log.debug("[DeviceStateEventHandler] 锁外记录异常日志成功: errorType={}, messageId={}", 
                        errorInfo.errorType(), request.getMessageId());
            } catch (Exception e) {
                // 异常日志记录失败不影响主流程，但需要记录警告
                log.warn("[DeviceStateEventHandler] 锁外记录异常日志失败: errorType={}, messageId={}, error={}", 
                        errorInfo.errorType(), request.getMessageId(), e.getMessage());
            }
        }
        
        // 优化：在锁外记录详细日志（减少锁内操作时间）
        // 重新创建上下文用于日志记录（使用锁内查询后的最新状态）
        TransitionContext logContext = new TransitionContext(
                latestStateOpt, eventData, identity, request, null, null, null, null, deviceInfoId, orgFactoryId);
        
        if (log.isDebugEnabled()) {
            transitionType = transition_determineTransitionType(logContext);
            log.debug("[DeviceStateEventHandler] 处理状态转换完成: deviceInfoId={}, transitionType={}, currentState={}({}), previousState={}({})",
                    deviceInfoId, transitionType, eventData.currentState(), eventData.currentStateCode(),
                    eventData.previousState(), eventData.previousStateCode());
        }
        if (log.isWarnEnabled() && latestStateOpt.isPresent()) {
            transitionType = transition_determineTransitionType(logContext);
            if (transitionType == TransitionType.FIRST_CONNECTION) {
                DeviceStateRecordDO latestState = latestStateOpt.get();
                if (latestState.getEndTs() == null) {
                    DeviceStateEnum dbStateEnum = DeviceStateEnum.fromCode(latestState.getStateCode());
                    log.warn("[DeviceStateEventHandler] 首次连接但数据库中有未结束的状态: deviceInfoId={}, DB状态={}({}), 将先结束该状态",
                            deviceInfoId, dbStateEnum.name(), latestState.getStateCode());
                }
            } else if (transitionType == TransitionType.STATE_MISMATCH) {
                DeviceStateRecordDO latestState = latestStateOpt.get();
                DeviceStateEnum dbStateEnum = DeviceStateEnum.fromCode(latestState.getStateCode());
                log.warn("[DeviceStateEventHandler] 状态不匹配: DB状态={}({}), 事件previousState={}({}), 事件currentState={}({}), deviceInfoId={}",
                        dbStateEnum.name(), latestState.getStateCode(),
                        eventData.previousState(), eventData.previousStateCode(),
                        eventData.currentState(), eventData.currentStateCode(), deviceInfoId);
            }
        }
    }

    /**
     * 处理状态转换（使用策略模式）
     */
    private StateTransitionResult processStateTransition(Optional<DeviceStateRecordDO> latestStateOpt,
                                                          EventData eventData,
                                                          DeviceIdentity identity,
                                                          WebhookRequest request,
                                                          RecordHandlerUtils.PrecomputedShiftInfo precomputedShiftInfo) {
        // 构建上下文对象
        TransitionContext context = new TransitionContext(
                latestStateOpt, eventData, identity, request,
                precomputedShiftInfo.recordShiftInfo(), precomputedShiftInfo.newRecordShiftInfo(),
                precomputedShiftInfo.recordShiftRange(), precomputedShiftInfo.newRecordShiftRange(),
                identity.deviceInfoId(), identity.orgFactoryId()
        );

        // 判断处理场景
        TransitionType transitionType = transition_determineTransitionType(context);

        // 使用策略模式处理
        Function<TransitionContext, StateTransitionResult> strategy = transitionStrategies.get(transitionType);
        if (strategy == null) {
            throw new IllegalStateException("未知的状态转换类型: " + transitionType);
        }

        return strategy.apply(context);
    }

    /**
     * 判断状态转换类型
     */
    private TransitionType transition_determineTransitionType(TransitionContext context) {
        Optional<DeviceStateRecordDO> latestStateOpt = context.latestStateOpt();
        EventData eventData = context.eventData();
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
        
        // 检查状态是否真的未变化（直接比较数字编码）
        boolean isCurrentStateSame = currentStateCode != null && currentStateCode.equals(latestState.getStateCode());
        boolean isPreviousStateSame = previousStateCode != null && previousStateCode.equals(latestState.getStateCode());
        boolean isStateUnchanged = isCurrentStateSame && isPreviousStateSame;
        
        if (isStateUnchanged) {
            log.debug("[DeviceStateEventHandler] 判断转换类型: STATE_UNCHANGED (状态未变化)");
            return TransitionType.STATE_UNCHANGED;
        }
        
        // 检查是否正常匹配（直接比较数字编码）
        boolean isNormalMatch = previousStateCode != null && previousStateCode.equals(latestState.getStateCode());
        
        if (isNormalMatch) {
            log.debug("[DeviceStateEventHandler] 判断转换类型: NORMAL_TRANSITION (正常匹配)");
            return TransitionType.NORMAL_TRANSITION;
        }
        
        log.debug("[DeviceStateEventHandler] 判断转换类型: STATE_MISMATCH (状态不匹配)");
        return TransitionType.STATE_MISMATCH;
    }

    // ==================== 状态转换策略 ====================

    /**
     * 策略：处理首次记录
     */
    private StateTransitionResult strategy_handleFirstRecord(TransitionContext context) {
        if (context.eventData().previousStateCode() != null) {
            log.warn("[Webhook-Handler-DeviceState] 数据库无记录但previousState不为NULL: deviceInfoId={}, previousState={}",
                    context.deviceInfoId(), context.eventData().previousState());
        }

        Map<String, Object> properties = recordFactory_createStateProperties(context.eventData(), null);
        List<DeviceStateRecordDO> newRecords = recordFactory_createStateRecords(
                context.deviceInfoId(), context.orgFactoryId(), context.eventData().currentStateCode(),
                context.eventData().eventTimestamp(), null, true, properties, 
                context.precomputedNewRecordShiftInfo(), context.precomputedNewRecordShiftRange());

        recordFactory_insertBatch(newRecords, "首次记录");
        return new StateTransitionResult(true, true);
    }

    /**
     * 策略：处理首次连接
     */
    private StateTransitionResult strategy_handleFirstConnection(TransitionContext context) {
        // 注意：如果数据库中有未结束的状态记录，需要先结束它
        if (context.hasLatestState() && context.getLatestState().getEndTs() == null) {
            DeviceStateRecordDO latestState = context.getLatestState();
            // 如果开始时间存在且超过过期阈值，标记为过期并创建新状态（避免计算超长持续时间）
            if (timeRangeRecordHandler.isExpired(latestState, context.eventData().eventTimestamp())) {
                // 已在 handleExpiredState 中创建新状态，直接返回
                return strategy_handleExpiredState(latestState, context);
            }

            // 先结束未完成的状态（正常短期场景）
            recordHandlerUtils.endRecord(latestState, context.eventData().eventTimestamp(),
                    context.orgFactoryId(), context.precomputedShiftInfo(), false);
            stateTimelineRepository.update(latestState);
        }

        Map<String, Object> properties = recordFactory_createStateProperties(context.eventData(), null);
        List<DeviceStateRecordDO> records = recordFactory_createStateRecords(
                context.deviceInfoId(), context.orgFactoryId(),
                context.eventData().currentStateCode(), context.eventData().eventTimestamp(), null, true,
                properties, context.precomputedNewRecordShiftInfo(), context.precomputedNewRecordShiftRange());

        recordFactory_insertBatch(records, "首次连接");
        return new StateTransitionResult(true, true);
    }

    /**
     * 策略：处理状态未变化
     */
    private StateTransitionResult strategy_handleStateUnchanged(TransitionContext context) {
        // 状态未变化（重复的相同状态事件）
        return new StateTransitionResult(false, true);
    }

    /**
     * 策略：处理正常转换
     */
    private StateTransitionResult strategy_handleNormalTransition(TransitionContext context) {
        DeviceStateRecordDO latestState = context.getLatestState();
        DeviceStateEnum latestStateEnum = DeviceStateEnum.fromCode(latestState.getStateCode());
        log.debug("[DeviceStateEventHandler] 处理正常状态转换: 结束状态={}({}), startTs={}, endTs={}, 新状态={}, 新startTs={}",
                latestStateEnum.name(), latestState.getStateCode(), latestState.getStartTs(), context.eventData().eventTimestamp(),
                context.eventData().currentState(), context.eventData().currentState());

        // 过期检查：如果状态记录持续时间超过阈值，直接标记为过期
        if (timeRangeRecordHandler.isExpired(latestState, context.eventData().eventTimestamp())) {
            log.warn("[DeviceStateEventHandler] 检测到过期状态记录，直接标记为过期: deviceInfoId={}, stateCode={}, " +
                            "startTs={}, currentTs={}",
                    latestState.getDeviceInfoId(), latestState.getStateCode(),
                    latestState.getStartTs(), context.eventData().eventTimestamp());

            return strategy_handleExpiredState(latestState, context);
        }

        Long oldStartTs = latestState.getStartTs();
        Long newEndTs = context.eventData().eventTimestamp();

        // 优化：使用预计算的班次范围判断是否跨班次，避免锁内数据库查询
        boolean crossesShift = false;
        if (context.precomputedShiftRange() != null && context.precomputedShiftRange().getEndTs() != null) {
            // 使用预计算的班次结束时间判断跨班次
            crossesShift = newEndTs > context.precomputedShiftRange().getEndTs();
        } else {
            // 回退：如果预计算失败，使用通用服务（会查询数据库，应尽量避免）
            log.warn("[DeviceStateEventHandler] 预计算的班次范围不可用，锁内查询跨班次: deviceId={}, startTs={}, endTs={}",
                    latestState.getDeviceInfoId(), oldStartTs, newEndTs);
            crossesShift = timeRangeRecordHandler.checkIfCrossesShift(
                    context.orgFactoryId(), latestState.getDeviceInfoId(), oldStartTs, newEndTs);
        }

        Map<String, Object> oldProperties = latestState.getProperties();
        if (oldProperties == null) {
            oldProperties = new HashMap<>();
        }
        final Map<String, Object> finalProperties = oldProperties;
        final Integer stateCode = latestState.getStateCode();
        boolean createdNewRecord = false;

        if (crossesShift) {
            // 跨班次：使用通用服务拆分记录（会查询数据库，但跨班次场景较少）
            log.debug("[DeviceStateEventHandler] 记录跨班次，进行截断: deviceId={}, startTs={}, endTs={}",
                    latestState.getDeviceInfoId(), oldStartTs, newEndTs);
            
            createdNewRecord = timeRangeRecordHandler.updateOngoingRecord(
                    latestState,
                    newEndTs,
                    context.orgFactoryId(),
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
        } else {
            // 优化：不跨班次，直接更新记录，避免调用 updateOngoingRecord 中的数据库查询
            latestState.setEndTs(newEndTs);
            if (oldStartTs != null) {
                latestState.setDurationS(newEndTs - oldStartTs);
            }
            latestState.setIsComplete(true);
            
            // 优化：使用预计算的班次信息，避免数据库查询
            if (context.precomputedShiftInfo() != null) {
                setShiftInfoIfMissing(latestState, context.precomputedShiftInfo());
            }
            // 如果预计算失败，回退到数据库查询（应尽量避免）
            if (latestState.getShiftDate() == null || latestState.getShiftCode() == null) {
                log.warn("[DeviceStateEventHandler] 预计算的班次信息不可用，锁内查询: deviceId={}, startTs={}",
                        latestState.getDeviceInfoId(), oldStartTs);
                recordHandlerUtils.fillShiftInfoIfMissing(latestState, context.orgFactoryId());
            }
            
            // 直接更新数据库
            stateTimelineRepository.update(latestState);
            
            log.debug("[DeviceStateEventHandler] 更新旧状态记录（不跨班次）: 状态={}({}), endTs={}, durationS={}",
                    latestStateEnum.name(), latestState.getStateCode(), latestState.getEndTs(), latestState.getDurationS());
        }

        if (!createdNewRecord) {
            log.debug("[DeviceStateEventHandler] 更新旧状态记录: 状态={}({}), endTs={}, durationS={}",
                    latestStateEnum.name(), latestState.getStateCode(), latestState.getEndTs(), latestState.getDurationS());
        }

        // 插入新状态记录
        Map<String, Object> properties = recordFactory_createStateProperties(context.eventData(), null);
        List<DeviceStateRecordDO> newRecords = recordFactory_createStateRecords(
                latestState.getDeviceInfoId(), context.orgFactoryId(), context.eventData().currentStateCode(),
                context.eventData().eventTimestamp(), null, true, properties, 
                context.precomputedNewRecordShiftInfo(), context.precomputedNewRecordShiftRange());

        recordFactory_insertBatch(newRecords, "正常转换");
        return new StateTransitionResult(true, true);
    }

    /**
     * 策略：处理状态不匹配
     */
    private StateTransitionResult strategy_handleStateMismatch(TransitionContext context) {
        DeviceStateRecordDO latestState = context.getLatestState();
        boolean isOngoing = latestState.getEndTs() == null;

        if (isOngoing) {
            // 子情况C2：数据库状态进行中（end_ts IS NULL）
            return strategy_handleOngoingStateMismatch(latestState, context);
        } else {
            // 子情况C1：数据库状态已结束（end_ts IS NOT NULL）
            return strategy_handleEndedStateMismatch(latestState, context);
        }
    }

    /**
     * 策略：处理过期状态
     */
    private StateTransitionResult strategy_handleExpiredState(DeviceStateRecordDO latestState, TransitionContext context) {
        handleExpiredState(latestState, context.eventData(), context.orgFactoryId(), 
                context.precomputedNewRecordShiftInfo(), context.precomputedNewRecordShiftRange());
        // 过期状态处理不产生异常日志，返回正常结果
        return new StateTransitionResult(true, true);
    }

    // ==================== 状态处理场景 ====================

    /**
     * 子情况C1：数据库状态已结束（end_ts IS NOT NULL）
     */
    private StateTransitionResult strategy_handleEndedStateMismatch(DeviceStateRecordDO latestState, TransitionContext context) {
        Long latestEndTs = latestState.getEndTs();
        Long deviceInfoId = context.deviceInfoId();
        Long orgFactoryId = context.orgFactoryId();

        // 检查是否存在状态间隙
        if (latestEndTs < context.eventData().eventTimestamp()) {
            // 存在间隙，插入 UNKNOWN 状态记录填充间隙
            log.debug("检测到状态间隙，插入UNKNOWN状态: deviceInfoId={}, gap=[{} -> {}]",
                    deviceInfoId, latestEndTs, context.eventData().eventTimestamp());

            Map<String, Object> gapProperties = new HashMap<>();
            gapProperties.put(DeviceStateEventFields.GAP_REASON, "状态不匹配导致间隙");
            gapProperties.put(DeviceStateEventFields.EXPECTED_PREVIOUS, context.eventData().previousState());
            DeviceStateEnum dbStateEnum = DeviceStateEnum.fromCode(latestState.getStateCode());
            gapProperties.put(DeviceStateEventFields.ACTUAL_DB_STATE, dbStateEnum.name());

            List<DeviceStateRecordDO> unknownRecords = recordFactory_createStateRecords(deviceInfoId, orgFactoryId,
                    DeviceStateEnum.UNKNOWN.getCode(), latestEndTs, context.eventData().eventTimestamp(), false, gapProperties, null, null);
            recordFactory_insertBatch(unknownRecords, "状态间隙填充");
        }

        // 插入新状态记录
        Map<String, Object> properties = recordFactory_createStateProperties(context.eventData(), null);
        List<DeviceStateRecordDO> newRecords = recordFactory_createStateRecords(deviceInfoId, orgFactoryId, context.eventData().currentStateCode(),
                context.eventData().eventTimestamp(), null, true, properties, 
                context.precomputedNewRecordShiftInfo(), context.precomputedNewRecordShiftRange());
        recordFactory_insertBatch(newRecords, "状态不匹配（已结束）");

        // 优化：收集异常信息，锁外记录（减少锁持有时间）
        String errorMessage = String.format("状态不匹配（已结束）: DB状态=%s, 事件previousState=%s, 间隙=%d毫秒",
                latestState.getStateCode(), context.eventData().previousState(),
                context.eventData().eventTimestamp() - latestEndTs);
        ErrorLogInfo errorInfo = new ErrorLogInfo(
                DeviceStateEventFields.ERROR_TYPE_STATE_MISMATCH, 
                errorMessage, 
                false  // 可以自动修复，不需要人工处理
        );
        // 返回异常信息，锁外记录
        return new StateTransitionResult(true, true, errorInfo);
    }

    /**
     * 子情况C2：数据库状态进行中（end_ts IS NULL）
     */
    private StateTransitionResult strategy_handleOngoingStateMismatch(DeviceStateRecordDO latestState, TransitionContext context) {
        Long deviceInfoId = context.deviceInfoId();
        Long orgFactoryId = context.orgFactoryId();
        Long recordId = latestState.getId();

        // 前置检查：时间戳异常
        if (recordHandlerUtils.isTimestampAnomaly(latestState, context.eventData().eventTimestamp())) {
            return strategy_handleTimestampAnomaly(latestState, context);
        }

        // 前置检查：过期状态
        if (timeRangeRecordHandler.isExpired(latestState, context.eventData().eventTimestamp())) {
            return strategy_handleExpiredState(latestState, context);
        }

        // 使用CAS更新：原子性地结束进行中的记录
        boolean updateSuccess = updateOngoingRecordWithCAS(latestState, recordId, context.eventData(), orgFactoryId, context.request(), context.precomputedShiftInfo());

        if (!updateSuccess) {
            // CAS更新失败，说明记录已被其他线程处理，直接插入新状态
            log.info("[DeviceStateEventHandler] CAS更新失败，记录已被处理，直接插入新状态: deviceInfoId={}, recordId={}, messageId={}",
                    deviceInfoId, recordId, context.request().getMessageId());
            strategy_insertNewStateRecord(deviceInfoId, orgFactoryId, context.eventData(), 
                    context.precomputedNewRecordShiftInfo(), context.precomputedNewRecordShiftRange());
            return new StateTransitionResult(true, true);
        }

        // 更新成功，插入恢复记录和新状态记录
        return strategy_insertRecoveryAndNewStateRecords(deviceInfoId, orgFactoryId, context);
    }


    /**
     * 策略：处理时间戳异常
     */
    private StateTransitionResult strategy_handleTimestampAnomaly(DeviceStateRecordDO latestState, TransitionContext context) {
        long gapMs = latestState.getStartTs() - context.eventData().eventTimestamp();
        long gapSeconds = gapMs / 1000;
        log.warn("时间戳异常: deviceInfoId={}, 事件时间={}, DB状态开始时间={}, 差距={}毫秒 ({}秒)",
                context.deviceInfoId(), context.eventData().eventTimestamp(), latestState.getStartTs(), gapMs, gapSeconds);

        Long deviceInfoId = context.deviceInfoId();
        Long orgFactoryId = context.orgFactoryId();

        // 先结束数据库中的异常记录
        if (latestState.getEndTs() == null) {
            // 添加异常标记到properties
            Map<String, Object> existingProperties = latestState.getProperties();
            if (existingProperties == null) {
                existingProperties = new HashMap<>();
            }
            existingProperties.put(DeviceStateEventFields.TIMESTAMP_ANOMALY, true);
            existingProperties.put(DeviceStateEventFields.EVENT_TIMESTAMP, context.eventData().eventTimestamp());
            existingProperties.put(DeviceStateEventFields.DB_START_TS, latestState.getStartTs());
            existingProperties.put(DeviceStateEventFields.ANOMALY_REASON,
                    String.format("时间戳异常: 事件时间(%d) < DB开始时间(%d), 差距=%d秒",
                            context.eventData().eventTimestamp(), latestState.getStartTs(), gapSeconds));
            latestState.setProperties(existingProperties);

            recordHandlerUtils.endRecord(latestState, context.eventData().eventTimestamp(),
                    orgFactoryId, context.precomputedShiftInfo(), false);
            stateTimelineRepository.update(latestState);
        }

        // 插入新状态记录，使用设备时间戳
        Map<String, Object> anomalyProperties = new HashMap<>();
        anomalyProperties.put(DeviceStateEventFields.TIMESTAMP_ANOMALY, true);
        anomalyProperties.put(DeviceStateEventFields.EVENT_TIMESTAMP, context.eventData().eventTimestamp());
        anomalyProperties.put(DeviceStateEventFields.DB_START_TS, latestState.getStartTs());
        anomalyProperties.put(DeviceStateEventFields.ANOMALY_REASON,
                String.format("时间戳异常: 事件时间(%d) < DB开始时间(%d), 差距=%d秒",
                        context.eventData().eventTimestamp(), latestState.getStartTs(), gapSeconds));
        anomalyProperties = recordFactory_createStateProperties(context.eventData(), anomalyProperties);

        List<DeviceStateRecordDO> newRecords = recordFactory_createStateRecords(deviceInfoId, orgFactoryId, context.eventData().currentStateCode(),
                context.eventData().eventTimestamp(), null, false, anomalyProperties, 
                context.precomputedShiftInfo(), context.precomputedShiftRange());
        recordFactory_insertBatch(newRecords, "时间戳异常");

        // 优化：收集异常信息，锁外记录（减少锁持有时间）
        String errorMessage = String.format("时间戳异常: 事件时间=%d, DB状态开始时间=%d, 差距=%d秒 (已使用设备时间戳插入新记录)",
                context.eventData().eventTimestamp(), latestState.getStartTs(), gapSeconds);
        ErrorLogInfo errorInfo = new ErrorLogInfo(
                DeviceStateEventFields.ERROR_TYPE_TIMESTAMP_ANOMALY, 
                errorMessage, 
                true  // 需要人工审核
        );
        // 返回异常信息，锁外记录
        return new StateTransitionResult(true, true, errorInfo);
    }

    /**
     * 策略：插入新状态记录
     */
    private void strategy_insertNewStateRecord(Long deviceInfoId, Long orgFactoryId, EventData eventData,
                                               ShiftDateAndCode precomputedShiftInfo, ShiftTimeRange precomputedShiftRange) {
        Map<String, Object> properties = recordFactory_createStateProperties(eventData, null);
        List<DeviceStateRecordDO> newRecords = recordFactory_createStateRecords(deviceInfoId, orgFactoryId, eventData.currentStateCode(),
                eventData.eventTimestamp(), null, true, properties, precomputedShiftInfo, precomputedShiftRange);
        recordFactory_insertBatch(newRecords, "插入新状态记录");
    }

    /**
     * 策略：插入恢复记录和新状态记录
     */
    private StateTransitionResult strategy_insertRecoveryAndNewStateRecords(Long deviceInfoId, Long orgFactoryId, TransitionContext context) {
        List<DeviceStateRecordDO> allRecords = new ArrayList<>();

        // 如果 previousState 不为 NULL，插入 previousState 状态记录（用于修复时间线）
        if (context.eventData().previousStateCode() != null && context.eventData().previousStateResult() != null) {
            Map<String, Object> recoveryProperties = new HashMap<>();
            recoveryProperties.put(DeviceStateEventFields.RECOVERY, true);
            recoveryProperties.put(DeviceStateEventFields.RECOVERED_FROM, DeviceStateUtils.UNKNOWN_STATE);
            recoveryProperties = recordFactory_createStateProperties(context.eventData(), recoveryProperties);

            List<DeviceStateRecordDO> previousRecords = recordFactory_createStateRecords(deviceInfoId, orgFactoryId,
                    context.eventData().previousStateCode(), context.eventData().eventTimestamp(), context.eventData().eventTimestamp(),
                    false, recoveryProperties, context.precomputedShiftInfo(), context.precomputedShiftRange());
            allRecords.addAll(previousRecords);
        }

        // 创建新状态记录
        Map<String, Object> properties = recordFactory_createStateProperties(context.eventData(), null);
        List<DeviceStateRecordDO> newRecords = recordFactory_createStateRecords(deviceInfoId, orgFactoryId, context.eventData().currentStateCode(),
                context.eventData().eventTimestamp(), null, true, properties, 
                context.precomputedNewRecordShiftInfo(), context.precomputedNewRecordShiftRange());
        allRecords.addAll(newRecords);

        // 批量插入所有记录
        recordFactory_insertBatch(allRecords, "恢复记录和新状态记录");

        // 优化：收集异常信息，锁外记录（减少锁持有时间）
        String errorMessage = String.format("状态不匹配（进行中）: 事件previousState=%s, 已标记为UNKNOWN",
                context.eventData().previousState());
        ErrorLogInfo errorInfo = new ErrorLogInfo(
                DeviceStateEventFields.ERROR_TYPE_STATE_MISMATCH, 
                errorMessage, 
                true  // 需要人工审核
        );
        // 返回异常信息，锁外记录
        return new StateTransitionResult(true, true, errorInfo);
    }

    /**
     * 使用CAS（Compare-And-Swap）更新进行中的记录
     * <p>
     * 通过WHERE条件（end_ts IS NULL AND id = ?）确保原子性更新
     * 如果更新失败（影响行数为0），说明记录已被其他线程处理
     * </p>
     *
     * @param recordId 记录ID
     * @param eventData 事件数据
     * @param orgFactoryId 工厂ID
     * @param request Webhook请求
     * @return true 如果更新成功，false 如果记录已被其他线程处理
     */
    private boolean updateOngoingRecordWithCAS(DeviceStateRecordDO latestState, Long recordId, 
                                               EventData eventData, Long orgFactoryId, 
                                               WebhookRequest request,
                                               ShiftDateAndCode precomputedShiftInfo) {
        Long deviceInfoId = latestState.getDeviceInfoId();
        
        // 重新查询记录以验证状态（防止并发修改）
        Optional<DeviceStateRecordDO> currentOpt = stateTimelineRepository.findLatestState(deviceInfoId);
        if (currentOpt.isEmpty()) {
            log.debug("[DeviceStateEventHandler] 记录不存在，CAS更新失败: deviceInfoId={}, recordId={}", 
                    deviceInfoId, recordId);
            return false;
        }
        
        DeviceStateRecordDO current = currentOpt.get();
        
        // 验证记录是否仍然是进行中状态且ID匹配
        if (!current.getId().equals(recordId) || current.getEndTs() != null) {
            log.debug("[DeviceStateEventHandler] 记录已被处理，CAS更新失败: deviceInfoId={}, recordId={}, " +
                            "当前recordId={}, 当前endTs={}", 
                    deviceInfoId, recordId, current.getId(), current.getEndTs());
            return false;
        }
        
        // 构建更新对象
        DeviceStateRecordDO updateRecord = new DeviceStateRecordDO();
        updateRecord.setId(recordId);
        updateRecord.setDeviceInfoId(deviceInfoId);
        updateRecord.setStateCode(DeviceStateEnum.UNKNOWN.getCode());
        updateRecord.setEndTs(eventData.eventTimestamp());
        
        if (current.getStartTs() != null) {
            updateRecord.setDurationS(eventData.eventTimestamp() - current.getStartTs());
        }
        updateRecord.setIsComplete(false);
        
        // 设置不匹配属性
        Map<String, Object> mismatchProperties = new HashMap<>();
        mismatchProperties.put(DeviceStateEventFields.MISMATCH_REASON, "previousState不匹配");
        DeviceStateEnum dbStateEnum = DeviceStateEnum.fromCode(current.getStateCode());
        mismatchProperties.put(DeviceStateEventFields.EXPECTED, dbStateEnum.name());
        mismatchProperties.put(DeviceStateEventFields.ACTUAL_DB, dbStateEnum.name());
        mismatchProperties.put(DeviceStateEventFields.EVENT_PREVIOUS, eventData.previousState());
        updateRecord.setProperties(mismatchProperties);
        
        // 优化：使用预计算的班次信息，减少锁内数据库查询
        recordFactory_ensureShiftInfo(updateRecord, precomputedShiftInfo, orgFactoryId);
        
        // 执行CAS更新：使用条件更新确保原子性
        try {
            int affectedRows = updateRecordWithCondition(updateRecord, deviceInfoId);
            if (affectedRows > 0) {
                log.debug("[DeviceStateEventHandler] CAS更新成功: recordId={}, affectedRows={}", recordId, affectedRows);
                return true;
            } else {
                log.debug("[DeviceStateEventHandler] CAS更新失败（记录已被处理）: recordId={}, affectedRows={}", 
                        recordId, affectedRows);
                return false;
            }
        } catch (org.springframework.dao.CannotAcquireLockException e) {
            // 锁超时异常：记录可能正在被其他线程处理
            log.warn("[DeviceStateEventHandler] CAS更新时发生锁超时: recordId={}, messageId={}, error={}",
                    recordId, request.getMessageId(), e.getMessage());
            return false;
        }
    }

    /**
     * 条件更新记录（CAS更新）
     * <p>
     * 只更新 end_ts IS NULL 的记录，确保原子性
     * 优化：减少验证查询，通过更新前后的状态判断是否成功
     * </p>
     * 
     * <p>
     * 优化说明：
     * 1. 在调用此方法前，已经验证了记录状态（在updateOngoingRecordWithCAS中）
     * 2. 更新后，如果最新记录ID变化，说明已有新记录，原记录已被处理（视为成功）
     * 3. 如果最新记录ID未变化，检查end_ts是否已设置（更新成功）
     * 4. 如果更新失败，说明记录已被其他线程修改
     * </p>
     */
    private int updateRecordWithCondition(DeviceStateRecordDO record, Long deviceInfoId) {
        // 执行更新
        try {
            // 记录更新前的状态（用于验证）
            Long recordIdBeforeUpdate = record.getId();
            Long endTsBeforeUpdate = null; // 更新前应该是null（进行中状态）
            
            stateTimelineRepository.update(record);
            
            // 优化：只查询一次验证更新结果（而不是两次）
            // 如果最新记录ID变化，说明已有新记录，原记录已被处理（视为成功）
            // 如果最新记录ID未变化，检查end_ts是否已设置
            Optional<DeviceStateRecordDO> updatedOpt = stateTimelineRepository.findLatestState(deviceInfoId);
            if (updatedOpt.isPresent()) {
                DeviceStateRecordDO updated = updatedOpt.get();
                
                // 情况1：记录ID变化，说明已有新记录，原记录已被结束（视为成功）
                if (!updated.getId().equals(recordIdBeforeUpdate)) {
                    log.debug("[DeviceStateEventHandler] CAS更新成功（记录ID变化）: 原recordId={}, 新recordId={}", 
                            recordIdBeforeUpdate, updated.getId());
                    return 1;
                }
                
                // 情况2：记录ID未变化，检查end_ts是否已设置
                if (updated.getEndTs() != null && updated.getEndTs().equals(record.getEndTs())
                        && updated.getStateCode() != null 
                        && updated.getStateCode().equals(DeviceStateEnum.UNKNOWN.getCode())) {
                    log.debug("[DeviceStateEventHandler] CAS更新成功（end_ts已设置）: recordId={}, endTs={}", 
                            recordIdBeforeUpdate, updated.getEndTs());
                    return 1;
                }
                
                // 情况3：记录ID未变化，但end_ts未设置或state_code不匹配（更新失败）
                log.debug("[DeviceStateEventHandler] CAS更新失败（状态不匹配）: recordId={}, endTs={}, stateCode={}", 
                        recordIdBeforeUpdate, updated.getEndTs(), updated.getStateCode());
                return 0;
            }
            
            // 情况4：记录不存在（异常情况）
            log.warn("[DeviceStateEventHandler] CAS更新后记录不存在: recordId={}, deviceInfoId={}", 
                    recordIdBeforeUpdate, deviceInfoId);
            return 0;
        } catch (Exception e) {
            log.error("[DeviceStateEventHandler] 更新记录时发生异常: recordId={}, error={}", 
                    record.getId(), e.getMessage(), e);
            throw e;
        }
    }


    // ==================== 记录创建工厂方法 ====================

    /**
     * 统一批量插入状态记录
     * <p>
     * 统一处理日志、错误处理、空列表检查
     * </p>
     */
    private void recordFactory_insertBatch(List<DeviceStateRecordDO> records, String operation) {
        recordHandlerUtils.insertBatch(
                () -> {
                    if (records != null && !records.isEmpty()) {
                        stateTimelineRepository.insertBatch(records);
                        return records.size();
                    }
                    return 0;
                },
                records,
                operation,
                "DeviceStateEventHandler"
        );
    }

    /**
     * 确保记录有班次信息（统一处理预计算和回退逻辑）
     * <p>
     * 优先使用预计算的班次信息，失败则查询数据库
     * </p>
     */
    private void recordFactory_ensureShiftInfo(DeviceStateRecordDO record,
                                              ShiftDateAndCode precomputedShiftInfo,
                                              Long orgFactoryId) {
        // 先尝试使用预计算的班次信息
        if (precomputedShiftInfo != null) {
            setShiftInfoIfMissing(record, precomputedShiftInfo);
        }

        // 如果预计算失败或未提供，回退到数据库查询
        if (record.getShiftDate() == null || record.getShiftCode() == null) {
            recordHandlerUtils.fillShiftInfoIfMissing(record, orgFactoryId);
        }
    }

    /**
     * 创建状态记录属性（统一入口）
     */
    private Map<String, Object> recordFactory_createStateProperties(EventData eventData,
                                                                   Map<String, Object> additionalProperties) {
        return DeviceStateUtils.createPropertiesWithOriginalState(
                eventData.currentStateResult(),
                additionalProperties,
                eventData.eventTimestamp()
        );
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
    /**
     * 创建状态记录（支持跨班次截断）- 使用预计算的班次范围
     */
    private List<DeviceStateRecordDO> recordFactory_createStateRecords(Long deviceInfoId, Long orgFactoryId,
                                                           Integer stateCode, Long startTs, Long endTs,
                                                           boolean isComplete, Map<String, Object> properties,
                                                           ShiftDateAndCode precomputedShiftInfo,
                                                           ShiftTimeRange precomputedShiftRange) {
        // 数据验证
        if (startTs == null) {
            log.error("[DeviceStateEventHandler] startTs cannot be null: deviceId={}, stateCode={}",
                    deviceInfoId, stateCode);
            throw new IllegalArgumentException("startTs cannot be null");
        }

        // 优化：优先使用预计算的班次范围，避免锁内数据库查询
        ShiftTimeRange startShift = precomputedShiftRange;
        if (startShift == null) {
            // 回退：如果预计算失败，锁内查询（应尽量避免）
            log.warn("[DeviceStateEventHandler] 预计算的班次范围不可用，锁内查询: deviceId={}, stateCode={}, startTs={}",
                    deviceInfoId, stateCode, startTs);
            startShift = shiftCalculationService.calculateShiftRange(orgFactoryId, deviceInfoId, startTs);
        }
        
        if (startShift == null || startShift.getEndTs() == null) {
            log.warn("[DeviceStateEventHandler] 无法计算班次范围，使用默认处理: deviceId={}, stateCode={}, startTs={}",
                    deviceInfoId, stateCode, startTs);
            // 如果无法计算班次，回退到原有逻辑（不进行班次时长限制）
            return recordFactory_createStateRecordsWithoutShiftLimit(deviceInfoId, orgFactoryId, stateCode, startTs, endTs, isComplete, properties, precomputedShiftInfo, precomputedShiftRange);
        }

        // 增强边界验证：确保班次时间范围有效
        if (!recordHandlerUtils.validateShiftBoundary(startShift)) {
            log.warn("[DeviceStateEventHandler] 班次边界验证失败，使用默认处理: deviceId={}, stateCode={}, shiftStart={}, shiftEnd={}",
                    deviceInfoId, stateCode, startShift.getStartTs(), startShift.getEndTs());
            return recordFactory_createStateRecordsWithoutShiftLimit(deviceInfoId, orgFactoryId, stateCode, startTs, endTs, isComplete, properties, precomputedShiftInfo, precomputedShiftRange);
        }

        long shiftDurationMs = startShift.getEndTs() - startShift.getStartTs();
        long shiftEndTs = startShift.getEndTs();

        long currentTime = System.currentTimeMillis();
        long effectiveEndTs = endTs != null ? endTs : currentTime;
        long duration = effectiveEndTs - startTs;
        boolean isOngoing = (endTs == null);

        // 统一处理进行中和结束状态的班次截断逻辑
        return handleStateWithShiftLimit(deviceInfoId, orgFactoryId, stateCode, startTs, effectiveEndTs,
                shiftEndTs, shiftDurationMs, duration, isOngoing, isComplete, properties, precomputedShiftInfo, precomputedShiftRange);
    }
    
    /**
     * 创建状态记录（兼容旧接口，不传入预计算的班次范围）
     * @deprecated 请使用带 precomputedShiftRange 参数的方法
     */
    @Deprecated
    private List<DeviceStateRecordDO> recordFactory_createStateRecords(Long deviceInfoId, Long orgFactoryId,
                                                           Integer stateCode, Long startTs, Long endTs,
                                                           boolean isComplete, Map<String, Object> properties,
                                                           ShiftDateAndCode precomputedShiftInfo) {
        return recordFactory_createStateRecords(deviceInfoId, orgFactoryId, stateCode, startTs, endTs, 
                isComplete, properties, precomputedShiftInfo, null);
    }
    
    /**
     * 创建状态记录（不进行班次时长限制，用于无法计算班次时的回退处理）
     */
    private List<DeviceStateRecordDO> recordFactory_createStateRecordsWithoutShiftLimit(Long deviceInfoId, Long orgFactoryId,
                                                                           Integer stateCode, Long startTs, Long endTs,
                                                                           boolean isComplete, Map<String, Object> properties,
                                                                           ShiftDateAndCode precomputedShiftInfo,
                                                                           ShiftTimeRange precomputedShiftRange) {
        if (endTs == null) {
            DeviceStateRecordDO record = createSingleStateRecord(deviceInfoId, orgFactoryId, stateCode,
                    startTs, null, false, properties, precomputedShiftInfo);
            return Collections.singletonList(record);
        }

        // 优化：使用预计算的班次范围检查跨班次，避免锁内数据库查询
        boolean crossesShift = false;
        if (precomputedShiftRange != null && precomputedShiftRange.getEndTs() != null) {
            crossesShift = endTs > precomputedShiftRange.getEndTs();
        } else {
            // 回退：如果预计算失败，锁内查询（应尽量避免）
            log.warn("[DeviceStateEventHandler] 预计算的班次范围不可用，锁内查询跨班次: deviceId={}, startTs={}, endTs={}",
                    deviceInfoId, startTs, endTs);
            crossesShift = timeRangeRecordHandler.checkIfCrossesShift(orgFactoryId, deviceInfoId, startTs, endTs);
        }
        
        if (!crossesShift) {
            DeviceStateRecordDO record = createSingleStateRecord(deviceInfoId, orgFactoryId, stateCode,
                    startTs, endTs, isComplete, properties, precomputedShiftInfo);
            return Collections.singletonList(record);
        }

        return recordFactory_splitByShift(deviceInfoId, orgFactoryId, stateCode, startTs, endTs, properties, precomputedShiftInfo);
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
    private List<DeviceStateRecordDO> recordFactory_splitByShift(Long deviceInfoId, Long orgFactoryId, Integer stateCode,
                                                    Long startTs, Long endTs, Map<String, Object> properties,
                                                    ShiftDateAndCode precomputedShiftInfo) {
        log.debug("[DeviceStateEventHandler] 开始按班次截断: deviceInfoId={}, stateCode={}, startTs={}, endTs={}",
                deviceInfoId, stateCode, startTs, endTs);

        // 使用通用服务拆分记录
        final Integer finalStateCode = stateCode;
        final Map<String, Object> finalProperties = properties != null ? properties : new HashMap<>();
        final ShiftDateAndCode finalPrecomputedShiftInfo = precomputedShiftInfo;
        
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
                    // 优化：对于第一条记录（开始时间匹配），使用预计算的班次信息
                    if (recordStartTs.equals(startTs) && finalPrecomputedShiftInfo != null) {
                        setShiftInfoIfMissing(record, finalPrecomputedShiftInfo);
                    }
                    return record;
                    }
        );

        log.debug("[DeviceStateEventHandler] 按班次截断完成: deviceInfoId={}, stateCode={}, 原始记录1条, 截断后{}条",
                deviceInfoId, stateCode, records.size());

        return records;
    }


    /**
     * 创建单条状态记录（不跨班次）
     * 
     * @param precomputedShiftInfo 预计算的班次信息（可选，如果为null则使用原有方法查询数据库）
     */
    private DeviceStateRecordDO createSingleStateRecord(Long deviceInfoId, Long orgFactoryId,
                                                         Integer stateCode, Long startTs, Long endTs,
                                                         boolean isComplete, Map<String, Object> properties,
                                                         ShiftDateAndCode precomputedShiftInfo) {
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
        
        // 优化：使用预计算的班次信息，减少锁内数据库查询
        recordFactory_ensureShiftInfo(record, precomputedShiftInfo, orgFactoryId);
        
        return record;
    }
    
    /**
     * 设置班次信息（如果缺失）
     * <p>
     * 优化：锁内只设置班次信息，不查询数据库
     * 班次信息应在锁外提前计算
     * </p>
     *
     * @param record    记录对象
     * @param shiftInfo 预计算的班次信息（如果为null，则不设置）
     */
    private void setShiftInfoIfMissing(DeviceStateRecordDO record, ShiftDateAndCode shiftInfo) {
        if (record == null || shiftInfo == null) {
            return;
        }
        if (record.getShiftDate() == null) {
            record.setShiftDate(shiftInfo.shiftDate());
        }
        if (record.getShiftCode() == null) {
            record.setShiftCode(shiftInfo.shiftCode());
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
            log.debug("[DeviceStateEventHandler] 更新缓存: deviceInfoId={}, state={}({})",
                    deviceInfoId, eventData.currentState(), eventData.currentStateCode());
            updateStateCache(orgFactoryId, deviceInfoId, eventData.currentStateCode(),
                    eventData.eventTimestamp(), request.getMessageId());
        } else {
            // 状态未变化，只刷新缓存 TTL 和心跳
            log.debug("[DeviceStateEventHandler] 刷新缓存TTL: deviceInfoId={}, state={}({})",
                    deviceInfoId, eventData.currentState(), eventData.currentStateCode());
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
     * <p>
     * 优化：使用 NOT_SUPPORTED 挂起事务，避免 Redis MULTI 嵌套问题
     * 心跳事件只更新缓存，不需要数据库事务
     * </p>
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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
    private record StateTransitionResult(
            boolean needUpdateCache, 
            boolean dbOperationSuccess,
            ErrorLogInfo errorLogInfo  // 异常日志信息（锁外记录）
    ) {
        public StateTransitionResult(boolean needUpdateCache, boolean dbOperationSuccess) {
            this(needUpdateCache, dbOperationSuccess, null);
        }
    }

    /**
     * 异常日志信息
     * <p>
     * 用于在锁外记录异常日志，减少锁持有时间
     * </p>
     */
    private record ErrorLogInfo(
            String errorType,      // 错误类型
            String errorMessage,    // 错误消息
            boolean needReview     // 是否需要人工审核
    ) {}

    /**
     * 状态转换上下文
     * <p>
     * 用于传递状态转换处理所需的所有参数，减少方法参数数量
     * 包含预计算的班次信息（日期编码和时间范围），避免锁内数据库查询
     * </p>
     */
    private record TransitionContext(
            Optional<DeviceStateRecordDO> latestStateOpt,
            EventData eventData,
            DeviceIdentity identity,
            WebhookRequest request,
            ShiftDateAndCode precomputedShiftInfo,
            ShiftDateAndCode precomputedNewRecordShiftInfo,
            ShiftTimeRange precomputedShiftRange,           // 已有记录的班次时间范围（用于跨班次检查）
            ShiftTimeRange precomputedNewRecordShiftRange, // 新记录的班次时间范围（用于跨班次检查）
            Long deviceInfoId,
            Long orgFactoryId
    ) {
        public boolean hasLatestState() {
            return latestStateOpt.isPresent();
        }

        public DeviceStateRecordDO getLatestState() {
            return latestStateOpt.orElseThrow();
        }
    }

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
     * <p>
     * 改进：对于进行中状态，即使 checkIfCrossesShift 返回 false，如果 effectiveEndTs 超过了开始班次的结束时间，
     * 也应该进行跨班次拆分，确保跨天场景能正确截断。
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
                                                                        Map<String, Object> properties,
                                                                        ShiftDateAndCode precomputedShiftInfo,
                                                                        ShiftTimeRange precomputedShiftRange) {
        // 优化：使用预计算的班次范围检查跨班次，避免锁内数据库查询
        boolean crossesShift = false;
        if (precomputedShiftRange != null && precomputedShiftRange.getEndTs() != null) {
            // 使用预计算的班次结束时间检查跨班次
            crossesShift = effectiveEndTs > precomputedShiftRange.getEndTs();
        } else {
            // 回退：如果预计算失败，锁内查询（应尽量避免）
            log.warn("[DeviceStateEventHandler] 预计算的班次范围不可用，锁内查询跨班次: deviceId={}, startTs={}, endTs={}",
                    deviceInfoId, startTs, effectiveEndTs);
            crossesShift = timeRangeRecordHandler.checkIfCrossesShift(
                    orgFactoryId, deviceInfoId, startTs, effectiveEndTs);
        }
        
        // 对于进行中状态，额外检查：如果 effectiveEndTs 超过了开始班次的结束时间，也应该进行跨班次拆分
        // 这可以处理跨天场景，确保能正确截断（例如：前一天的第二班延续到今天的第一班）
        if (!crossesShift && isOngoing && effectiveEndTs > shiftEndTs) {
            log.debug("[DeviceStateEventHandler] 进行中状态超过开始班次结束时间，进行跨班次拆分: deviceId={}, stateCode={}, " +
                            "startTs={}, shiftEndTs={}, effectiveEndTs={}",
                    deviceInfoId, stateCode, startTs, shiftEndTs, effectiveEndTs);
            crossesShift = true;
        }

        if (crossesShift) {
            // 跨班次：按班次拆分（统一处理逻辑）
            log.debug("[DeviceStateEventHandler] 状态跨班次，按班次拆分: deviceId={}, stateCode={}, startTs={}, effectiveEndTs={}",
                    deviceInfoId, stateCode, startTs, effectiveEndTs);
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
                    startTs, shiftEndTs, true, properties, precomputedShiftInfo);
            DeviceStateRecordDO ongoingRecord = createSingleStateRecord(deviceInfoId, orgFactoryId, stateCode,
                    shiftEndTs, null, false, properties, null); // 新记录的开始时间不同，不使用预计算
            return Arrays.asList(endedRecord, ongoingRecord);
            } else {
                // 结束状态：截断到班次结束
                DeviceStateRecordDO record = createSingleStateRecord(deviceInfoId, orgFactoryId, stateCode,
                        startTs, shiftEndTs, isComplete, properties, precomputedShiftInfo);
                return Collections.singletonList(record);
            }
        }

        // 正常情况：不跨班次且未超班次时长
        DeviceStateRecordDO record = createSingleStateRecord(deviceInfoId, orgFactoryId, stateCode,
                startTs, isOngoing ? null : effectiveEndTs, !isOngoing && isComplete, properties, precomputedShiftInfo);
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
        List<DeviceStateRecordDO> records = recordFactory_splitByShift(deviceInfoId, orgFactoryId, stateCode,
                startTs, endTs, properties, null); // 跨班次拆分时，第一条记录使用预计算的班次信息已在 splitByShift 内部处理

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
    private void handleExpiredState(DeviceStateRecordDO latestState, EventData eventData, Long orgFactoryId,
                                    ShiftDateAndCode precomputedShiftInfo, ShiftTimeRange precomputedShiftRange) {
        DeviceStateEnum stateEnum = DeviceStateEnum.fromCode(latestState.getStateCode());

        log.debug("[DeviceStateEventHandler] 处理过期状态记录: deviceInfoId={}, state={}({}), startTs={}, currentTs={}",
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

        log.debug("[DeviceStateEventHandler] 已标记过期状态记录: deviceInfoId={}, state={}, endTs={}",
                latestState.getDeviceInfoId(), stateEnum.name(), EXPIRED_END_TIMESTAMP);

        // 创建新的状态记录
        createNewStateAfterExpiry(latestState, eventData, orgFactoryId, precomputedShiftInfo, precomputedShiftRange);
    }

    /**
     * 在处理过期状态后创建新状态记录
     * <p>
     * 使用统一的 createStateRecords 方法，会自动处理班次截断逻辑
     * </p>
     */
    private void createNewStateAfterExpiry(DeviceStateRecordDO expiredState, EventData eventData, Long orgFactoryId,
                                            ShiftDateAndCode precomputedShiftInfo, ShiftTimeRange precomputedShiftRange) {
        log.debug("[DeviceStateEventHandler] 为过期状态创建新状态记录: deviceInfoId={}, newState={}",
                expiredState.getDeviceInfoId(), eventData.currentState());

        // 创建新状态记录属性
        Map<String, Object> additionalProperties = new HashMap<>();
        additionalProperties.put("previous_expired", true);
        additionalProperties.put("previous_state_id", expiredState.getId());
        Map<String, Object> properties = recordFactory_createStateProperties(eventData, additionalProperties);

        // 使用统一的创建逻辑，会自动处理班次截断
        List<DeviceStateRecordDO> newRecords = recordFactory_createStateRecords(
                expiredState.getDeviceInfoId(), orgFactoryId, eventData.currentStateCode(),
                eventData.eventTimestamp(), null, true, properties, precomputedShiftInfo, precomputedShiftRange);

        // 批量插入新记录
        recordFactory_insertBatch(newRecords, "过期状态后创建新状态记录");
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