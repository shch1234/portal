package com.weili.iot_portal.service.devicemng.impl;

import com.weili.iot_portal.domain.devicemng.RealtimeCurvePointVO;
import com.weili.iot_portal.domain.devicemng.RealtimeCurveVO;
import com.weili.iot_portal.domain.devicemng.request.SpindleSpeedWebhookRequest;
import com.weili.iot_portal.service.devicemng.SpindleSpeedWebhookService;
import com.weili.iot_portal.service.ingestion.dispatcher.RealtimeIngestionDispatcher;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEvent;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEventType;
import com.weili.iot_portal.service.ingestion.support.WebhookIdempotentService;
import com.weili.iot_portal.service.ingestion.support.WebhookSecurityService;
import com.weili.iot_portal.service.support.DeviceIdentityCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpindleSpeedWebhookServiceImpl implements SpindleSpeedWebhookService {

    private static final String METRIC = "spindleSpeed";

    private final RealtimeIngestionDispatcher dispatcher;
    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final WebhookIdempotentService webhookIdempotentService;
    private final WebhookSecurityService webhookSecurityService;

    @Override
    public void handleSpindleSpeedWebhook(SpindleSpeedWebhookRequest request, String secret) {
        webhookSecurityService.validate(secret);

        if (!webhookIdempotentService.tryConsume(request.getMessageId())) {
            log.warn("主轴转速Webhook重复，忽略: messageId={}", request.getMessageId());
            return;
        }

        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getTenantId(), request.getDeviceCode(),
                        request.getTbDeviceId(), "spindleSpeed");

        RealtimeCurveVO curve = buildCurve(identity.getDeviceId(), request);

        RealtimeIngestionEvent event = RealtimeIngestionEvent.builder()
                .eventType(RealtimeIngestionEventType.SPINDLE_SPEED)
                .messageId(request.getMessageId())
                .tenantId(request.getTenantId())
                .factoryId(identity.getFactoryId())
                .deviceId(identity.getDeviceId())
                .timestamp(request.getTs())
                .payload(curve)
                .build();

        dispatcher.dispatch(event);

        log.info("接收主轴转速Webhook成功: messageId={}, tenantId={}, deviceCode={}, deviceId={}, factoryId={}, points={}, tbDeviceId={}",
                request.getMessageId(), request.getTenantId(), request.getDeviceCode(),
                identity.getDeviceId(), identity.getFactoryId(), curve.getPoints().size(), request.getTbDeviceId());
    }

    private RealtimeCurveVO buildCurve(String deviceId, SpindleSpeedWebhookRequest request) {
        RealtimeCurveVO vo = new RealtimeCurveVO();
        vo.setMetric(METRIC);
        List<RealtimeCurvePointVO> points = request.getPoints().stream()
                .map(point -> {
                    RealtimeCurvePointVO voPoint = new RealtimeCurvePointVO();
                    voPoint.setTs(point.getTs());
                    voPoint.setValue(point.getSpeed());
                    return voPoint;
                })
                .collect(Collectors.toList());
        vo.setPoints(points);
        vo.setStartTs(points.stream().map(RealtimeCurvePointVO::getTs).min(Long::compareTo).orElse(request.getTs()));
        vo.setEndTs(points.stream().map(RealtimeCurvePointVO::getTs).max(Long::compareTo).orElse(request.getTs()));
        return vo;
    }
}

