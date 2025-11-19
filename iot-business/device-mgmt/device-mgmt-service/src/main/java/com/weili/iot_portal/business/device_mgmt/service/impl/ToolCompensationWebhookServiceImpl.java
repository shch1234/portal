package com.weili.iot_portal.business.device_mgmt.service.impl;

import com.weili.iot_portal.business.device_mgmt.domain.model.ToolCompensationUpdateItem;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.ToolCompensationWebhookRequest;
import com.weili.iot_portal.business.device_mgmt.service.ToolCompensationWebhookService;
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
public class ToolCompensationWebhookServiceImpl implements ToolCompensationWebhookService {

    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final WebhookIdempotentService webhookIdempotentService;
    private final WebhookSecurityService webhookSecurityService;
    private final RealtimeIngestionDispatcher dispatcher;

    @Override
    public void handleWebhook(ToolCompensationWebhookRequest request, String secret) {
        webhookSecurityService.validate(secret);

        if (!webhookIdempotentService.tryConsume(request.getMessageId())) {
            log.warn("刀具补偿Webhook重复，忽略: messageId={}", request.getMessageId());
            return;
        }

        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getTenantId(), request.getDeviceCode(),
                        request.getTbDeviceId(), "toolCompensation");

        List<ToolCompensationUpdateItem> items = request.getItems().stream()
                .map(item -> {
                    ToolCompensationUpdateItem update = new ToolCompensationUpdateItem();
                    update.setDimension(item.getDimension());
                    update.setSlot(item.getSlot());
                    update.setShapeValue(item.getShapeValue());
                    update.setWearValue(item.getWearValue());
                    update.setTs(item.getTs());
                    return update;
                })
                .collect(Collectors.toList());

        RealtimeIngestionEvent event = RealtimeIngestionEvent.builder()
                .eventType(RealtimeIngestionEventType.TOOL_COMPENSATION)
                .messageId(request.getMessageId())
                .tenantId(request.getTenantId())
                .factoryId(identity.getFactoryId())
                .deviceId(identity.getDeviceId())
                .timestamp(System.currentTimeMillis())
                .payload(items)
                .build();

        dispatcher.dispatch(event);

        log.info("接收刀具补偿Webhook成功: messageId={}, tenantId={}, deviceCode={}, deviceId={}, factoryId={}, count={}, tbDeviceId={}",
                request.getMessageId(), request.getTenantId(), request.getDeviceCode(),
                identity.getDeviceId(), identity.getFactoryId(), items.size(), request.getTbDeviceId());
    }
}

