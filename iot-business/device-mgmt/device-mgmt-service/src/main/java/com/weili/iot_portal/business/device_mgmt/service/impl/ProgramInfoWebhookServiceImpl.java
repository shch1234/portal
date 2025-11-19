package com.weili.iot_portal.business.device_mgmt.service.impl;

import com.weili.iot_portal.business.device_mgmt.domain.model.ProgramInfoVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.ProgramInfoWebhookRequest;
import com.weili.iot_portal.business.device_mgmt.service.ProgramInfoWebhookService;
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
public class ProgramInfoWebhookServiceImpl implements ProgramInfoWebhookService {

    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final WebhookIdempotentService webhookIdempotentService;
    private final WebhookSecurityService webhookSecurityService;
    private final RealtimeIngestionDispatcher dispatcher;

    @Override
    public void handleProgramInfoWebhook(ProgramInfoWebhookRequest request, String secret) {
        webhookSecurityService.validate(secret);

        if (!webhookIdempotentService.tryConsume(request.getMessageId())) {
            log.warn("程序信息Webhook重复，忽略: messageId={}", request.getMessageId());
            return;
        }

        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getTenantId(), request.getDeviceCode(),
                        request.getTbDeviceId(), "programInfo");

        ProgramInfoVO payload = buildPayload(identity.getDeviceId(), request);

        RealtimeIngestionEvent event = RealtimeIngestionEvent.builder()
                .eventType(RealtimeIngestionEventType.PROGRAM_INFO)
                .messageId(request.getMessageId())
                .tenantId(request.getTenantId())
                .factoryId(identity.getFactoryId())
                .deviceId(identity.getDeviceId())
                .timestamp(request.getTs())
                .payload(payload)
                .build();

        dispatcher.dispatch(event);

        log.info("接收程序信息Webhook成功: messageId={}, tenantId={}, deviceCode={}, deviceId={}, factoryId={}, tbDeviceId={}",
                request.getMessageId(), request.getTenantId(), request.getDeviceCode(),
                identity.getDeviceId(), identity.getFactoryId(), request.getTbDeviceId());
    }

    private ProgramInfoVO buildPayload(String deviceId, ProgramInfoWebhookRequest request) {
        ProgramInfoVO vo = new ProgramInfoVO();
        vo.setDeviceId(deviceId);
        vo.setProgramName(request.getProgramName());
        vo.setProgramPath(request.getProgramPath());
        vo.setCurrentLine(request.getCurrentLine());
        vo.setCurrentCode(request.getCurrentCode());
        vo.setTs(request.getTs() == null ? System.currentTimeMillis() : request.getTs());
        return vo;
    }
}

