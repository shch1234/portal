package com.weili.iot_portal.service.devicemng.impl;

import com.weili.iot_portal.domain.devicemng.CurrentToolInfoVO;
import com.weili.iot_portal.domain.devicemng.request.CurrentToolWebhookRequest;
import com.weili.iot_portal.service.devicemng.ToolInfoWebhookService;
import com.weili.iot_portal.service.ingestion.dispatcher.RealtimeIngestionDispatcher;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEvent;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEventType;
import com.weili.iot_portal.service.ingestion.support.WebhookIdempotentService;
import com.weili.iot_portal.service.ingestion.support.WebhookSecurityService;
import com.weili.iot_portal.service.support.DeviceIdentityCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolInfoWebhookServiceImpl implements ToolInfoWebhookService {

    private final RealtimeIngestionDispatcher dispatcher;
    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final WebhookIdempotentService webhookIdempotentService;
    private final WebhookSecurityService webhookSecurityService;

    @Override
    public void handleToolInfoWebhook(CurrentToolWebhookRequest request, String secret) {
        webhookSecurityService.validate(secret);

        if (!webhookIdempotentService.tryConsume(request.getMessageId())) {
            log.warn("刀具信息Webhook重复，忽略: messageId={}", request.getMessageId());
            return;
        }

        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getTenantId(), request.getDeviceCode(),
                        request.getTbDeviceId(), "toolInfo");

        CurrentToolInfoVO payload = buildPayload(request);

        RealtimeIngestionEvent event = RealtimeIngestionEvent.builder()
                .eventType(RealtimeIngestionEventType.TOOL_INFO)
                .messageId(request.getMessageId())
                .tenantId(request.getTenantId())
                .factoryId(identity.getFactoryId())
                .deviceId(identity.getDeviceId())
                .timestamp(request.getTs())
                .payload(payload)
                .build();

        dispatcher.dispatch(event);

        log.info("接收刀具信息Webhook成功: messageId={}, tenantId={}, deviceCode={}, deviceId={}, factoryId={}, tbDeviceId={}",
                request.getMessageId(), request.getTenantId(), request.getDeviceCode(),
                identity.getDeviceId(), identity.getFactoryId(), request.getTbDeviceId());
    }

    private CurrentToolInfoVO buildPayload(CurrentToolWebhookRequest request) {
        CurrentToolInfoVO vo = new CurrentToolInfoVO();
        vo.setToolNumber(request.getToolNumber());
        vo.setToolHolderNumber(request.getToolHolderNumber());
        vo.setLengthComp(request.getLengthComp());
        vo.setRadiusComp(request.getRadiusComp());
        vo.setLengthWear(request.getLengthWear());
        vo.setRadiusWear(request.getRadiusWear());
        vo.setTs(request.getTs());
        return vo;
    }
}

