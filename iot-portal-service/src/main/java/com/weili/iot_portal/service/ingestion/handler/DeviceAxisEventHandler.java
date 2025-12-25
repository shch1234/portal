package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceAxisCacheService;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.WebhookProcessingStrategy;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceAxisEventFields;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import com.weili.iot_portal.domain.ingestion.DeviceIdentity;

/**
 * 设备轴信息事件处理器
 * 处理 DEVICE_AXIS 事件，写入实时轴信息缓存（rt:axis）
 * 事件数据要求：
 * - payload.eventData 内包含按约定命名的字段：axis.<axisName>.<field>
 *   例：axis.X.absolute, axis.X.relative, axis.X.machine, axis.X.remaining
 * - 仅写入存在的轴字段，未上报的轴不占位
     * - 负载/转速/进给曲线：load / rpm / feed 数值，将按 10min 滚动缓存（可配置）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceAxisEventHandler implements WebhookEventHandler {

    private final WebhookHandlerUtils webhookHandlerUtils;
    private final DeviceAxisCacheService deviceAxisCacheService;

    @Value("${rt.axis.curve.maxlen.load:2000}")
    private int axisCurveMaxLenLoad;

    @Value("${rt.axis.curve.maxlen.rpm:2000}")
    private int axisCurveMaxLenRpm;

    @Value("${rt.axis.curve.maxlen.feed:2000}")
    private int axisCurveMaxLenFeed;

    @Override
    public boolean supports(String eventType) {
        return DeviceAxisEventFields.EVENT_TYPE.equals(eventType);
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.DEVICE_AXIS;
    }

    @Override
    public WebhookProcessingStrategy getProcessingStrategy() {
        // 实时直接处理：只写Redis缓存，不需要持久化
        return WebhookProcessingStrategy.REALTIME_DIRECT;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        // REALTIME_DIRECT策略：inbox参数不使用，直接调用实时处理方法
        handleRealtime(request);
    }

    /**
     * 实时处理轴信息事件
     * <p>
     * REALTIME_DIRECT策略：只写Redis缓存，不需要持久化
     * </p>
     *
     * @param request Webhook请求对象
     */
    @Override
    public void handleRealtime(WebhookRequest request) {
        DeviceIdentity identity = webhookHandlerUtils.resolveDeviceIdentity(request);
        Long deviceInfoId = identity.deviceInfoId();
        Long orgFactoryId = identity.orgFactoryId();

        // 解析时间戳
        Long eventTimestamp = request.getDataTimestamp() != null
                ? request.getDataTimestamp()
                : request.getTimestamp();
        if (eventTimestamp == null) {
            eventTimestamp = System.currentTimeMillis();
        }
        Map<String, Object> eventData = request.getEventData();
        // 提取 axis.* 字段
        Map<String, Object> axisFields = extractAxisFields(eventData);
        if (axisFields.isEmpty()) {
            log.warn("DEVICE_AXIS 事件未包含 axis.* 字段，跳过写入: deviceInfoId={}", deviceInfoId);
        } else {
            // 倍率值
            Object ratio = eventData.get(DeviceAxisEventFields.RATIO);
            if (ratio == null) {
                ratio = eventData.get(DeviceAxisEventFields.OVERRIDE);
            }
            deviceAxisCacheService.saveAxisData(orgFactoryId, deviceInfoId, axisFields,
                    eventTimestamp, DeviceAxisEventFields.SOURCE_TB,
                    request.getMessageId(), ratio);
        }

        // 写入曲线：负载/转速/进给（如果上报了相应字段）
        appendCurveIfPresent(DeviceAxisEventFields.METRIC_LOAD, eventTimestamp, orgFactoryId, deviceInfoId, eventData, axisCurveMaxLenLoad);
        appendCurveIfPresent(DeviceAxisEventFields.METRIC_RPM, eventTimestamp, orgFactoryId, deviceInfoId, eventData, axisCurveMaxLenRpm);
        appendCurveIfPresent(DeviceAxisEventFields.METRIC_FEED, eventTimestamp, orgFactoryId, deviceInfoId, eventData, axisCurveMaxLenFeed);
    }

    /**
     * 提取 axis.* 字段并优化字段名
     * 优化前：axis.X.absolute → 优化后：X.abs
     * 节省约70%内存空间
     */
    private Map<String, Object> extractAxisFields(Map<String, Object> eventData) {
        Map<String, Object> axisMap = new HashMap<>();
        eventData.forEach((k, v) -> {
            if (DeviceAxisEventFields.isAxisField(k) && v != null) {
                // 优化字段名：axis.X.absolute -> X.abs
                String optimizedKey = optimizeFieldName(k);
                axisMap.put(optimizedKey, v);
            }
        });
        return axisMap;
    }

    /**
     * 优化字段名，减少内存占用
     * axis.X.absolute -> X.abs
     * axis.Y.relative -> Y.rel
     * axis.Z.machine -> Z.mach
     * axis.A.remaining -> A.rem
     */
    private String optimizeFieldName(String fieldName) {
        if (!DeviceAxisEventFields.isAxisField(fieldName)) {
            return fieldName;
        }

        // 移除 "axis." 前缀
        String withoutPrefix = fieldName.substring(DeviceAxisEventFields.AXIS_PREFIX.length());

        // 简化坐标类型名称
        return withoutPrefix
                .replace(".absolute", ".abs")
                .replace(".relative", ".rel")
                .replace(".machine", ".mach")
                .replace(".remaining", ".rem");
    }

    /**
     * 写入曲线点（若存在）
     */
    private void appendCurveIfPresent(String metric, Long eventTimestamp,
                                      Long orgFactoryId, Long deviceInfoId,
                                      Map<String, Object> eventData, int maxLen) {
        Object value = eventData.get(metric);
        if (value == null) {
            return;
        }
        long ts = eventTimestamp != null ? eventTimestamp : System.currentTimeMillis();
        deviceAxisCacheService.appendCurvePoint(orgFactoryId, deviceInfoId, metric, ts, value, maxLen);
    }
}

