package com.weili.iot_portal.service.ingestion.handler;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceIdentityCacheService;
import com.weili.iot_portal.service.cache.DeviceLockService;
import com.weili.iot_portal.service.cache.DeviceStateCacheService;
import com.weili.iot_portal.service.ingestion.WebhookFailLogService;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceStateEventFields;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 设备状态事件处理器
 * 处理 DeviceStateEvent，更新设备状态时间线记录
 * 支持的事件类型：DEVICE_STATE
 * 事件数据结构：
 * - previousState: 上一个设备状态（如：WORKING、STANDBY、FAULT等）
 * - currentState: 当前设备状态
 * - timestamp: 事件时间戳（秒）
 * - dataTimestamp: 数据时间戳（可选，设备实际时间）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceStateEventHandler implements WebhookEventHandler {


    private final DeviceStateRecordRepository stateTimelineRepository;
    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final WebhookFailLogService webhookFailLogService;
    private final DeviceLockService deviceLockService;
    private final DeviceStateCacheService deviceStateCacheService;

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

        // 3. 使用分布式锁保证同一设备的状态更新串行化
        if (!deviceLockService.tryLockState(deviceInfoId, DeviceStateEventFields.LOCK_TIMEOUT_SECONDS)) {
            log.warn("获取设备状态锁失败，可能正在并发处理: deviceInfoId={}", deviceInfoId);
            throw new IotPortalException(IotPortalErrorCode.EVENT_DEVICE_STATE_PROCESSING);
        }

        try {
            // 4. 查询数据库最新状态记录
            Optional<DeviceStateRecordDO> latestStateOpt = stateTimelineRepository
                    .findLatestState(deviceInfoId);

            // 5. 根据情况处理
            if (latestStateOpt.isEmpty()) {
                // 情况D：数据库无记录（首次记录）
                handleFirstRecord(deviceInfoId, orgFactoryId, currentState, eventTimestamp, previousState);
            } else if (StringUtils.isBlank(previousState)) {
                // 情况A：首次连接（previousState = NULL）
                handleFirstConnection(deviceInfoId, orgFactoryId, currentState, eventTimestamp);
            } else {
                // 情况B或C：正常匹配或状态不匹配
                DeviceStateRecordDO latestState = latestStateOpt.get();
                if (currentState.equalsIgnoreCase(latestState.getStateCode())) {
                    // 状态未变化，刷新缓存 TTL 和心跳，但不写入时间线
                    refreshStateCacheAndHeartbeat(orgFactoryId, deviceInfoId, request.getMessageId());
                    log.debug("状态未变化，刷新缓存TTL和心跳: deviceInfoId={}, state={}", deviceInfoId, currentState);
                    return;
                }

                if (previousState.equalsIgnoreCase(latestState.getStateCode())) {
                    // 情况B：正常匹配
                    handleNormalTransition(latestState, orgFactoryId, currentState, eventTimestamp);
                } else {
                    // 情况C：状态不匹配（异常情况）
                    handleStateMismatch(latestState, previousState, currentState, eventTimestamp,
                            deviceInfoId, orgFactoryId, request);
                }
            }

            // 写入实时状态缓存（覆盖写，供前端轮询）
            deviceStateCacheService.saveState(orgFactoryId, deviceInfoId, currentState,
                    eventTimestamp, DeviceStateEventFields.SOURCE_TB, request.getMessageId());
            deviceStateCacheService.saveHeartbeat(orgFactoryId, deviceInfoId, request.getMessageId());
        } finally {
            // 释放锁
            deviceLockService.unlockState(deviceInfoId);
        }
    }

    /**
     * 状态未变化时，刷新状态缓存 TTL（不改值）并刷新心跳
     */
    private void refreshStateCacheAndHeartbeat(String factoryId, String deviceId,
                                               String traceId) {
        // 仅刷新 TTL，保持原值（避免 updatedAt 误更新）
        deviceStateCacheService.refreshStateTtl(factoryId, deviceId);
        deviceStateCacheService.saveHeartbeat(factoryId, deviceId, traceId);
    }

    /**
     * 情况A：首次连接（previousState = NULL）
     */
    private void handleFirstConnection(String deviceInfoId, String orgFactoryId,
                                       String currentState, Long eventTimestamp) {
        log.info("设备首次连接，插入新状态记录: deviceInfoId={}, state={}, timestamp={}",
                deviceInfoId, currentState, eventTimestamp);

        DeviceStateRecordDO newRecord = createStateRecord(deviceInfoId, orgFactoryId, currentState,
                eventTimestamp, null, true, null);
        stateTimelineRepository.insert(newRecord);
    }

    /**
     * 情况B：正常匹配（DB最新状态 = previousState）
     */
    private void handleNormalTransition(DeviceStateRecordDO latestState, String orgFactoryId,
                                       String currentState, Long eventTimestamp) {
        log.debug("正常状态转换: deviceInfoId={}, {} -> {}, timestamp={}",
                latestState.getDeviceInfoId(), latestState.getStateCode(), currentState, eventTimestamp);

        // 更新旧状态记录
        latestState.setEndTs(eventTimestamp);
        if (latestState.getStartTs() != null) {
            latestState.setDurationS((int) (eventTimestamp - latestState.getStartTs()));
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
        String errorMessage = String.format("状态不匹配（已结束）: DB状态=%s, 事件previousState=%s, 间隙=%d秒",
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
            latestState.setDurationS((int) (eventTimestamp - latestState.getStartTs()));
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
        log.error("时间戳异常: deviceInfoId={}, 事件时间={}, DB状态开始时间={}, 差距={}秒",
                deviceInfoId, eventTimestamp, latestState.getStartTs(),
                latestState.getStartTs() - eventTimestamp);

        // 使用数据库当前时间作为 start_ts（避免时间倒流）
        long currentTimeSeconds = System.currentTimeMillis() / DeviceStateEventFields.MILLIS_TO_SECONDS;

        Map<String, Object> anomalyProperties = new HashMap<>();
        anomalyProperties.put(DeviceStateEventFields.TIMESTAMP_ANOMALY, true);
        anomalyProperties.put(DeviceStateEventFields.EVENT_TIMESTAMP, eventTimestamp);
        anomalyProperties.put(DeviceStateEventFields.DB_TIMESTAMP, currentTimeSeconds);
        anomalyProperties.put(DeviceStateEventFields.DB_START_TS, latestState.getStartTs());

        DeviceStateRecordDO newRecord = createStateRecord(deviceInfoId, orgFactoryId, currentState,
                currentTimeSeconds, null, false, anomalyProperties);
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
            log.warn("数据库无记录但previousState不为NULL: deviceInfoId={}, previousState={}",
                    deviceInfoId, previousState);
        }

        log.info("设备首次记录，插入新状态记录: deviceInfoId={}, state={}, timestamp={}",
                deviceInfoId, currentState, eventTimestamp);

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
            record.setDurationS((int) (endTs - startTs));
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

        // 解析设备身份
        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getDeviceCode(),
                        request.getDeviceId(), DeviceStateEventFields.EVENT_SOURCE_HEARTBEAT);
        String deviceInfoId = identity.getDeviceId();
        String orgFactoryId = identity.getFactoryId();

        // 事件时间戳
        long ts = request.getDataTimestamp() != null
                ? request.getDataTimestamp()
                : (request.getTimestamp() != null ? request.getTimestamp()
                : System.currentTimeMillis() / DeviceStateEventFields.MILLIS_TO_SECONDS);

        // 覆盖写实时状态 + 续租 TTL
        deviceStateCacheService.saveState(orgFactoryId, deviceInfoId, currentState,
                ts, DeviceStateEventFields.SOURCE_TB, request.getMessageId());

        // 刷新心跳（短 TTL）
        deviceStateCacheService.saveHeartbeat(orgFactoryId, deviceInfoId, request.getMessageId());
    }
}

