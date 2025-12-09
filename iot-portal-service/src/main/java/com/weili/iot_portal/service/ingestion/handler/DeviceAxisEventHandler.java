package com.weili.iot_portal.service.ingestion.handler;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.RealTimeCacheService;
import com.weili.iot_portal.service.support.DeviceIdentityCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 设备轴信息事件处理器
 * 处理 DEVICE_AXIS 事件，写入实时轴信息缓存（rt:axis）
 *
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

    private static final String EVENT_TYPE = "DEVICE_AXIS";

    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final RealTimeCacheService realTimeCacheService;

    @Value("${rt.axis.ttl-millis:300000}")
    private long axisTtlMillis;

    @Value("${rt.axis.curve.ttl-millis:600000}")
    private long axisCurveTtlMillis;

    @Value("${rt.axis.curve.maxlen.load:2000}")
    private int axisCurveMaxLenLoad;

    @Value("${rt.axis.curve.maxlen.rpm:2000}")
    private int axisCurveMaxLenRpm;

    @Value("${rt.axis.curve.maxlen.feed:2000}")
    private int axisCurveMaxLenFeed;

    @Override
    public boolean supports(String eventType) {
        return EVENT_TYPE.equals(eventType);
    }

    @Override
    public int order() {
        return 20; // 在状态事件之后处理
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        Map<String, Object> eventData = request.getEventData();
        if (eventData == null || eventData.isEmpty()) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "事件数据不能为空");
        }

        // 解析设备标识（按 deviceCode / deviceId 解析为 portal 的 deviceInfoId / factoryId）
        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getTenantId(), request.getDeviceCode(),
                        request.getDeviceId(), "DeviceAxisEvent");
        String deviceInfoId = identity.getDeviceId();
        String orgFactoryId = identity.getFactoryId();

        // 解析时间戳
        Long eventTimestamp = request.getDataTimestamp() != null
                ? request.getDataTimestamp()
                : request.getTimestamp();
        if (eventTimestamp == null) {
            eventTimestamp = System.currentTimeMillis();
        }

        // 提取 axis.* 字段
        Map<String, Object> axisFields = extractAxisFields(eventData);
        if (axisFields.isEmpty()) {
            log.warn("DEVICE_AXIS 事件未包含 axis.* 字段，跳过写入: deviceInfoId={}", deviceInfoId);
        } else {
            Map<String, String> payload = new HashMap<>();
            axisFields.forEach((k, v) -> payload.put(k, String.valueOf(v)));
            payload.put("updatedAt", String.valueOf(eventTimestamp));
            payload.put("source", "TB");
            if (StringUtils.isNotBlank(request.getMessageId())) {
                payload.put("traceId", request.getMessageId());
            }
            // 倍率值
            Object ratio = eventData.get("ratio");
            if (ratio == null) {
                ratio = eventData.get("override");
            }
            if (ratio != null) {
                payload.put("ratio", String.valueOf(ratio));
            }
            String axisKey = String.format(RedisConstant.RT_AXIS,
                    defaultBlank(request.getTenantId()), defaultBlank(orgFactoryId), defaultBlank(deviceInfoId));
            realTimeCacheService.hsetWithTtl(axisKey, payload, axisTtlMillis);
        }

        // 写入曲线：负载/转速/进给（如果上报了相应字段）
        appendCurveIfPresent("load", eventTimestamp, request, orgFactoryId, deviceInfoId, eventData, axisCurveMaxLenLoad);
        appendCurveIfPresent("rpm", eventTimestamp, request, orgFactoryId, deviceInfoId, eventData, axisCurveMaxLenRpm);
        appendCurveIfPresent("feed", eventTimestamp, request, orgFactoryId, deviceInfoId, eventData, axisCurveMaxLenFeed);
    }

    /**
     * 提取 axis.* 字段
     */
    private Map<String, Object> extractAxisFields(Map<String, Object> eventData) {
        Map<String, Object> axisMap = new HashMap<>();
        eventData.forEach((k, v) -> {
            if (k != null && k.startsWith("axis.") && v != null) {
                axisMap.put(k, v);
            }
        });
        return axisMap;
    }

    /**
     * 写入曲线点（若存在），key 为 rt:axis:curve:{metric}:...
     */
    private void appendCurveIfPresent(String metric, Long eventTimestamp, WebhookRequest request,
                                      String orgFactoryId, String deviceInfoId, Map<String, Object> eventData, int maxLen) {
        Object value = eventData.get(metric);
        if (value == null) {
            return;
        }
        long ts = eventTimestamp != null ? eventTimestamp : System.currentTimeMillis();
        String pointJson = String.format("{\"ts\":%d,\"value\":%s}", ts, value.toString());
        String key = String.format("rt:axis:curve:%s:%s:%s:%s",
                defaultBlank(metric), defaultBlank(request.getTenantId()), defaultBlank(orgFactoryId), defaultBlank(deviceInfoId));
        realTimeCacheService.lpushTrimExpire(key, pointJson, maxLen, axisCurveTtlMillis);
    }

    private String defaultBlank(String value) {
        return StringUtils.defaultIfBlank(value, "none");
    }
}

