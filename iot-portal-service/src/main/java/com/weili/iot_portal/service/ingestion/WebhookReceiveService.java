package com.weili.iot_portal.service.ingestion;

import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.support.DeviceMatchingService;
import com.weili.iot_portal.service.ingestion.support.RealtimeWebhookCacheService;
import com.weili.iot_portal.service.ingestion.support.WebhookIdempotentService;
import com.weili.iot_portal.service.ingestion.support.WebhookInboxService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class WebhookReceiveService {

    @Autowired
    private WebhookSecurityService securityService;

    @Autowired
    private WebhookIdempotentService idempotentService;

    @Autowired
    private DeviceMatchingService deviceMatchingService;

    @Autowired
    private WebhookInboxService webhookInboxService;

    @Autowired
    private RealtimeWebhookCacheService realtimeWebhookCacheService;

    /**
     * 处理 webhook 消息
     */
    public void handle(String category,
                       String eventType,
                       String rawBody,
                       WebhookRequest request,
                       String headerSecret,
                       String signature,
                       String timestamp,
                       String nonce) {
        // 1) 校验可选 header 密钥
        securityService.validate(headerSecret);
        // 2) 验签 + 时间戳 + nonce
        securityService.validateSignature(signature, timestamp, nonce, rawBody);
        // 3) 幂等
        if (!idempotentService.tryConsume(request.getMessageId())) {
            log.info("Webhook 已处理，跳过: messageId={}", request.getMessageId());
            return;
        }
        // 4) 设备匹配
        Optional<DeviceInfoDO> deviceOpt = deviceMatchingService.match(request.getDeviceCode());
        if (deviceOpt.isEmpty()) {
            log.warn("Webhook 设备未匹配，直接ACK: messageId={}, deviceCode={}", request.getMessageId(), request.getDeviceCode());
            return;
        }
        DeviceInfoDO device = deviceOpt.get();
        // 补充设备/租户信息
        if (StringUtils.isBlank(request.getDeviceId())) {
            request.setDeviceId(device.getTbDeviceId());
        }
        if (StringUtils.isBlank(request.getTenantId())) {
            request.setTenantId(device.getTenantUuid());
        }
        request.setWebhookCategory(category);
        request.setEventType(eventType);

        // 5) 分类处理
        if ("BUSINESS".equalsIgnoreCase(category)) {
            webhookInboxService.saveToInbox(request);
        } else if ("REALTIME".equalsIgnoreCase(category)) {
            realtimeWebhookCacheService.cache(eventType, device.getDeviceCode(), request);
        } else {
            throw new ServiceException(400, "不支持的 webhook category");
        }
    }
}

