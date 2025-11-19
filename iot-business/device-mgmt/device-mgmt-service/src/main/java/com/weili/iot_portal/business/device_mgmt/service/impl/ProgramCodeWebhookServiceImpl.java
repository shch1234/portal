package com.weili.iot_portal.business.device_mgmt.service.impl;

import com.weili.iot_portal.business.device_mgmt.domain.model.ProgramCodeVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.ProgramCodeWebhookRequest;
import com.weili.iot_portal.business.device_mgmt.service.ProgramCodeWebhookService;
import com.weili.iot_portal.business.device_mgmt.service.support.DeviceIdentityCacheService;
import com.weili.iot_portal.service.ingestion.dispatcher.RealtimeIngestionDispatcher;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEvent;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEventType;
import com.weili.iot_portal.service.ingestion.support.WebhookIdempotentService;
import com.weili.iot_portal.service.ingestion.support.WebhookSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramCodeWebhookServiceImpl implements ProgramCodeWebhookService {

    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final WebhookIdempotentService webhookIdempotentService;
    private final WebhookSecurityService webhookSecurityService;
    private final RealtimeIngestionDispatcher dispatcher;

    @Override
    public void handleProgramCodeWebhook(ProgramCodeWebhookRequest request, String secret) {
        webhookSecurityService.validate(secret);

        if (!webhookIdempotentService.tryConsume(request.getMessageId())) {
            log.warn("程序代码Webhook重复，忽略: messageId={}", request.getMessageId());
            return;
        }

        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getTenantId(), request.getDeviceCode(),
                        request.getTbDeviceId(), "programCode");

        ProgramCodeVO payload = new ProgramCodeVO();
        payload.setType(request.getType());
        payload.setContent(request.getContent());
        payload.setTs(request.getTs() == null ? System.currentTimeMillis() : request.getTs());

        RealtimeIngestionEvent event = RealtimeIngestionEvent.builder()
                .eventType(RealtimeIngestionEventType.PROGRAM_CODE)
                .messageId(request.getMessageId())
                .tenantId(request.getTenantId())
                .factoryId(identity.getFactoryId())
                .deviceId(identity.getDeviceId())
                .timestamp(payload.getTs())
                .payload(payload)
                .build();

        dispatcher.dispatch(event);

        log.info("接收程序代码Webhook成功: messageId={}, tenantId={}, deviceCode={}, deviceId={}, factoryId={}, type={}, tbDeviceId={}",
                request.getMessageId(), request.getTenantId(), request.getDeviceCode(),
                identity.getDeviceId(), identity.getFactoryId(), request.getType(), request.getTbDeviceId());
    }
}

