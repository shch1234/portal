package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.common.utils.WebhookTimestampUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceProductionRecordDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceProductionRecordRepository;
import com.weili.iot_portal.domain.ingestion.DeviceIdentity;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceLockService;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.WebhookFailLogService;
import com.weili.iot_portal.service.ingestion.WebhookProcessingStrategy;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceWorkingStateEventFields;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * 设备加工状态事件处理器
 * <p>
 * 处理设备加工状态变化事件（开始/结束）。
 * 加工状态：开始为1，结束为0
 * </p>
 * <p>
 * 处理流程：
 * 1. 解析事件数据（状态、时间戳等）
 * 2. 获取分布式锁
 * 3. 查询数据库最新记录
 * 4. 根据状态变化处理（从0到1：开始生产新产品，从1到0：完成产品）
 * 5. 处理异常情况（状态不匹配、时间戳异常等）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceWorkingStateEventHandler implements WebhookEventHandler {

    private final WebhookHandlerUtils webhookHandlerUtils;
    private final DeviceProductionRecordRepository deviceProductionRecordRepository;
    private final DeviceLockService deviceLockService;
    private final WebhookFailLogService webhookFailLogService;
    private final RecordHandlerUtils recordHandlerUtils;

    @Override
    public boolean supports(String eventType) {
        return DeviceWorkingStateEventFields.EVENT_TYPE.equals(eventType);
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.DEVICE_WORKING_STATE;
    }

    @Override
    public WebhookProcessingStrategy getProcessingStrategy() {
        // 业务持久化处理：需要写数据库，经过收件箱，支持重试
        return WebhookProcessingStrategy.BUSINESS_PERSISTENT;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        // 1. 解析事件数据
        EventData eventData = parseEventData(request);
        
        // 2. 解析设备信息
        DeviceIdentity identity = webhookHandlerUtils.resolveDeviceIdentity(request);
        
        // 3. 使用分布式锁处理加工状态变化
        processWorkingStateChangeWithLock(eventData, identity, request);
    }

    // ==================== 数据解析 ====================

    /**
     * 解析事件数据
     */
    private EventData parseEventData(WebhookRequest request) {
        Map<String, Object> eventDataMap = request.getEventData();
        if (eventDataMap == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_DATA_EMPTY);
        }

        // 提取状态值（应该是数字0或1）
        Object previousStatusObj = eventDataMap.get(DeviceWorkingStateEventFields.PREVIOUS_STATUS);
        Object currentStatusObj = eventDataMap.get(DeviceWorkingStateEventFields.CURRENT_STATUS);
        
        if (currentStatusObj == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_CURRENT_STATE_EMPTY);
        }

        // 提取并验证状态编码
        Integer previousStatus = extractWorkingStatus(previousStatusObj);
        Integer currentStatus = extractWorkingStatus(currentStatusObj);
        
        if (currentStatus == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_CURRENT_STATE_EMPTY);
        }

        // 验证状态值（只接受0或1）
        if (currentStatus != DeviceWorkingStateEventFields.STATUS_START 
                && currentStatus != DeviceWorkingStateEventFields.STATUS_END) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_CURRENT_STATE_EMPTY, 
                    "加工状态值无效，只接受0（结束）或1（开始）");
        }

        // 提取时间戳
        Long eventTimestamp = WebhookTimestampUtils.extractDeviceTimestamp(
                eventDataMap, request.getTelemetryData(), request.getDataTimestamp(), request.getTimestamp());
        if (eventTimestamp == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_TIMESTAMP_EMPTY);
        }

        return new EventData(previousStatus, currentStatus, eventTimestamp);
    }

    /**
     * 提取加工状态值
     * 支持数字类型和数字字符串
     * 
     * @param statusObj 状态值对象
     * @return 状态编码（0或1），如果无法识别则返回null
     */
    private Integer extractWorkingStatus(Object statusObj) {
        if (statusObj == null) {
            return null;
        }
        
        int status;
        
        // 如果是数字类型，直接获取整数值
        if (statusObj instanceof Number) {
            status = ((Number) statusObj).intValue();
        } else {
            // 如果是字符串，尝试解析为数字
            String statusStr = statusObj.toString().trim();
            try {
                status = Integer.parseInt(statusStr);
            } catch (NumberFormatException e) {
                log.warn("[DeviceWorkingStateEventHandler] 无法解析加工状态值，不是有效的数字: {}", statusStr);
                return null;
            }
        }
        
        // 验证状态值（只接受0或1）
        if (status == DeviceWorkingStateEventFields.STATUS_START 
                || status == DeviceWorkingStateEventFields.STATUS_END) {
            return status;
        }
        
        log.warn("[DeviceWorkingStateEventHandler] 加工状态值超出有效范围 [0-1]: {}", status);
        return null;
    }


    // ==================== 状态处理 ====================

    /**
     * 使用分布式锁处理加工状态变化
     */
    private void processWorkingStateChangeWithLock(EventData eventData, DeviceIdentity identity, WebhookRequest request) {
        Long deviceInfoId = identity.deviceInfoId();
        
        // 获取分布式锁
        if (!deviceLockService.tryLockProduction(deviceInfoId, DeviceWorkingStateEventFields.LOCK_TIMEOUT_SECONDS)) {
            log.warn("[Webhook-Handler-DeviceWorkingState] 获取设备加工状态锁失败: deviceInfoId={}, messageId={}",
                    deviceInfoId, request.getMessageId());
            throw new IotPortalException(IotPortalErrorCode.EVENT_DEVICE_STATE_PROCESSING);
        }

        try {
            // 查询数据库最新记录
            Optional<DeviceProductionRecordDO> latestOngoingOpt = deviceProductionRecordRepository.findLatestOngoing(deviceInfoId);
            
            // 处理加工状态变化
            processWorkingStateTransition(latestOngoingOpt, eventData, identity, request);
        } finally {
            deviceLockService.unlockProduction(deviceInfoId);
        }
    }

    /**
     * 处理加工状态转换
     */
    private void processWorkingStateTransition(Optional<DeviceProductionRecordDO> latestOngoingOpt,
                                                EventData eventData,
                                                DeviceIdentity identity,
                                                WebhookRequest request) {
        Long deviceInfoId = identity.deviceInfoId();
        Long orgFactoryId = identity.orgFactoryId();
        
        Integer previousStatus = eventData.previousStatus();
        Integer currentStatus = eventData.currentStatus();
        Long eventTimestamp = eventData.eventTimestamp();
        
        log.debug("[DeviceWorkingStateEventHandler] 处理加工状态转换: deviceInfoId={}, previousStatus={}, currentStatus={}, timestamp={}",
                deviceInfoId, previousStatus, currentStatus, eventTimestamp);
        
        // 判断处理场景
        TransitionType transitionType = determineTransitionType(latestOngoingOpt, eventData);
        
        switch (transitionType) {
            case START_NEW_PRODUCTION:
                // 从0到1：开始加工新产品
                handleStartNewProduction(deviceInfoId, orgFactoryId, eventData);
                break;
                
            case END_CURRENT_PRODUCTION:
                // 从1到0：完成当前产品加工
                handleEndCurrentProduction(latestOngoingOpt.get(), orgFactoryId, eventData, identity, request);
                break;
                
            case STATE_UNCHANGED:
                // 状态未变化（重复的相同状态事件）
                log.debug("[DeviceWorkingStateEventHandler] 状态未变化，跳过处理: deviceInfoId={}, status={}",
                        deviceInfoId, currentStatus);
                break;
                
            case STATE_MISMATCH:
                // 状态不匹配（异常情况）
                handleStateMismatch(latestOngoingOpt, eventData, identity, request);
                break;
                
            case FIRST_CONNECTION:
                // 首次连接（previousStatus = NULL）
                handleFirstConnection(deviceInfoId, orgFactoryId, eventData, identity, request);
                break;
                
            default:
                throw new IllegalStateException("未知的状态转换类型: " + transitionType);
        }
    }

    /**
     * 判断状态转换类型
     */
    private TransitionType determineTransitionType(Optional<DeviceProductionRecordDO> latestOngoingOpt,
                                                    EventData eventData) {
        Integer previousStatus = eventData.previousStatus();
        Integer currentStatus = eventData.currentStatus();
        
        // 首次连接：previousStatus 为 null
        if (previousStatus == null) {
            if (currentStatus == DeviceWorkingStateEventFields.STATUS_START) {
                log.debug("[DeviceWorkingStateEventHandler] 判断转换类型: FIRST_CONNECTION (previousStatus为空，currentStatus=1)");
                return TransitionType.FIRST_CONNECTION;
            } else {
                // currentStatus=0，首次连接但状态是结束，可能是异常情况
                log.warn("[DeviceWorkingStateEventHandler] 首次连接但状态是结束: currentStatus={}", currentStatus);
                return TransitionType.FIRST_CONNECTION;
            }
        }
        
        // 状态未变化
        if (previousStatus.equals(currentStatus)) {
            log.debug("[DeviceWorkingStateEventHandler] 判断转换类型: STATE_UNCHANGED (状态未变化)");
            return TransitionType.STATE_UNCHANGED;
        }
        
        // 从0到1：开始新产品
        if (previousStatus == DeviceWorkingStateEventFields.STATUS_END 
                && currentStatus == DeviceWorkingStateEventFields.STATUS_START) {
            log.debug("[DeviceWorkingStateEventHandler] 判断转换类型: START_NEW_PRODUCTION (0->1)");
            return TransitionType.START_NEW_PRODUCTION;
        }
        
        // 从1到0：完成产品
        if (previousStatus == DeviceWorkingStateEventFields.STATUS_START 
                && currentStatus == DeviceWorkingStateEventFields.STATUS_END) {
            boolean hasOngoing = latestOngoingOpt.isPresent();
            if (hasOngoing) {
                log.debug("[DeviceWorkingStateEventHandler] 判断转换类型: END_CURRENT_PRODUCTION (1->0，有进行中记录)");
                return TransitionType.END_CURRENT_PRODUCTION;
            } else {
                log.warn("[DeviceWorkingStateEventHandler] 判断转换类型: STATE_MISMATCH (1->0，但没有进行中记录)");
                return TransitionType.STATE_MISMATCH;
            }
        }
        
        // 其他情况：状态不匹配
        log.warn("[DeviceWorkingStateEventHandler] 判断转换类型: STATE_MISMATCH (未知的状态转换: {}->{})",
                previousStatus, currentStatus);
        return TransitionType.STATE_MISMATCH;
    }

    // ==================== 状态处理场景 ====================

    /**
     * 从0到1：开始加工新产品
     */
    private void handleStartNewProduction(Long deviceInfoId, Long orgFactoryId, EventData eventData) {
        log.debug("[DeviceWorkingStateEventHandler] 开始加工新产品: deviceInfoId={}, timestamp={}",
                deviceInfoId, eventData.eventTimestamp());
        
        // 检查是否有未结束的记录（异常情况）
        Optional<DeviceProductionRecordDO> ongoingOpt = deviceProductionRecordRepository.findLatestOngoing(deviceInfoId);
        if (ongoingOpt.isPresent()) {
            DeviceProductionRecordDO ongoing = ongoingOpt.get();
            log.warn("[DeviceWorkingStateEventHandler] 开始新产品但存在未结束的记录: deviceInfoId={}, 将先结束该记录, startTs={}",
                    deviceInfoId, ongoing.getStartTs());
            // 先结束未完成的记录
            ongoing.setEndTs(eventData.eventTimestamp());
            if (ongoing.getStartTs() != null) {
                ongoing.setDurationS(eventData.eventTimestamp() - ongoing.getStartTs());
            }
            recordHandlerUtils.fillShiftInfoIfMissing(ongoing, orgFactoryId);
            deviceProductionRecordRepository.updateById(ongoing);
        }
        
        // 插入新记录
        DeviceProductionRecordDO newRecord = createProductionRecord(
                deviceInfoId, orgFactoryId, eventData.eventTimestamp(), null);
        deviceProductionRecordRepository.insert(newRecord);
        
        log.debug("[DeviceWorkingStateEventHandler] 插入新加工记录: deviceInfoId={}, startTs={}",
                deviceInfoId, newRecord.getStartTs());
    }

    /**
     * 从1到0：完成当前产品加工
     */
    private void handleEndCurrentProduction(DeviceProductionRecordDO ongoing,
                                            Long orgFactoryId,
                                           EventData eventData,
                                           DeviceIdentity identity,
                                           WebhookRequest request) {
        Long deviceInfoId = identity.deviceInfoId();
        
        // 检查时间戳异常
        if (ongoing.getStartTs() != null && eventData.eventTimestamp() < ongoing.getStartTs()) {
            handleTimestampAnomaly(ongoing, eventData, identity, request);
            return;
        }
        
        log.debug("[DeviceWorkingStateEventHandler] 完成产品加工: deviceInfoId={}, startTs={}, endTs={}",
                deviceInfoId, ongoing.getStartTs(), eventData.eventTimestamp());
        
        // 更新记录
        ongoing.setEndTs(eventData.eventTimestamp());
        if (ongoing.getStartTs() != null) {
            ongoing.setDurationS(eventData.eventTimestamp() - ongoing.getStartTs());
        }
        recordHandlerUtils.fillShiftInfoIfMissing(ongoing, orgFactoryId);
        deviceProductionRecordRepository.updateById(ongoing);
        
        log.debug("[DeviceWorkingStateEventHandler] 更新加工记录: deviceInfoId={}, durationS={}",
                deviceInfoId, ongoing.getDurationS());
    }

    /**
     * 首次连接处理
     */
    private void handleFirstConnection(Long deviceInfoId, Long orgFactoryId,
                                       EventData eventData,
                                       DeviceIdentity identity,
                                       WebhookRequest request) {
        Integer currentStatus = eventData.currentStatus();
        
        if (currentStatus == DeviceWorkingStateEventFields.STATUS_START) {
            // 首次连接且状态是开始，插入新记录
            log.debug("[DeviceWorkingStateEventHandler] 首次连接，开始加工: deviceInfoId={}, timestamp={}",
                    deviceInfoId, eventData.eventTimestamp());
            
            // 检查是否有未结束的记录
            Optional<DeviceProductionRecordDO> ongoingOpt = deviceProductionRecordRepository.findLatestOngoing(deviceInfoId);
            if (ongoingOpt.isPresent()) {
                DeviceProductionRecordDO ongoing = ongoingOpt.get();
                log.warn("[DeviceWorkingStateEventHandler] 首次连接但存在未结束的记录: deviceInfoId={}, 将先结束该记录",
                        deviceInfoId);
                ongoing.setEndTs(eventData.eventTimestamp());
                if (ongoing.getStartTs() != null) {
                    ongoing.setDurationS(eventData.eventTimestamp() - ongoing.getStartTs());
                }
                recordHandlerUtils.fillShiftInfoIfMissing(ongoing, orgFactoryId);
                deviceProductionRecordRepository.updateById(ongoing);
            }
            
            DeviceProductionRecordDO newRecord = createProductionRecord(
                    deviceInfoId, orgFactoryId, eventData.eventTimestamp(), null);
            deviceProductionRecordRepository.insert(newRecord);
        } else {
            // 首次连接但状态是结束，可能是异常情况
            log.warn("[DeviceWorkingStateEventHandler] 首次连接但状态是结束: deviceInfoId={}, currentStatus={}",
                    deviceInfoId, currentStatus);
            // 插入一条仅结束的记录（起止相同）
            DeviceProductionRecordDO record = createProductionRecord(
                    deviceInfoId, orgFactoryId, eventData.eventTimestamp(), eventData.eventTimestamp());
            record.setDurationS(0L);
            deviceProductionRecordRepository.insert(record);
            
            String errorMessage = String.format("首次连接但状态是结束: currentStatus=%d", currentStatus);
            webhookFailLogService.saveFailLog(request, DeviceWorkingStateEventFields.ERROR_TYPE_STATE_MISMATCH,
                    errorMessage, false);
        }
    }

    /**
     * 状态不匹配处理
     */
    private void handleStateMismatch(Optional<DeviceProductionRecordDO> latestOngoingOpt,
                                     EventData eventData,
                                     DeviceIdentity identity,
                                     WebhookRequest request) {
        Long deviceInfoId = identity.deviceInfoId();
        Long orgFactoryId = identity.orgFactoryId();
        Integer previousStatus = eventData.previousStatus();
        Integer currentStatus = eventData.currentStatus();
        
        log.warn("[DeviceWorkingStateEventHandler] 状态不匹配: deviceInfoId={}, previousStatus={}, currentStatus={}",
                deviceInfoId, previousStatus, currentStatus);
        
        if (latestOngoingOpt.isPresent()) {
            // 有进行中的记录
            DeviceProductionRecordDO ongoing = latestOngoingOpt.get();
            
            // 检查时间戳异常
            if (ongoing.getStartTs() != null && eventData.eventTimestamp() < ongoing.getStartTs()) {
                handleTimestampAnomaly(ongoing, eventData, identity, request);
                return;
            }
            
            // 如果是从1到0，但数据库中有进行中记录，可能是状态不同步
            if (previousStatus == DeviceWorkingStateEventFields.STATUS_START 
                    && currentStatus == DeviceWorkingStateEventFields.STATUS_END) {
                // 结束该记录
                ongoing.setEndTs(eventData.eventTimestamp());
                if (ongoing.getStartTs() != null) {
                    ongoing.setDurationS(eventData.eventTimestamp() - ongoing.getStartTs());
                }
                recordHandlerUtils.fillShiftInfoIfMissing(ongoing, orgFactoryId);
                deviceProductionRecordRepository.updateById(ongoing);
                
                String errorMessage = String.format("状态不匹配但已修复: previousStatus=%d, currentStatus=%d, 已结束进行中记录",
                        previousStatus, currentStatus);
                webhookFailLogService.saveFailLog(request, DeviceWorkingStateEventFields.ERROR_TYPE_STATE_MISMATCH,
                        errorMessage, false);
            } else {
                // 其他不匹配情况，插入新记录
                DeviceProductionRecordDO newRecord = createProductionRecord(
                        deviceInfoId, orgFactoryId, eventData.eventTimestamp(), null);
                deviceProductionRecordRepository.insert(newRecord);
                
                String errorMessage = String.format("状态不匹配: previousStatus=%d, currentStatus=%d",
                        previousStatus, currentStatus);
                webhookFailLogService.saveFailLog(request, DeviceWorkingStateEventFields.ERROR_TYPE_STATE_MISMATCH,
                        errorMessage, true);
            }
        } else {
            // 没有进行中的记录
            if (currentStatus == DeviceWorkingStateEventFields.STATUS_START) {
                // 状态是开始，插入新记录
                DeviceProductionRecordDO newRecord = createProductionRecord(
                        deviceInfoId, orgFactoryId, eventData.eventTimestamp(), null);
                deviceProductionRecordRepository.insert(newRecord);
            } else {
                // 状态是结束，插入一条仅结束的记录
                DeviceProductionRecordDO record = createProductionRecord(
                        deviceInfoId, orgFactoryId, eventData.eventTimestamp(), eventData.eventTimestamp());
                record.setDurationS(0L);
                deviceProductionRecordRepository.insert(record);
            }
            
            String errorMessage = String.format("状态不匹配且无进行中记录: previousStatus=%d, currentStatus=%d",
                    previousStatus, currentStatus);
            webhookFailLogService.saveFailLog(request, DeviceWorkingStateEventFields.ERROR_TYPE_STATE_MISMATCH,
                    errorMessage, true);
        }
    }

    /**
     * 时间戳异常处理
     */
    private void handleTimestampAnomaly(DeviceProductionRecordDO ongoing,
                                         EventData eventData,
                                         DeviceIdentity identity,
                                         WebhookRequest request) {
        Long deviceInfoId = identity.deviceInfoId();
        Long orgFactoryId = identity.orgFactoryId();
        
        long gapMs = ongoing.getStartTs() - eventData.eventTimestamp();
        long gapSeconds = gapMs / 1000;
        
        log.warn("[DeviceWorkingStateEventHandler] 时间戳异常: deviceInfoId={}, 事件时间={}, 记录开始时间={}, 差距={}秒",
                deviceInfoId, eventData.eventTimestamp(), ongoing.getStartTs(), gapSeconds);
        
        // 先结束异常记录
        if (ongoing.getEndTs() == null) {
            ongoing.setEndTs(eventData.eventTimestamp());
            if (ongoing.getStartTs() != null) {
                long duration = eventData.eventTimestamp() - ongoing.getStartTs();
                ongoing.setDurationS(duration < 0 ? 0L : duration);
            }
            recordHandlerUtils.fillShiftInfoIfMissing(ongoing, orgFactoryId);
            deviceProductionRecordRepository.updateById(ongoing);
        }
        
        // 插入新记录，使用设备时间戳
        DeviceProductionRecordDO newRecord = createProductionRecord(
                deviceInfoId, orgFactoryId, eventData.eventTimestamp(), null);
        deviceProductionRecordRepository.insert(newRecord);
        
        String errorMessage = String.format("时间戳异常: 事件时间=%d, 记录开始时间=%d, 差距=%d秒 (已使用设备时间戳插入新记录)",
                eventData.eventTimestamp(), ongoing.getStartTs(), gapSeconds);
        webhookFailLogService.saveFailLog(request, DeviceWorkingStateEventFields.ERROR_TYPE_TIMESTAMP_ANOMALY,
                errorMessage, true);
    }

    // ==================== 记录创建方法 ====================

    /**
     * 创建加工记录
     */
    private DeviceProductionRecordDO createProductionRecord(Long deviceInfoId, Long orgFactoryId,
                                                             Long startTs, Long endTs) {
        DeviceProductionRecordDO record = new DeviceProductionRecordDO();
        record.setDeviceInfoId(deviceInfoId);
        record.setOrgFactoryId(orgFactoryId);
        record.setStartTs(startTs);
        record.setEndTs(endTs);
        
        if (endTs != null && startTs != null) {
            record.setDurationS(endTs - startTs);
        }
        
        // 使用通用工具类设置班次信息
        recordHandlerUtils.fillShiftInfoIfMissing(record, orgFactoryId);
        
        return record;
    }

    // ==================== 内部数据类 ====================

    /**
     * 事件数据
     */
    private record EventData(
            Integer previousStatus,    // 上一个状态（0或1）
            Integer currentStatus,     // 当前状态（0或1）
            Long eventTimestamp        // 事件时间戳
    ) {}

    /**
     * 状态转换类型
     */
    private enum TransitionType {
        START_NEW_PRODUCTION,      // 从0到1：开始新产品
        END_CURRENT_PRODUCTION,    // 从1到0：完成产品
        STATE_UNCHANGED,           // 状态未变化
        STATE_MISMATCH,            // 状态不匹配
        FIRST_CONNECTION           // 首次连接
    }

}

