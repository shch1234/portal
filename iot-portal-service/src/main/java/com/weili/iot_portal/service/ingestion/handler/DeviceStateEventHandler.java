package com.weili.iot_portal.service.ingestion.handler;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceIdentityCacheService;
import com.weili.iot_portal.service.cache.DeviceLockService;
import com.weili.iot_portal.service.cache.DeviceStateCacheService;
import com.weili.iot_portal.service.ingestion.WebhookFailLogService;
import com.weili.iot_portal.service.ingestion.support.WebhookInboxService;
import com.weili.iot_portal.service.ingestion.support.DistributedLockService;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceStateEventFields;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 设备状态事件处理器
 * 处理 DeviceStateEvent，更新设备状态时间线记录
 * 支持的事件类型：DEVICE_STATE
 * 事件数据结构：
 * - previousState: 上一个设备状态（如：WORKING、STANDBY、FAULT等）
 * - currentState: 当前设备状态
 * - timestamp: 事件时间戳（毫秒）
 * - dataTimestamp: 数据时间戳（可选，设备实际时间，毫秒）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceStateEventHandler implements WebhookEventHandler {


    private final DeviceStateRecordRepository stateTimelineRepository;
    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final WebhookFailLogService webhookFailLogService;
    private final WebhookInboxService inboxService;
    private final DistributedLockService distributedLockService;
    private final RealTimeCacheService realTimeCacheService;

    @Value("${rt.state.ttl-millis:600000}")
    private long stateTtlMillis;

    @Value("${rt.state.heartbeat-ttl-seconds:300}")
    private long stateHeartbeatTtlSeconds;

    @Override
    public boolean supports(String eventType) {
        return DeviceStateEventFields.EVENT_TYPE.equals(eventType)
                || DeviceStateEventFields.EVENT_TYPE_HEARTBEAT.equals(eventType);
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.DEVICE_STATE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        log.info("[Webhook-Handler-DeviceState] 处理设备状态事件: messageId={}, eventType={}, deviceCode={}", 
            request.getMessageId(), request.getEventType(), request.getDeviceCode());
        
        if (DeviceStateEventFields.EVENT_TYPE_HEARTBEAT.equals(request.getEventType())) {
            handleHeartbeat(request);
            return;
        }
        // 1. 解析事件数据
        Map<String, Object> eventData = request.getEventData();
        if (eventData == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_DATA_EMPTY);
        }

        String previousState = getStringValue(eventData, DeviceStateEventFields.PREVIOUS_STATE);
        String currentState = getStringValue(eventData, DeviceStateEventFields.CURRENT_STATE);
        if (StringUtils.isBlank(currentState)) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_CURRENT_STATE_EMPTY);
        }
        
        // 统一转换为大写，确保状态值一致性
        if (previousState != null) {
            previousState = previousState.toUpperCase();
        }
        currentState = currentState.toUpperCase();

        // 优先使用 dataTimestamp，否则使用 timestamp
        Long eventTimestamp = request.getDataTimestamp() != null
                ? request.getDataTimestamp()
                : request.getTimestamp();
        if (eventTimestamp == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_TIMESTAMP_EMPTY);
        }

        // 2. 解析设备信息
        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getDeviceCode(),
                        request.getDeviceId(), DeviceStateEventFields.EVENT_SOURCE);
        String deviceInfoId = identity.getDeviceId();
        String orgFactoryId = identity.getFactoryId();

        // 3. 使用分布式锁保证同一设备的状态更新串行化（事务外）
        String lockKey = DeviceStateEventFields.LOCK_KEY_PREFIX + deviceInfoId;
        Boolean lockAcquired = distributedLockService.tryLock(lockKey, DeviceStateEventFields.LOCK_TIMEOUT_SECONDS);
        
        if (!Boolean.TRUE.equals(lockAcquired)) {
            log.warn("[Webhook-Handler-DeviceState] 获取设备状态锁失败: deviceInfoId={}, messageId={}", 
                deviceInfoId, request.getMessageId());
            throw new IotPortalException(IotPortalErrorCode.EVENT_DEVICE_STATE_PROCESSING);
        }

        // 标记是否需要更新缓存（在事务外使用）
        boolean needUpdateCache = false;
        boolean dbOperationSuccess = false;
        
        try {
            // 4. 查询数据库最新状态记录（事务内）
            Optional<DeviceStateRecordDO> latestStateOpt = stateTimelineRepository
                    .findLatestState(deviceInfoId);

            // 5. 根据情况处理数据库操作（事务内）
            if (latestStateOpt.isEmpty()) {
                // 情况D：数据库无记录（首次记录）
                handleFirstRecord(deviceInfoId, orgFactoryId, currentState, eventTimestamp, previousState);
                needUpdateCache = true;
                dbOperationSuccess = true;
            } else if (StringUtils.isBlank(previousState)) {
                // 情况A：首次连接（previousState = NULL）
                handleFirstConnection(deviceInfoId, orgFactoryId, currentState, eventTimestamp);
                needUpdateCache = true;
                dbOperationSuccess = true;
            } else {
                // 情况B或C：正常匹配或状态不匹配
                DeviceStateRecordDO latestState = latestStateOpt.get();
                if (currentState.equalsIgnoreCase(latestState.getStateCode())) {
                    // 状态未变化，只刷新缓存 TTL 和心跳，不写入时间线（无数据库写操作）
                    // 标记需要刷新缓存，在事务外执行
                    needUpdateCache = false; // 不需要更新状态，只需要刷新 TTL
                    dbOperationSuccess = true; // 查询成功，没有写操作
                    // 注意：这里不 return，让代码继续执行到 finally 后刷新缓存
                } else {
                    if (previousState.equalsIgnoreCase(latestState.getStateCode())) {
                        // 情况B：正常匹配
                        handleNormalTransition(latestState, orgFactoryId, currentState, eventTimestamp);
                        needUpdateCache = true;
                    } else {
                        // 情况C：状态不匹配（异常情况）
                        log.warn("[Webhook-Handler-DeviceState] 状态不匹配: DB状态={}, 事件previousState={}, 事件currentState={}, deviceInfoId={}", 
                            latestState.getStateCode(), previousState, currentState, deviceInfoId);
                        handleStateMismatch(latestState, previousState, currentState, eventTimestamp, 
                            deviceInfoId, orgFactoryId, request);
                        needUpdateCache = true;
                    }
                    // 数据库写操作成功完成（如果没有抛出异常，说明事务会提交）
                    dbOperationSuccess = true;
                }
            }
        } finally {
            // 释放锁（事务外）
            distributedLockService.releaseLock(lockKey);
        }
        
        // Redis 缓存操作在事务外执行，确保只有数据库操作成功后才更新缓存
        // 如果数据库操作失败（抛出异常），dbOperationSuccess 为 false，不会更新缓存
        if (dbOperationSuccess) {
            if (needUpdateCache) {
                // 更新状态缓存（数据库操作成功）
                updateStateCache(orgFactoryId, deviceInfoId, currentState, eventTimestamp, request.getMessageId());
            } else {
                // 状态未变化，只刷新缓存 TTL 和心跳（无数据库操作）
                refreshStateCacheAndHeartbeat(orgFactoryId, deviceInfoId, eventTimestamp, request.getMessageId());
            }
        }
    }

    /**
     * 更新实时状态缓存（事务外执行）
     * 只有数据库操作成功后才更新缓存，避免事务回滚时缓存不一致
     */
    private void updateStateCache(String factoryId, String deviceId, String currentState, 
                                  Long eventTimestamp, String traceId) {
        Map<String, String> payload = new HashMap<>();
        payload.put(DeviceStateEventFields.STATE, currentState);
        payload.put(DeviceStateEventFields.UPDATED_AT, String.valueOf(eventTimestamp));
        payload.put(DeviceStateEventFields.SOURCE, DeviceStateEventFields.SOURCE_TB);
        if (StringUtils.isNotBlank(traceId)) {
            payload.put(DeviceStateEventFields.TRACE_ID, traceId);
        }
        String stateKey = formatStateKey(factoryId, deviceId);
        realTimeCacheService.hsetWithTtl(stateKey, payload, stateTtlMillis);
        refreshHeartbeat(factoryId, deviceId, traceId);
    }

    /**
     * 状态未变化时，刷新状态缓存 TTL（不改值）并刷新心跳（事务外执行）
     */
    private void refreshStateCacheAndHeartbeat(String factoryId, String deviceId,
                                               String traceId) {
        // 仅刷新 TTL，保持原值（避免 updatedAt 误更新）
        // 通过重新设置 hash 来刷新 TTL（保持原值不变）
        Map<String, String> currentState = realTimeCacheService.getHash(stateKey);
        if (currentState != null && !currentState.isEmpty()) {
            realTimeCacheService.hsetWithTtl(stateKey, currentState, stateTtlMillis);
        }
        refreshHeartbeat(factoryId, deviceId, traceId);
    }

    /**
     * 写入/刷新状态心跳 key（短 TTL），用于实时性判断，避免数据过期后取不到
     */
    private void refreshHeartbeat(String factoryId, String deviceId, String traceId) {
        String hbKey = formatStateHeartbeatKey(factoryId, deviceId);
        String value = StringUtils.defaultIfBlank(traceId, DeviceStateEventFields.DEFAULT_HEARTBEAT_VALUE);
        realTimeCacheService.setWithTtlSeconds(hbKey, value, stateHeartbeatTtlSeconds);
    }

    /**
     * 情况A：首次连接（previousState = NULL）
     */
    private void handleFirstConnection(String deviceInfoId, String orgFactoryId,
                                       String currentState, Long eventTimestamp) {
        DeviceStateRecordDO newRecord = createStateRecord(deviceInfoId, orgFactoryId, currentState,
                eventTimestamp, null, true, null);
        stateTimelineRepository.insert(newRecord);
    }

    /**
     * 情况B：正常匹配（DB最新状态 = previousState）
     */
    private void handleNormalTransition(DeviceStateRecordDO latestState, String orgFactoryId,
                                       String currentState, Long eventTimestamp) {
        // 更新旧状态记录
        latestState.setEndTs(eventTimestamp);
        if (latestState.getStartTs() != null) {
            latestState.setDurationS(eventTimestamp - latestState.getStartTs());
        }
        latestState.setIsComplete(true);
        stateTimelineRepository.update(latestState);

        // 插入新状态记录
        DeviceStateRecordDO newRecord = createStateRecord(
                latestState.getDeviceInfoId(), orgFactoryId, currentState, eventTimestamp, null, true, null);
        stateTimelineRepository.insert(newRecord);
    }

    /**
     * 情况C：状态不匹配（异常情况）
     */
    private void handleStateMismatch(DeviceStateRecordDO latestState, String previousState,
                                     String currentState, Long eventTimestamp,
                                     String deviceInfoId, String orgFactoryId, WebhookRequest request) {
        log.warn("状态不匹配异常: deviceInfoId={}, DB状态={}, 事件previousState={}, 事件currentState={}, timestamp={}",
                deviceInfoId, latestState.getStateCode(), previousState, currentState, eventTimestamp);

        boolean isOngoing = latestState.getEndTs() == null;
        if (isOngoing) {
            // 子情况C2：数据库状态进行中（end_ts IS NULL）
            handleOngoingStateMismatch(latestState, previousState, currentState, eventTimestamp,
                    deviceInfoId, orgFactoryId, request);
        } else {
            // 子情况C1：数据库状态已结束（end_ts IS NOT NULL）
            handleEndedStateMismatch(latestState, previousState, currentState, eventTimestamp,
                    deviceInfoId, orgFactoryId, request);
        }
    }

    /**
     * 子情况C1：数据库状态已结束（end_ts IS NOT NULL）
     */
    private void handleEndedStateMismatch(DeviceStateRecordDO latestState, String previousState,
                                         String currentState, Long eventTimestamp,
                                         String deviceInfoId, String orgFactoryId, WebhookRequest request) {
        Long latestEndTs = latestState.getEndTs();

        // 检查是否存在状态间隙
        if (latestEndTs < eventTimestamp) {
            // 存在间隙，插入 UNKNOWN 状态记录填充间隙
            log.warn("检测到状态间隙，插入UNKNOWN状态: deviceInfoId={}, gap=[{} -> {}]",
                    deviceInfoId, latestEndTs, eventTimestamp);

            Map<String, Object> gapProperties = new HashMap<>();
            gapProperties.put(DeviceStateEventFields.GAP_REASON, "状态不匹配导致间隙");
            gapProperties.put(DeviceStateEventFields.EXPECTED_PREVIOUS, previousState);
            gapProperties.put(DeviceStateEventFields.ACTUAL_DB_STATE, latestState.getStateCode());

            DeviceStateRecordDO unknownRecord = createStateRecord(deviceInfoId, orgFactoryId, DeviceStateEventFields.UNKNOWN_STATE,
                    latestEndTs, eventTimestamp, false, gapProperties);
            stateTimelineRepository.insert(unknownRecord);
        }

        // 插入新状态记录
        DeviceStateRecordDO newRecord = createStateRecord(deviceInfoId, orgFactoryId, currentState,
                eventTimestamp, null, true, null);
        stateTimelineRepository.insert(newRecord);

        // 记录异常日志（可以自动修复，不需要人工处理）
        String errorMessage = String.format("状态不匹配（已结束）: DB状态=%s, 事件previousState=%s, 间隙=%d毫秒",
                latestState.getStateCode(), previousState, eventTimestamp - latestEndTs);
        webhookFailLogService.saveFailLog(request, DeviceStateEventFields.ERROR_TYPE_STATE_MISMATCH, errorMessage, false);
    }

    /**
     * 子情况C2：数据库状态进行中（end_ts IS NULL）
     */
    private void handleOngoingStateMismatch(DeviceStateRecordDO latestState, String previousState,
                                           String currentState, Long eventTimestamp,
                                           String deviceInfoId, String orgFactoryId, WebhookRequest request) {
        // 检查时间戳异常
        if (latestState.getStartTs() != null && eventTimestamp < latestState.getStartTs()) {
            // 子情况C3：时间戳异常
            handleTimestampAnomaly(latestState, currentState, eventTimestamp, deviceInfoId, orgFactoryId, request);
            return;
        }

        // 将数据库中的进行中状态标记为 UNKNOWN

        Map<String, Object> mismatchProperties = new HashMap<>();
        mismatchProperties.put(DeviceStateEventFields.MISMATCH_REASON, "previousState不匹配");
        mismatchProperties.put(DeviceStateEventFields.EXPECTED, latestState.getStateCode());
        mismatchProperties.put(DeviceStateEventFields.ACTUAL_DB, latestState.getStateCode());
        mismatchProperties.put(DeviceStateEventFields.EVENT_PREVIOUS, previousState);

        latestState.setStateCode(DeviceStateEventFields.UNKNOWN_STATE);
        latestState.setEndTs(eventTimestamp);
        if (latestState.getStartTs() != null) {
            latestState.setDurationS(eventTimestamp - latestState.getStartTs());
        }
        latestState.setIsComplete(false); // 标记为不完整，需要后续修复
        latestState.setProperties(mismatchProperties);
        stateTimelineRepository.update(latestState);

        // 如果 previousState 不为 NULL，插入 previousState 状态记录（用于修复时间线）
        if (StringUtils.isNotBlank(previousState)) {
            Map<String, Object> recoveryProperties = new HashMap<>();
            recoveryProperties.put(DeviceStateEventFields.RECOVERY, true);
            recoveryProperties.put(DeviceStateEventFields.RECOVERED_FROM, DeviceStateEventFields.UNKNOWN_STATE);

            DeviceStateRecordDO previousRecord = createStateRecord(deviceInfoId, orgFactoryId, previousState,
                    eventTimestamp, eventTimestamp, false, recoveryProperties);
            stateTimelineRepository.insert(previousRecord);
        }

        // 插入新状态记录
        DeviceStateRecordDO newRecord = createStateRecord(deviceInfoId, orgFactoryId, currentState,
                eventTimestamp, null, true, null);
        stateTimelineRepository.insert(newRecord);

        // 记录异常日志（需要人工审核）
        String errorMessage = String.format("状态不匹配（进行中）: DB状态=%s, 事件previousState=%s, 已标记为UNKNOWN",
                latestState.getStateCode(), previousState);
        webhookFailLogService.saveFailLog(request, DeviceStateEventFields.ERROR_TYPE_STATE_MISMATCH, errorMessage, true);
    }

    /**
     * 子情况C3：时间戳异常（事件时间 < 数据库状态开始时间）
     */
    private void handleTimestampAnomaly(DeviceStateRecordDO latestState, String currentState,
                                        Long eventTimestamp, String deviceInfoId, String orgFactoryId,
                                        WebhookRequest request) {
        log.warn("时间戳异常: deviceInfoId={}, 事件时间={}, DB状态开始时间={}, 差距={}毫秒",
                deviceInfoId, eventTimestamp, latestState.getStartTs(),
                latestState.getStartTs() - eventTimestamp);

        // 使用数据库当前时间作为 start_ts（避免时间倒流）
        long currentTimeMillis = System.currentTimeMillis();

        Map<String, Object> anomalyProperties = new HashMap<>();
        anomalyProperties.put(DeviceStateEventFields.TIMESTAMP_ANOMALY, true);
        anomalyProperties.put(DeviceStateEventFields.EVENT_TIMESTAMP, eventTimestamp);
        anomalyProperties.put(DeviceStateEventFields.DB_TIMESTAMP, currentTimeMillis);
        anomalyProperties.put(DeviceStateEventFields.DB_START_TS, latestState.getStartTs());

        DeviceStateRecordDO newRecord = createStateRecord(deviceInfoId, orgFactoryId, currentState,
                currentTimeMillis, null, false, anomalyProperties);
        stateTimelineRepository.insert(newRecord);

        // 记录异常日志（需要人工审核）
        String errorMessage = String.format("时间戳异常: 事件时间=%d, DB状态开始时间=%d, 差距=%d秒",
                eventTimestamp, latestState.getStartTs(), latestState.getStartTs() - eventTimestamp);
        webhookFailLogService.saveFailLog(request, DeviceStateEventFields.ERROR_TYPE_TIMESTAMP_ANOMALY, errorMessage, true);
    }

    /**
     * 情况D：数据库无记录（首次记录）
     */
    private void handleFirstRecord(String deviceInfoId, String orgFactoryId,
                                   String currentState, Long eventTimestamp, String previousState) {
        if (StringUtils.isNotBlank(previousState)) {
            log.warn("[Webhook-Handler-DeviceState] 数据库无记录但previousState不为NULL: deviceInfoId={}, previousState={}",
                    deviceInfoId, previousState);
        }

        DeviceStateRecordDO newRecord = createStateRecord(deviceInfoId, orgFactoryId, currentState,
                eventTimestamp, null, true, null);
        stateTimelineRepository.insert(newRecord);
    }

    /**
     * 创建状态记录
     */
    private DeviceStateRecordDO createStateRecord(String deviceInfoId, String orgFactoryId,
                                                  String stateCode, Long startTs, Long endTs, boolean isComplete,
                                                  Map<String, Object> properties) {
        DeviceStateRecordDO record = new DeviceStateRecordDO();
        record.setId(IdWorker.getIdStr());
        record.setDeviceInfoId(deviceInfoId);
        record.setOrgFactoryId(orgFactoryId);
        record.setStateCode(stateCode);
        record.setStartTs(startTs);
        record.setEndTs(endTs);
        if (endTs != null && startTs != null) {
            record.setDurationS(endTs - startTs);
        }
        record.setIsComplete(isComplete);
        record.setProperties(properties);
        return record;
    }

    /**
     * 从 Map 中获取字符串值
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private String defaultBlank(String value) {
        return StringUtils.defaultIfBlank(value, DeviceStateEventFields.DEFAULT_BLANK_PLACEHOLDER);
    }

    private String formatStateKey(String factoryId, String deviceId) {
        return String.format(RedisConstant.RT_STATE,
                "none", defaultBlank(factoryId), defaultBlank(deviceId));
    }

    private String formatStateHeartbeatKey(String factoryId, String deviceId) {
        return String.format(RedisConstant.RT_STATE_HEARTBEAT,
                "none", defaultBlank(factoryId), defaultBlank(deviceId));
    }

    /**
     * 设备状态心跳事件：不写时间线，仅续租实时缓存 TTL 并更新心跳
     */
    private void handleHeartbeat(WebhookRequest request) {
        Map<String, Object> eventData = request.getEventData();
        if (eventData == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_DATA_EMPTY);
        }
        String currentState = getStringValue(eventData, DeviceStateEventFields.CURRENT_STATE);
        if (StringUtils.isBlank(currentState)) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_CURRENT_STATE_EMPTY);
        }
        
        // 统一转换为大写，确保状态值一致性
        currentState = currentState.toUpperCase();

        // 解析设备身份
        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getDeviceCode(),
                        request.getDeviceId(), DeviceStateEventFields.EVENT_SOURCE_HEARTBEAT);
        String deviceInfoId = identity.getDeviceId();
        String orgFactoryId = identity.getFactoryId();

        // 事件时间戳（毫秒级）
        long ts = request.getDataTimestamp() != null
                ? request.getDataTimestamp()
                : (request.getTimestamp() != null ? request.getTimestamp()
                : System.currentTimeMillis());

        // 覆盖写实时状态 + 续租 TTL
        deviceStateCacheService.saveState(orgFactoryId, deviceInfoId, currentState,
                ts, DeviceStateEventFields.SOURCE_TB, request.getMessageId());

        // 刷新心跳（短 TTL）
        deviceStateCacheService.saveHeartbeat(orgFactoryId, deviceInfoId, request.getMessageId());
    }

    /**
     * 在事务外获取分布式锁（避免事务导致 setIfAbsent 返回 null）
     * 
     * @param lockKey 锁的键
     * @param deviceInfoId 设备ID
     * @param request Webhook请求
     * @param currentState 当前状态
     * @param previousState 之前状态
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    private void acquireLock(String lockKey, String deviceInfoId, WebhookRequest request, 
                             String currentState, String previousState) {
        // 获取锁之前，记录详细信息（包括时间戳，用于诊断相同时间戳的并发问题）
        log.debug("[Webhook-Handler-DeviceState] 尝试获取设备状态锁: lockKey={}, deviceInfoId={}, deviceCode={}, messageId={}, currentState={}, previousState={}, timestamp={}, dataTimestamp={}", 
            lockKey, deviceInfoId, request.getDeviceCode(), request.getMessageId(), currentState, previousState, 
            request.getTimestamp(), request.getDataTimestamp());
        
        // 检查锁的当前状态（用于诊断）
        Boolean isLocked = distributedLockService.isLocked(lockKey);
        Long lockTtl = distributedLockService.getLockTtl(lockKey);
        String currentLockValue = Boolean.TRUE.equals(isLocked) ? "1" : null;
        if (currentLockValue != null) {
            log.warn("[Webhook-Handler-DeviceState] 锁已被占用: lockKey={}, currentValue={}, ttl={}秒, deviceInfoId={}, messageId={}", 
                lockKey, currentLockValue, lockTtl, deviceInfoId, request.getMessageId());
            
            // 查询是否有同一设备的其他消息正在处理中（用于诊断，排除当前消息）
            try {
                List<WebhookInboxDO> processingMessages = inboxService.findProcessingByDevice(request.getDeviceCode(), request.getMessageId());
                if (!processingMessages.isEmpty()) {
                    log.warn("[Webhook-Handler-DeviceState] 发现同一设备有其他消息正在处理: deviceCode={}, processingCount={}, messageIds={}, currentMessageId={}", 
                        request.getDeviceCode(), processingMessages.size(), 
                        processingMessages.stream().map(WebhookInboxDO::getMessageId).collect(java.util.stream.Collectors.toList()),
                        request.getMessageId());
                }
            } catch (Exception e) {
                log.debug("[Webhook-Handler-DeviceState] 查询正在处理的消息失败（不影响主流程）: {}", e.getMessage());
            }
        }
        
        // 尝试获取锁，记录操作前后的时间戳用于诊断
        // 使用独立的 DistributedLockService，通过 Spring 代理调用，确保在非事务模式下执行
        long beforeLock = System.currentTimeMillis();
        Boolean lockAcquired = distributedLockService.tryLock(lockKey, LOCK_TIMEOUT_SECONDS);
        long afterLock = System.currentTimeMillis();
        log.debug("[Webhook-Handler-DeviceState] 锁获取操作: lockKey={}, result={}, 耗时={}ms", 
            lockKey, lockAcquired, afterLock - beforeLock);
        
        // 如果返回 null 或 false，都视为获取锁失败
        // null 可能是 Redis 连接问题或异常，应该当作失败处理
        if (!Boolean.TRUE.equals(lockAcquired)) {
            if (lockAcquired == null) {
                log.error("[Webhook-Handler-DeviceState] 锁获取返回null（可能是Redis连接问题）: lockKey={}, deviceInfoId={}, messageId={}", 
                    lockKey, deviceInfoId, request.getMessageId());
            } else {
                // lockAcquired == false，说明锁被占用
                log.debug("[Webhook-Handler-DeviceState] 锁被占用，等待检查: lockKey={}, deviceInfoId={}, messageId={}", 
                    lockKey, deviceInfoId, request.getMessageId());
            }
            
            // 获取锁失败后，再次检查锁的当前状态（用于诊断）
            // 注意：这里可能存在竞态条件，锁可能在 setIfAbsent 和检查之间被释放
            Boolean actualIsLocked = distributedLockService.isLocked(lockKey);
            Long actualLockTtl = distributedLockService.getLockTtl(lockKey);
            String actualLockValue = Boolean.TRUE.equals(actualIsLocked) ? "1" : null;
            
            if (Boolean.TRUE.equals(lockAcquired) == false && !Boolean.TRUE.equals(actualIsLocked)) {
                // setIfAbsent 返回 false，但检查时锁不存在，说明锁在获取和检查之间被释放了
                log.warn("[Webhook-Handler-DeviceState] ⚠️ 竞态条件：setIfAbsent返回false但检查时锁不存在（可能在获取和检查之间被释放）: lockKey={}, deviceInfoId={}, deviceCode={}, messageId={}", 
                    lockKey, deviceInfoId, request.getDeviceCode(), request.getMessageId());
            }
            
            log.warn("[Webhook-Handler-DeviceState] 获取设备状态锁失败: lockKey={}, deviceInfoId={}, deviceCode={}, messageId={}, currentState={}, previousState={}, setIfAbsent结果={}, 检查时lockValue={}, 检查时lockTtl={}秒, 再次检查lockValue={}, 再次检查lockTtl={}秒", 
                lockKey, deviceInfoId, request.getDeviceCode(), request.getMessageId(), currentState, previousState, 
                lockAcquired, currentLockValue, lockTtl, actualLockValue, actualLockTtl);
            
            // 查询是否有同一设备的其他消息正在处理中（用于诊断，排除当前消息）
            try {
                List<WebhookInboxDO> processingMessages = inboxService.findProcessingByDevice(request.getDeviceCode(), request.getMessageId());
                if (!processingMessages.isEmpty()) {
                    log.warn("[Webhook-Handler-DeviceState] 发现同一设备有其他消息正在处理: deviceCode={}, processingCount={}, messageIds={}, statuses={}, currentMessageId={}", 
                        request.getDeviceCode(), processingMessages.size(), 
                        processingMessages.stream().map(WebhookInboxDO::getMessageId).collect(java.util.stream.Collectors.toList()),
                        processingMessages.stream().map(WebhookInboxDO::getStatus).collect(java.util.stream.Collectors.toList()),
                        request.getMessageId());
                } else {
                    log.warn("[Webhook-Handler-DeviceState] 锁获取失败但未发现其他正在处理的消息，可能是其他进程/实例持有锁或Redis问题: deviceCode={}, lockKey={}, currentMessageId={}, setIfAbsent返回值={}", 
                        request.getDeviceCode(), lockKey, request.getMessageId(), lockAcquired);
                }
                
                // 检查是否有相同 dataTimestamp 的消息（用于诊断相同时间戳导致的并发问题）
                if (request.getDataTimestamp() != null) {
                    List<WebhookInboxDO> sameTimestampMessages = inboxService.findPendingByDeviceAndTimestamp(
                        request.getDeviceCode(), request.getDataTimestamp(), request.getMessageId());
                    if (!sameTimestampMessages.isEmpty()) {
                        log.warn("[Webhook-Handler-DeviceState] ⚠️ 发现相同dataTimestamp的消息（可能是并发问题的根源）: deviceCode={}, dataTimestamp={}, sameTimestampCount={}, messageIds={}, statuses={}, currentMessageId={}", 
                            request.getDeviceCode(), request.getDataTimestamp(), sameTimestampMessages.size(),
                            sameTimestampMessages.stream().map(WebhookInboxDO::getMessageId).collect(java.util.stream.Collectors.toList()),
                            sameTimestampMessages.stream().map(WebhookInboxDO::getStatus).collect(java.util.stream.Collectors.toList()),
                            request.getMessageId());
                    }
                }
            } catch (Exception e) {
                log.debug("[Webhook-Handler-DeviceState] 查询正在处理的消息失败（不影响主流程）: {}", e.getMessage());
            }
            
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备状态正在处理中，请稍后重试");
        }
    }
}

