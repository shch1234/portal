package com.weili.iot_portal.service.devicemng.impl;

import com.weili.iot_portal.dal.dataobject.devicemng.ToolUsageHistoryDO;
import com.weili.iot_portal.dal.repository.devicemng.ToolUsageHistoryRepository;
import com.weili.iot_portal.domain.devicemng.request.ToolUsageWebhookRequest;
import com.weili.iot_portal.service.devicemng.ToolUsageWebhookService;
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
public class ToolUsageWebhookServiceImpl implements ToolUsageWebhookService {

    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final WebhookIdempotentService webhookIdempotentService;
    private final WebhookSecurityService webhookSecurityService;
    private final ToolUsageHistoryRepository toolUsageHistoryRepository;

    @Override
    public void handleToolUsageWebhook(ToolUsageWebhookRequest request, String secret) {
        webhookSecurityService.validate(secret);

        if (!webhookIdempotentService.tryConsume(request.getMessageId())) {
            log.warn("刀具列表Webhook重复，忽略: messageId={}", request.getMessageId());
            return;
        }

        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getTenantId(), request.getDeviceCode(),
                        request.getTbDeviceId(), "toolUsage");

        List<ToolUsageHistoryDO> batch = request.getItems().stream()
                .map(item -> buildDO(identity, request, item))
                .collect(Collectors.toList());

        toolUsageHistoryRepository.insertBatch(batch);

        log.info("接收刀具列表Webhook成功: messageId={}, tenantId={}, deviceCode={}, deviceId={}, factoryId={}, count={}, tbDeviceId={}",
                request.getMessageId(), request.getTenantId(), request.getDeviceCode(),
                identity.getDeviceId(), identity.getFactoryId(), batch.size(), request.getTbDeviceId());
    }

    private ToolUsageHistoryDO buildDO(DeviceIdentityCacheService.DeviceIdentity identity,
                                       ToolUsageWebhookRequest request,
                                       ToolUsageWebhookRequest.ToolUsageItem item) {
        ToolUsageHistoryDO record = new ToolUsageHistoryDO();
        record.setTenantId(request.getTenantId());
        record.setDeviceId(identity.getDeviceId());
        record.setFactoryId(identity.getFactoryId());
        record.setToolNumber(item.getToolNumber());
        record.setToolHolderNumber(item.getToolHolderNumber());
        record.setStartTs(item.getStartTs());
        record.setEndTs(item.getEndTs());
        record.setDurationMs(item.getDurationMs());
        return record;
    }
}

