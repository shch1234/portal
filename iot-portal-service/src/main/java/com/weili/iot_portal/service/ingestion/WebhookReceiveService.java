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

import static com.weili.iot_portal.service.device.util.DeviceLogContext.*;

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
        // 设置设备编号到 MDC，使日志能够显示设备编号
        setDeviceCode(request.getDeviceCode());
        
        try {
            log.debug("[Webhook-处理] 开始处理: messageId={}, category={}, eventType={}, deviceCode={}",
                        request.getMessageId(), category, eventType, request.getDeviceCode());

            // 1) 幂等性检查
            if (!checkIdempotency(request)) {
                log.debug("[Webhook-处理] [步骤1] 消息已处理，跳过: messageId={}", request.getMessageId());
                return;
            }

            // 2) 设备匹配和同步
            Optional<DeviceInfoDO> deviceOpt = matchAndSyncDevice(request);
            if (deviceOpt.isEmpty()) {
                log.debug("[Webhook-处理] [步骤2] 设备未匹配，停止处理: messageId={}, eventType={}, deviceCode={}",
                        request.getMessageId(), eventType, request.getDeviceCode());
                return;
            }
            DeviceInfoDO device = deviceOpt.get();

        // 3) 补充请求信息
        enrichRequest(request, category, eventType, device);

            // 4) 分类处理
            dispatchByCategory(category, request, device);

            log.debug("[Webhook-处理] 处理完成: messageId={}", request.getMessageId());
        } finally {
            // 清除设备编号 MDC，避免线程复用导致设备编号污染
            clearDeviceCode();
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
            log.debug("[Webhook-处理] [步骤1] 消息已处理，跳过: messageId={}", request.getMessageId());
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
            log.debug("[Webhook-处理] [步骤2] 设备未匹配，直接ACK: messageId={}, deviceCode={}, eventType={}",
                    request.getMessageId(), request.getDeviceCode(), request.getEventType());
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
     * 核心原则：根据Handler的处理策略决定是否走收件箱，而不是根据REALTIME/BUSINESS类别
     * </p>
     * <p>
     * 处理策略说明：
     * - BUSINESS_PERSISTENT: 走收件箱（保证可靠性，必须持久化）
     *   - 即使REALTIME类别，如果策略是BUSINESS_PERSISTENT，也要走收件箱
     *   - 原因：需要保证数据不丢失，支持重试（如DEVICE_STATE, DEVICE_ALARM等）
     * - REALTIME_DIRECT/REALTIME_WITH_PERSISTENCE: 直接处理（按能力处理，处理不了丢弃）
     *   - 不经过收件箱，减少数据库压力
     *   - 失败只记录日志，不重试（符合实时数据的低可靠性特性）
     *   - 原因：高频、低价值、可丢失的数据（如DEVICE_PROGRAM, DEVICE_AXIS等）
     * </p>
     * <p>
     * 行业最佳实践：
     * - 实时数据：按能力处理，处理不了直接丢弃，不持久化
     * - 业务数据：保证可靠性，必须持久化，支持重试
     * </p>
     *
     * @param request Webhook请求
     * @param device  设备信息
     */
    private void handleRealtimeCategory(WebhookRequest request, DeviceInfoDO device) {
        String finalEventType = request.getEventType();
        Optional<WebhookEventHandler> handlerOpt = handlerRegistry.resolve(finalEventType);

        if (handlerOpt.isPresent()) {
            WebhookEventHandler handler = handlerOpt.get();
            WebhookProcessingStrategy strategy = handler.getProcessingStrategy();

            log.debug("[Webhook-处理] [步骤3] 实时数据（有Handler），策略={}: messageId={}, eventType={}",
            strategy, request.getMessageId(), finalEventType);
            
            switch (strategy) {
                case BUSINESS_PERSISTENT -> {
                    // 优化：BUSINESS_PERSISTENT策略走收件箱，利用削峰能力
                    log.debug("[Webhook-处理] [步骤3] 实时数据（策略=BUSINESS_PERSISTENT），走收件箱削峰: messageId={}, eventType={}",
                            request.getMessageId(), finalEventType);
                    // 保存到收件箱（削峰）
                    webhookInboxService.saveToInbox(request);
                    // 立即异步处理（保证实时性）
                    if (asyncProcessEnabled) {
                        processMessageAsync(request.getMessageId());
                    }
                }
                case REALTIME_DIRECT, REALTIME_WITH_PERSISTENCE -> {
                    // 轻量级策略：直接处理，不经过收件箱
                    handleRealtimeHandler(handler, request, finalEventType, strategy);
                }
                default -> {
                    // 未知策略，降级为仅缓存
                    log.debug("[Webhook-处理] [步骤3] 未知处理策略，降级为仅缓存: strategy={}, messageId={}, eventType={}",
                            strategy, request.getMessageId(), finalEventType);
                    realtimeWebhookCacheService.cache(finalEventType, device.getDeviceCode(), request);
                }
            }
        } else {
            // 无 Handler，只缓存原始数据（轻量级处理）
            log.debug("[Webhook-处理] [步骤3] 实时数据（无Handler），仅缓存: messageId={}, eventType={}",
                    request.getMessageId(), finalEventType);
            realtimeWebhookCacheService.cache(finalEventType, device.getDeviceCode(), request);
        }
    }

    /**
     * 实时处理 Handler（统一处理所有 REALTIME 类别的事件）
     * <p>
     * 适用于：
     * - REALTIME_DIRECT: 只写Redis缓存的事件（DEVICE_PROGRAM, DEVICE_AXIS等）
     * - REALTIME_WITH_PERSISTENCE: REALTIME类别但需要写数据库（如DEVICE_TOOL需要写device_tool_compensation表）
     * - BUSINESS_PERSISTENT: 降级为直接处理，Handler内部有事务保证（如DEVICE_ALARM, DEVICE_TOOL_CHANGE等）
     * </p>
     * <p>
     * 注意：
     * - REALTIME事件处理失败不影响主流程，只记录日志
     * - 这是REALTIME事件的低可靠性策略：允许丢失，不重试
     * - Handler内部有@Transactional保证数据一致性（对于需要持久化的Handler）
     * </p>
     *
     * @param handler   事件处理器
     * @param request   Webhook请求
     * @param eventType 事件类型
     * @param strategy  处理策略
     */
    private void handleRealtimeHandler(WebhookEventHandler handler, WebhookRequest request,
                                      String eventType, WebhookProcessingStrategy strategy) {
        // 确保 MDC 中已设置设备编号（如果 handle 方法中已设置，这里会保持；如果未设置，这里补充设置）
        // 注意：这里不检查 MDC 是否已设置，直接设置即可（工具类内部会处理）
        setDeviceCode(request.getDeviceCode());
        
        try {
            Map<String, Object> eventData = request.getEventData();
            if (eventData == null || eventData.isEmpty()) {
                throw new IotPortalException(IotPortalErrorCode.EVENT_DATA_EMPTY);
            }
            handler.handleRealtime(request);

            if (log.isDebugEnabled()) {
                String strategyName = switch (strategy) {
                    case REALTIME_DIRECT -> "实时直接处理";
                    case REALTIME_WITH_PERSISTENCE -> "实时持久化处理";
                    case BUSINESS_PERSISTENT -> "实时直接处理（降级）";
                    default -> "实时处理";
                };
                log.debug("[Webhook-处理] [步骤3] {}完成: messageId={}, eventType={}",
                        strategyName, request.getMessageId(), eventType);
            }
        } catch (Exception e) {
            log.error("[Webhook-处理] [步骤3] 实时处理失败: messageId={}, eventType={}, strategy={}",
                    request.getMessageId(), eventType, strategy, e);
            // REALTIME事件处理失败不影响主流程，只记录日志
            // 这是REALTIME事件的低可靠性策略：允许丢失，不重试
        } finally {
            // 清除设备编号 MDC，避免线程复用导致设备编号污染
            clearDeviceCode();
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
        // 注意：@Async 方法会在新线程中执行，MDC 不会自动传递
        // 但这里调用的是 webhookProcessService.processSingle，它内部会设置 MDC
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
