package com.weili.iot_portal.service.ingestion;

import com.weili.iot_portal.common.enums.WebHookCategoryType;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.common.enums.InboxStatusEnum;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.support.DeviceMatchingService;
import com.weili.iot_portal.service.ingestion.support.RealtimeWebhookCacheService;
import com.weili.iot_portal.service.ingestion.support.WebhookIdempotentService;
import com.weili.iot_portal.service.ingestion.support.WebhookInboxService;
import com.weili.iot_portal.service.ingestion.support.WebhookProcessService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
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
    
    @Autowired
    private WebhookProcessService webhookProcessService;
    
    @Value("${webhook.inbox.async-process-enabled:true}")
    private boolean asyncProcessEnabled;

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
        log.debug("[Webhook-处理] ====== 开始处理Webhook消息 ======");
        log.debug("[Webhook-处理] messageId={}, category={}, eventType={}, deviceCode={}", 
            request.getMessageId(), category, eventType, request.getDeviceCode());
        
        // 1) 校验可选 header 密钥
        log.debug("[Webhook-处理] [步骤1] 校验Header密钥");
        securityService.validate(headerSecret);
        log.debug("[Webhook-处理] [步骤1] Header密钥校验通过");
        
        // 2) 验签 + 时间戳 + nonce
        log.debug("[Webhook-处理] [步骤2] 验签: signature={}, timestamp={}, nonce={}", 
            signature != null ? signature.substring(0, Math.min(8, signature.length())) + "..." : "null", timestamp, nonce);
        securityService.validateSignature(signature, timestamp, nonce, rawBody);
        log.debug("[Webhook-处理] [步骤2] 签名验证通过");
        
        // 3) 幂等
        log.debug("[Webhook-处理] [步骤3] 幂等性检查: messageId={}", request.getMessageId());
        if (!idempotentService.tryConsume(request.getMessageId())) {
            log.info("[Webhook-处理] [步骤3] 消息已处理，跳过: messageId={}", request.getMessageId());
            return;
        }
        log.debug("[Webhook-处理] [步骤3] 幂等性检查通过，消息未处理过");
        
        // 4) 设备匹配
        log.debug("[Webhook-处理] [步骤4] 设备匹配: deviceCode={}", request.getDeviceCode());
        Optional<DeviceInfoDO> deviceOpt = deviceMatchingService.match(request.getDeviceCode());
        if (deviceOpt.isEmpty()) {
            log.warn("[Webhook-处理] [步骤4] 设备未匹配，直接ACK: messageId={}, deviceCode={}", 
                request.getMessageId(), request.getDeviceCode());
            return;
        }
        DeviceInfoDO device = deviceOpt.get();
        log.debug("[Webhook-处理] [步骤4] 设备匹配成功: deviceCode={}, deviceId={}, deviceInfoId={}", 
            device.getDeviceCode(), device.getTbDeviceId(), device.getId());
        
        // 补充设备/租户信息
        if (StringUtils.isBlank(request.getDeviceId())) {
            request.setDeviceId(device.getTbDeviceId());
            log.debug("[Webhook-处理] [步骤4] 补充deviceId: {}", device.getTbDeviceId());
        }
        request.setWebhookCategory(category);
        
        // 优先使用请求体中的eventType（更准确），如果为空则使用URL路径中的eventType
        if (StringUtils.isBlank(request.getEventType())) {
            request.setEventType(eventType);
            log.debug("[Webhook-处理] [步骤4] 使用URL路径中的eventType: {}", eventType);
        } else {
            log.debug("[Webhook-处理] [步骤4] 使用请求体中的eventType: {}, URL路径中的eventType: {}", 
                request.getEventType(), eventType);
        }
        log.debug("[Webhook-处理] [步骤4] 设置category和eventType: category={}, eventType={}", 
            category, request.getEventType());

        // 5) 分类处理
        log.debug("[Webhook-处理] [步骤5] 分类处理: category={}", category);
        if (WebHookCategoryType.BUSINESS.name().equalsIgnoreCase(category)) {
            log.debug("[Webhook-处理] [步骤5] 业务数据，保存到收件箱: messageId={}, eventType={}", 
                request.getMessageId(), eventType);
            webhookInboxService.saveToInbox(request);
            log.debug("[Webhook-处理] [步骤5] 业务数据已保存到收件箱");
            
            // 立即异步处理（实时处理）
            if (asyncProcessEnabled) {
                processMessageAsync(request.getMessageId());
            }
        } else if (WebHookCategoryType.REALTIME.name().equalsIgnoreCase(category)) {
            realtimeWebhookCacheService.cache(eventType, device.getDeviceCode(), request);
            log.debug("[Webhook-处理] [步骤5] 实时数据已缓存");
        } else {
            throw new IotPortalException(IotPortalErrorCode.WEBHOOK_CATEGORY_NOT_SUPPORTED);
        }
        
        log.debug("[Webhook-处理] ====== Webhook消息处理完成 ====== messageId={}", request.getMessageId());
    }
    
    /**
     * 异步处理单条消息（实时处理）
     * 通过状态字段（PENDING -> PROCESSING）保证幂等性，避免重复处理
     * 
     * @param messageId 消息ID
     */
    @Async
    public void processMessageAsync(String messageId) {
        try {
            log.debug("[Webhook-处理] 开始异步处理消息: messageId={}", messageId);
            
            // 查询消息（只处理 PENDING 状态的消息，避免重复处理）
            com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO inbox = 
                webhookInboxService.findByMessageId(messageId);
            
            if (inbox == null || !InboxStatusEnum.PENDING.name().equals(inbox.getStatus())) {
                log.debug("[Webhook-处理] 消息不存在或已被处理: messageId={}, status={}", 
                    messageId, inbox != null ? inbox.getStatus() : "null");
                return;
            }
            
            // 标记为处理中（乐观锁：只有 PENDING 状态才能更新为 PROCESSING）
            int updated = webhookInboxService.markProcessingWithLock(messageId);
            
            if (updated == 0) {
                // 状态已被其他线程/进程更新，说明正在处理或已处理，跳过
                log.debug("[Webhook-处理] 消息已被其他线程处理，跳过: messageId={}", messageId);
                return;
            }
            
            log.debug("[Webhook-处理] 已标记为处理中，开始处理: messageId={}", messageId);
            
            // 重新查询最新状态的消息对象
            com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO processingInbox = 
                webhookInboxService.findByMessageId(messageId);
            if (processingInbox == null || !InboxStatusEnum.PROCESSING.name().equals(processingInbox.getStatus())) {
                log.debug("[Webhook-处理] 消息状态异常，跳过处理: messageId={}, status={}", 
                    messageId, processingInbox != null ? processingInbox.getStatus() : "null");
                return;
            }
            
            // 调用处理服务处理单条消息
            webhookProcessService.processSingle(processingInbox);
            
        } catch (Exception e) {
            log.error("[Webhook-处理] 异步处理消息失败: messageId={}", messageId, e);
            // 异步处理失败不影响主流程，定时任务会作为兜底机制重试
        }
    }
}

