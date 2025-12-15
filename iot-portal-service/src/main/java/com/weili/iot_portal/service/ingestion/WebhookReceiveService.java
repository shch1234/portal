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
import com.weili.iot_portal.service.ingestion.handler.registry.WebhookHandlerRegistry;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
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
    private WebhookIdempotentService idempotentService;

    @Autowired
    private DeviceMatchingService deviceMatchingService;

    @Autowired
    private WebhookInboxService webhookInboxService;

    @Autowired
    private RealtimeWebhookCacheService realtimeWebhookCacheService;
    
    @Autowired
    private WebhookProcessService webhookProcessService;
    
    @Autowired
    private WebhookHandlerRegistry handlerRegistry;
    
    @Value("${webhook.inbox.async-process-enabled:true}")
    private boolean asyncProcessEnabled;

    /**
     * 处理 webhook 消息
     * 
     * @param category 分类（BUSINESS/REALTIME）
     * @param eventType 事件类型
     * @param rawBody 原始请求体
     * @param request 解析后的请求对象
     */
    public void handle(String category,
                       String eventType,
                       String rawBody,
                       WebhookRequest request) {
        log.debug("[Webhook-处理] ====== 开始处理Webhook消息 ======");
        log.debug("[Webhook-处理] messageId={}, category={}, eventType={}, deviceCode={}", 
            request.getMessageId(), category, eventType, request.getDeviceCode());
        
        // 1) 幂等性检查
        log.debug("[Webhook-处理] [步骤1] 幂等性检查: messageId={}", request.getMessageId());
        if (!idempotentService.tryConsume(request.getMessageId())) {
            log.info("[Webhook-处理] [步骤1] 消息已处理，跳过: messageId={}", request.getMessageId());
            return;
        }
        log.debug("[Webhook-处理] [步骤1] 幂等性检查通过，消息未处理过");
        
        // 2) 设备匹配
        log.debug("[Webhook-处理] [步骤2] 设备匹配: deviceCode={}", request.getDeviceCode());
        Optional<DeviceInfoDO> deviceOpt = deviceMatchingService.match(request.getDeviceCode());
        if (deviceOpt.isEmpty()) {
            log.warn("[Webhook-处理] [步骤2] 设备未匹配，直接ACK: messageId={}, deviceCode={}", 
                request.getMessageId(), request.getDeviceCode());
            return;
        }
        DeviceInfoDO device = deviceOpt.get();
        log.debug("[Webhook-处理] [步骤2] 设备匹配成功: deviceCode={}, deviceId={}, deviceInfoId={}", 
            device.getDeviceCode(), device.getTbDeviceId(), device.getId());
        
        // 补充设备/租户信息
        if (StringUtils.isBlank(request.getDeviceId())) {
            request.setDeviceId(device.getTbDeviceId());
            log.debug("[Webhook-处理] [步骤2] 补充deviceId: {}", device.getTbDeviceId());
        }
        request.setWebhookCategory(category);
        
        // 优先使用请求体中的eventType（更准确），如果为空则使用URL路径中的eventType
        if (StringUtils.isBlank(request.getEventType())) {
            request.setEventType(eventType);
            log.debug("[Webhook-处理] [步骤2] 使用URL路径中的eventType: {}", eventType);
        } else {
            log.debug("[Webhook-处理] [步骤2] 使用请求体中的eventType: {}, URL路径中的eventType: {}", 
                request.getEventType(), eventType);
        }
        log.debug("[Webhook-处理] [步骤2] 设置category和eventType: category={}, eventType={}", 
            category, request.getEventType());

        // 3) 分类处理
        log.debug("[Webhook-处理] [步骤3] 分类处理: category={}", category);
        if (WebHookCategoryType.BUSINESS.name().equalsIgnoreCase(category)) {
            // BUSINESS 类别：业务数据，需要持久化保证
            handleBusinessCategory(request, eventType);
        } else if (WebHookCategoryType.REALTIME.name().equalsIgnoreCase(category)) {
            // REALTIME 类别：实时数据，根据Handler的策略决定处理方式
            handleRealtimeCategory(request, eventType, device);
        } else {
            throw new IotPortalException(IotPortalErrorCode.WEBHOOK_CATEGORY_NOT_SUPPORTED);
        }
        
        log.debug("[Webhook-处理] ====== Webhook消息处理完成 ====== messageId={}", request.getMessageId());
    }

    /**
     * 处理 BUSINESS 类别的事件
     * <p>
     * BUSINESS 类别的事件统一走收件箱流程，支持持久化和重试
     * </p>
     *
     * @param request Webhook请求
     * @param eventType 事件类型
     */
    private void handleBusinessCategory(WebhookRequest request, String eventType) {
        log.debug("[Webhook-处理] [步骤3] 业务数据，保存到收件箱: messageId={}, eventType={}", 
            request.getMessageId(), eventType);
        webhookInboxService.saveToInbox(request);
        log.debug("[Webhook-处理] [步骤3] 业务数据已保存到收件箱");
        
        // 立即异步处理（实时处理）
        if (asyncProcessEnabled) {
            processMessageAsync(request.getMessageId());
        }
    }

    /**
     * 处理 REALTIME 类别的事件
     * <p>
     * REALTIME 类别的事件根据Handler的处理策略决定处理方式：
     * - REALTIME_DIRECT: 直接调用Handler，不持久化
     * - REALTIME_WITH_PERSISTENCE: 直接调用Handler，Handler内部有事务保证
     * - BUSINESS_PERSISTENT: 特殊情况，走收件箱流程（保持兼容性）
     * - 无Handler: 仅缓存原始数据
     * </p>
     *
     * @param request Webhook请求
     * @param eventType 事件类型
     * @param device 设备信息
     */
    private void handleRealtimeCategory(WebhookRequest request, String eventType, DeviceInfoDO device) {
        String finalEventType = request.getEventType();
        Optional<WebhookEventHandler> handlerOpt = handlerRegistry.resolve(finalEventType);
        
        if (handlerOpt.isPresent()) {
            // 有 Handler，根据策略决定处理方式
            WebhookEventHandler handler = handlerOpt.get();
            WebhookProcessingStrategy strategy = handler.getProcessingStrategy();
            
            log.debug("[Webhook-处理] [步骤3] 实时数据（有Handler），策略={}: messageId={}, eventType={}", 
                strategy, request.getMessageId(), finalEventType);
            
            switch (strategy) {
                case REALTIME_DIRECT:
                    // 实时直接处理：只写Redis，不持久化
                    handleRealtimeDirect(handler, request, finalEventType);
                    break;
                    
                case REALTIME_WITH_PERSISTENCE:
                    // 实时但需持久化：直接处理，Handler内部有事务保证
                    handleRealtimeWithPersistence(handler, request, finalEventType);
                    break;
                    
                case BUSINESS_PERSISTENT:
                    // 特殊情况：REALTIME类别但需要持久化保证（保持兼容性）
                    log.debug("[Webhook-处理] [步骤3] 实时数据（策略=BUSINESS_PERSISTENT），走收件箱流程: messageId={}, eventType={}", 
                        request.getMessageId(), finalEventType);
                    webhookInboxService.saveToInbox(request);
                    if (asyncProcessEnabled) {
                        processMessageAsync(request.getMessageId());
                    }
                    break;
                    
                default:
                    // 未知策略，降级为仅缓存
                    log.warn("[Webhook-处理] [步骤3] 未知处理策略，降级为仅缓存: strategy={}, messageId={}, eventType={}", 
                        strategy, request.getMessageId(), finalEventType);
                    realtimeWebhookCacheService.cache(finalEventType, device.getDeviceCode(), request);
            }
        } else {
            // 无 Handler，只缓存原始数据（轻量级处理）
            log.debug("[Webhook-处理] [步骤3] 实时数据（无Handler），仅缓存: messageId={}, eventType={}", 
                request.getMessageId(), finalEventType);
            realtimeWebhookCacheService.cache(finalEventType, device.getDeviceCode(), request);
            log.debug("[Webhook-处理] [步骤3] 实时数据已缓存");
        }
    }

    /**
     * 实时直接处理：不持久化，只写Redis
     * <p>
     * 适用于：只写Redis缓存的事件（DEVICE_PROGRAM, DEVICE_AXIS等）
     * </p>
     *
     * @param handler 事件处理器
     * @param request Webhook请求
     * @param eventType 事件类型
     */
    private void handleRealtimeDirect(WebhookEventHandler handler, WebhookRequest request, String eventType) {
        try {
            // 优先使用 handleRealtime 方法（更清晰的接口）
            // 如果Handler实现了 handleRealtime，则使用；否则回退到 handle(null, request)
            handler.handleRealtime(request);
            
            log.debug("[Webhook-处理] [步骤3] 实时直接处理完成: messageId={}, eventType={}", 
                request.getMessageId(), eventType);
        } catch (Exception e) {
            log.error("[Webhook-处理] [步骤3] 实时直接处理失败: messageId={}, eventType={}", 
                request.getMessageId(), eventType, e);
            // REALTIME事件处理失败不影响主流程，只记录日志
            // 这是REALTIME事件的低可靠性策略：允许丢失，不重试
        }
    }

    /**
     * 实时但需持久化处理：直接处理，Handler内部有事务保证
     * <p>
     * 适用于：REALTIME类别但需要写数据库（如DEVICE_TOOL需要写device_tool_compensation表）
     * </p>
     *
     * @param handler 事件处理器
     * @param request Webhook请求
     * @param eventType 事件类型
     */
    private void handleRealtimeWithPersistence(WebhookEventHandler handler, WebhookRequest request, String eventType) {
        try {
            // 优先使用 handleRealtime 方法（更清晰的接口）
            // Handler内部有@Transactional保证数据一致性
            handler.handleRealtime(request);
            
            log.debug("[Webhook-处理] [步骤3] 实时持久化处理完成: messageId={}, eventType={}", 
                request.getMessageId(), eventType);
        } catch (Exception e) {
            log.error("[Webhook-处理] [步骤3] 实时持久化处理失败: messageId={}, eventType={}", 
                request.getMessageId(), eventType, e);
            // REALTIME事件处理失败不影响主流程，只记录日志
            // 虽然需要持久化，但保持REALTIME的低可靠性特性：允许丢失，不重试
        }
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

