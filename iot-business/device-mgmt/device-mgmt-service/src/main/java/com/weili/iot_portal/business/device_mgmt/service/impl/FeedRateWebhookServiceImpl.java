package com.weili.iot_portal.business.device_mgmt.service.impl;

import com.weili.iot_portal.business.device_mgmt.domain.model.RealtimeCurvePointVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.RealtimeCurveVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.FeedRateWebhookRequest;
import com.weili.iot_portal.business.device_mgmt.service.FeedRateWebhookService;
import com.weili.iot_portal.business.device_mgmt.service.support.DeviceIdentityCacheService;
import com.weili.iot_portal.service.ingestion.dispatcher.RealtimeIngestionDispatcher;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEvent;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEventType;
import com.weili.iot_portal.service.ingestion.support.WebhookIdempotentService;
import com.weili.iot_portal.service.ingestion.support.WebhookSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedRateWebhookServiceImpl implements FeedRateWebhookService {

    private static final String METRIC = "feedRate";

    private final RealtimeIngestionDispatcher dispatcher;
    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final WebhookIdempotentService webhookIdempotentService;
    private final WebhookSecurityService webhookSecurityService;

    @Override
    public void handleFeedRateWebhook(FeedRateWebhookRequest request, String secret) {
        webhookSecurityService.validate(secret);

        if (!webhookIdempotentService.tryConsume(request.getMessageId())) {
            log.warn("进给率Webhook重复，忽略: messageId={}", request.getMessageId());
            return;
        }

        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getTenantId(), request.getDeviceCode(),
                        request.getTbDeviceId(), "feedRate");

        RealtimeCurveVO curve = buildCurve(request);

        RealtimeIngestionEvent event = RealtimeIngestionEvent.builder()
                .eventType(RealtimeIngestionEventType.FEED_RATE)
                .messageId(request.getMessageId())
                .tenantId(request.getTenantId())
                .factoryId(identity.getFactoryId())
                .deviceId(identity.getDeviceId())
                .timestamp(request.getTs())
                .payload(curve)
                .extra(request.getOverrideValue())
                .build();

        dispatcher.dispatch(event);

        log.info("接收进给率Webhook成功: messageId={}, tenantId={}, deviceCode={}, deviceId={}, factoryId={}, points={}, override={}, tbDeviceId={}",
                request.getMessageId(), request.getTenantId(), request.getDeviceCode(),
                identity.getDeviceId(), identity.getFactoryId(), curve.getPoints().size(),
                request.getOverrideValue(), request.getTbDeviceId());
    }

    private RealtimeCurveVO buildCurve(FeedRateWebhookRequest request) {
        RealtimeCurveVO vo = new RealtimeCurveVO();
        vo.setMetric(METRIC);
        List<RealtimeCurvePointVO> points = request.getPoints().stream()
                .map(point -> {
                    RealtimeCurvePointVO voPoint = new RealtimeCurvePointVO();
                    voPoint.setTs(point.getTs());
                    voPoint.setValue(point.getFeedRate());
                    return voPoint;
                })
                .collect(Collectors.toList());
        vo.setPoints(points);
        vo.setStartTs(points.stream().map(RealtimeCurvePointVO::getTs).min(Long::compareTo).orElse(request.getTs()));
        vo.setEndTs(points.stream().map(RealtimeCurvePointVO::getTs).max(Long::compareTo).orElse(request.getTs()));
        return vo;
    }
}

