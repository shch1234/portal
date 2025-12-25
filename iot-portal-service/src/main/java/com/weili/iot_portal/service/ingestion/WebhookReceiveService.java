package com.weili.iot_portal.service.ingestion;

import com.weili.iot_portal.common.enums.InboxStatusEnum;
import com.weili.iot_portal.common.enums.WebHookCategoryType;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.handler.registry.WebhookHandlerRegistry;
import com.weili.iot_portal.service.ingestion.support.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Webhook 接收服务
 * <p>
 * 职责：
 * 1. 接收和验证 Webhook 请求
 * 2. 幂等性检查
 * 3. 设备匹配和同步
 * 4. 根据分类分发到不同的处理流程
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookReceiveService {

    private final WebhookIdempotentService idempotentService;
    private final DeviceMatchingService deviceMatchingService;
    private final WebhookInboxService webhookInboxService;
    private final RealtimeWebhookCacheService realtimeWebhookCacheService;
    private final WebhookProcessService webhookProcessService;
    private final WebhookHandlerRegistry handlerRegistry;

    @Value("${webhook.inbox.async-process-enabled:true}")
    private boolean asyncProcessEnabled;

    /**
     * 处理 webhook 消息
     *
     * @param category  分类（BUSINESS/REALTIME）
     * @param eventType 事件类型
     * @param request   解析后的请求对象
     */
    public void handle(String category, String eventType, WebhookRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("[Webhook-处理] 开始处理: messageId={}, category={}, eventType={}, deviceCode={}",
                    request.getMessageId(), category, eventType, request.getDeviceCode());
        }

        // 1) 幂等性检查
        if (!checkIdempotency(request)) {
            return;
        }

        // 2) 设备匹配和同步
        Optional<DeviceInfoDO> deviceOpt = matchAndSyncDevice(request);
        if (deviceOpt.isEmpty()) {
            return;
        }
        DeviceInfoDO device = deviceOpt.get();

        // 3) 补充请求信息
        enrichRequest(request, category, eventType, device);

        // 4) 分类处理
        dispatchByCategory(category, request, device);

        if (log.isDebugEnabled()) {
            log.debug("[Webhook-处理] 处理完成: messageId={}", request.getMessageId());
        }
    }

    /**
     * 幂等性检查
     *
     * @param request Webhook请求
     * @return true 如果消息未处理过，false 如果已处理
     */
    private boolean checkIdempotency(WebhookRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("[Webhook-处理] [步骤1] 幂等性检查: messageId={}", request.getMessageId());
        }
        if (!idempotentService.tryConsume(request.getMessageId())) {
            log.info("[Webhook-处理] [步骤1] 消息已处理，跳过: messageId={}", request.getMessageId());
            return false;
        }
        if (log.isDebugEnabled()) {
            log.debug("[Webhook-处理] [步骤1] 幂等性检查通过，消息未处理过");
        }
        return true;
    }

    /**
     * 设备匹配和同步
     *
     * @param request Webhook请求
     * @return 设备信息，如果未匹配则返回空
     */
    private Optional<DeviceInfoDO> matchAndSyncDevice(WebhookRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("[Webhook-处理] [步骤2] 设备匹配: deviceCode={}", request.getDeviceCode());
        }
        Optional<DeviceInfoDO> deviceOpt = deviceMatchingService.match(request.getDeviceCode());
        if (deviceOpt.isEmpty()) {
            log.warn("[Webhook-处理] [步骤2] 设备未匹配，直接ACK: messageId={}, deviceCode={}",
                    request.getMessageId(), request.getDeviceCode());
            return Optional.empty();
        }

        DeviceInfoDO device = deviceOpt.get();
        if (log.isDebugEnabled()) {
            log.debug("[Webhook-处理] [步骤2] 设备匹配成功: deviceCode={}, deviceId={}, deviceInfoId={}",
                    device.getDeviceCode(), device.getTbDeviceId(), device.getId());
        }

        // 同步 TB 设备ID（如果需要）
        deviceMatchingService.syncTbDeviceIdIfNeeded(device, request.getDeviceId());

        return Optional.of(device);
    }

    /**
     * 补充请求信息
     * <p>
     * 补充 deviceId、category、eventType 等信息
     * </p>
     *
     * @param request   Webhook请求
     * @param category  分类
     * @param eventType 事件类型
     * @param device    设备信息
     */
    private void enrichRequest(WebhookRequest request, String category, String eventType, DeviceInfoDO device) {
        // 补充设备ID
        if (StringUtils.isBlank(request.getDeviceId())) {
            request.setDeviceId(device.getTbDeviceId());
            if (log.isDebugEnabled()) {
                log.debug("[Webhook-处理] [步骤2] 补充deviceId: {}", device.getTbDeviceId());
            }
        }

        // 设置分类
        request.setWebhookCategory(category);

        // 设置事件类型（优先使用请求体中的）
        if (StringUtils.isBlank(request.getEventType())) {
            request.setEventType(eventType);
            if (log.isDebugEnabled()) {
                log.debug("[Webhook-处理] [步骤2] 使用URL路径中的eventType: {}", eventType);
            }
        } else if (log.isDebugEnabled()) {
            log.debug("[Webhook-处理] [步骤2] 使用请求体中的eventType: {}, URL路径中的eventType: {}",
                    request.getEventType(), eventType);
        }
    }

    /**
     * 根据分类分发处理
     *
     * @param category 分类
     * @param request  Webhook请求
     * @param device   设备信息
     */
    private void dispatchByCategory(String category, WebhookRequest request, DeviceInfoDO device) {
        if (log.isDebugEnabled()) {
            log.debug("[Webhook-处理] [步骤3] 分类处理: category={}", category);
        }

        WebHookCategoryType categoryType;
        try {
            categoryType = WebHookCategoryType.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IotPortalException(IotPortalErrorCode.WEBHOOK_CATEGORY_NOT_SUPPORTED);
        }

        switch (categoryType) {
            case BUSINESS -> handleBusinessCategory(request, request.getEventType());
            case REALTIME -> handleRealtimeCategory(request, device);
            default -> throw new IotPortalException(IotPortalErrorCode.WEBHOOK_CATEGORY_NOT_SUPPORTED);
        }
    }

    /**
     * 处理 BUSINESS 类别的事件
     * <p>
     * BUSINESS 类别的事件统一走收件箱流程，支持持久化和重试
     * </p>
     *
     * @param request   Webhook请求
     * @param eventType 事件类型
     */
    private void handleBusinessCategory(WebhookRequest request, String eventType) {
        if (log.isDebugEnabled()) {
            log.debug("[Webhook-处理] [步骤3] 业务数据，保存到收件箱: messageId={}, eventType={}",
                    request.getMessageId(), eventType);
        }
        webhookInboxService.saveToInbox(request);

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
     * @param device  设备信息
     */
    private void handleRealtimeCategory(WebhookRequest request, DeviceInfoDO device) {
        String finalEventType = request.getEventType();
        Optional<WebhookEventHandler> handlerOpt = handlerRegistry.resolve(finalEventType);

        if (handlerOpt.isPresent()) {
            // 有 Handler，根据策略决定处理方式
            WebhookEventHandler handler = handlerOpt.get();
            WebhookProcessingStrategy strategy = handler.getProcessingStrategy();

            if (log.isDebugEnabled()) {
                log.debug("[Webhook-处理] [步骤3] 实时数据（有Handler），策略={}: messageId={}, eventType={}",
                        strategy, request.getMessageId(), finalEventType);
            }

            switch (strategy) {
                case REALTIME_DIRECT ->
                    // 实时直接处理：只写Redis，不持久化
                        handleRealtimeHandler(handler, request, finalEventType, strategy);
                case REALTIME_WITH_PERSISTENCE ->
                    // 实时但需持久化：直接处理，Handler内部有事务保证
                        handleRealtimeHandler(handler, request, finalEventType, strategy);
                case BUSINESS_PERSISTENT -> {
                    // 特殊情况：REALTIME类别但需要持久化保证（保持兼容性）
                    if (log.isDebugEnabled()) {
                        log.debug("[Webhook-处理] [步骤3] 实时数据（策略=BUSINESS_PERSISTENT），走收件箱流程: messageId={}, eventType={}",
                                request.getMessageId(), finalEventType);
                    }
                    webhookInboxService.saveToInbox(request);
                    if (asyncProcessEnabled) {
                        processMessageAsync(request.getMessageId());
                    }
                }
                default -> {
                    // 未知策略，降级为仅缓存
                    log.warn("[Webhook-处理] [步骤3] 未知处理策略，降级为仅缓存: strategy={}, messageId={}, eventType={}",
                            strategy, request.getMessageId(), finalEventType);
                    realtimeWebhookCacheService.cache(finalEventType, device.getDeviceCode(), request);
                }
            }
        } else {
            // 无 Handler，只缓存原始数据（轻量级处理）
            if (log.isDebugEnabled()) {
                log.debug("[Webhook-处理] [步骤3] 实时数据（无Handler），仅缓存: messageId={}, eventType={}",
                        request.getMessageId(), finalEventType);
            }
            realtimeWebhookCacheService.cache(finalEventType, device.getDeviceCode(), request);
        }
    }

    /**
     * 实时处理 Handler（统一处理 REALTIME_DIRECT 和 REALTIME_WITH_PERSISTENCE）
     * <p>
     * 适用于：
     * - REALTIME_DIRECT: 只写Redis缓存的事件（DEVICE_PROGRAM, DEVICE_AXIS等）
     * - REALTIME_WITH_PERSISTENCE: REALTIME类别但需要写数据库（如DEVICE_TOOL需要写device_tool_compensation表）
     * </p>
     *
     * @param handler   事件处理器
     * @param request   Webhook请求
     * @param eventType 事件类型
     * @param strategy  处理策略
     */
    private void handleRealtimeHandler(WebhookEventHandler handler, WebhookRequest request,
                                      String eventType, WebhookProcessingStrategy strategy) {
        try {
            Map<String, Object> eventData = request.getEventData();
            if (eventData == null || eventData.isEmpty()) {
                throw new IotPortalException(IotPortalErrorCode.EVENT_DATA_EMPTY);
            }
            handler.handleRealtime(request);

            if (log.isDebugEnabled()) {
                String strategyName = strategy == WebhookProcessingStrategy.REALTIME_DIRECT
                        ? "实时直接处理" : "实时持久化处理";
                log.debug("[Webhook-处理] [步骤3] {}完成: messageId={}, eventType={}",
                        strategyName, request.getMessageId(), eventType);
            }
        } catch (Exception e) {
            log.error("[Webhook-处理] [步骤3] 实时处理失败: messageId={}, eventType={}, strategy={}",
                    request.getMessageId(), eventType, strategy, e);
            // REALTIME事件处理失败不影响主流程，只记录日志
            // 这是REALTIME事件的低可靠性策略：允许丢失，不重试
        }
    }

    /**
     * 异步处理单条消息（实时处理）
     * <p>
     * 通过状态字段（PENDING -> PROCESSING）保证幂等性，避免重复处理
     * </p>
     *
     * @param messageId 消息ID
     */
    @Async
    public void processMessageAsync(String messageId) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("[Webhook-处理] 开始异步处理消息: messageId={}", messageId);
            }

            // 查询消息（只处理 PENDING 状态的消息，避免重复处理）
            WebhookInboxDO inbox = webhookInboxService.findByMessageId(messageId);

            if (inbox == null || !InboxStatusEnum.PENDING.name().equals(inbox.getStatus())) {
                if (log.isDebugEnabled()) {
                    log.debug("[Webhook-处理] 消息不存在或已被处理: messageId={}, status={}",
                            messageId, inbox != null ? inbox.getStatus() : "null");
                }
                return;
            }

            // 标记为处理中（乐观锁：只有 PENDING 状态才能更新为 PROCESSING）
            int updated = webhookInboxService.markProcessingWithLock(messageId);

            if (updated == 0) {
                // 状态已被其他线程/进程更新，说明正在处理或已处理，跳过
                if (log.isDebugEnabled()) {
                    log.debug("[Webhook-处理] 消息已被其他线程处理，跳过: messageId={}", messageId);
                }
                return;
            }

            if (log.isDebugEnabled()) {
                log.debug("[Webhook-处理] 已标记为处理中，开始处理: messageId={}", messageId);
            }

            // 重新查询最新状态的消息对象
            WebhookInboxDO processingInbox = webhookInboxService.findByMessageId(messageId);
            if (processingInbox == null || !InboxStatusEnum.PROCESSING.name().equals(processingInbox.getStatus())) {
                if (log.isDebugEnabled()) {
                    log.debug("[Webhook-处理] 消息状态异常，跳过处理: messageId={}, status={}",
                            messageId, processingInbox != null ? processingInbox.getStatus() : "null");
                }
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
