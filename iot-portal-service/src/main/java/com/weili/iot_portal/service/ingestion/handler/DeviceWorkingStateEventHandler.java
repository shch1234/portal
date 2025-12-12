package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceIdentityCacheService;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceWorkingStateEventFields;
import com.weili.iot_portal.common.utils.WebhookTimestampUtils;
import com.weili.iot_portal.service.ingestion.handler.support.WebhookHandlerUtils;

import static com.weili.iot_portal.service.ingestion.handler.support.WebhookHandlerUtils.DeviceIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 设备加工状态事件处理器
 * <p>
 * 处理设备加工状态变化事件（开始/结束）。
 * 加工状态：开始为1，结束为0
 * </p>
 * <p>
 * 处理流程：
 * 1. 解析事件数据（状态、时间戳等）
 * 2. 记录加工状态变化
 * 3. 后续可以扩展为存储到数据库
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceWorkingStateEventHandler implements WebhookEventHandler {

    private final WebhookHandlerUtils webhookHandlerUtils;

    @Override
    public boolean supports(String eventType) {
        return DeviceWorkingStateEventFields.EVENT_TYPE.equals(eventType);
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.DEVICE_WORKING_STATE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        log.info("[Webhook-Handler-DeviceWorkingState] 处理设备加工状态事件: messageId={}, eventType={}, deviceCode={}",
                request.getMessageId(), request.getEventType(), request.getDeviceCode());

        // 1. 解析事件数据
        EventData eventData = parseEventData(request);
        
        // 2. 解析设备信息
        DeviceIdentity identity = webhookHandlerUtils.resolveDeviceIdentity(request);
        
        // 3. 处理加工状态变化
        processWorkingStateChange(eventData, identity, request);
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
     * 处理加工状态变化
     */
    private void processWorkingStateChange(EventData eventData, DeviceIdentity identity, WebhookRequest request) {
        String deviceInfoId = identity.deviceInfoId();
        String orgFactoryId = identity.orgFactoryId();
        
        log.info("[DeviceWorkingStateEventHandler] 加工状态变化: deviceInfoId={}, 从{}到{}, timestamp={}",
                deviceInfoId, eventData.previousStatus(), eventData.currentStatus(), eventData.eventTimestamp());
        
        // TODO: 后续可以扩展为存储到数据库
        // 例如：创建 DeviceWorkingStateRecordDO 和 DeviceWorkingStateRecordRepository
        // 类似于 DeviceProductionRecordDO 的处理方式
        
        // 当前实现：仅记录日志，后续可以根据业务需求添加数据库存储
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

}

