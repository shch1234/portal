package com.weili.iot_portal.service.ingestion.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.ProcessResult;
import com.weili.iot_portal.service.ingestion.WebhookInboxProcessor;
import com.weili.iot_portal.service.ingestion.support.WebhookInboxService;
import com.weili.iot_portal.service.ingestion.support.WebhookProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Webhook 收件箱批处理实现
 * <p>
 * 优化点：
 * 1. 减少重复查询：使用已查询的对象，避免重复查询
 * 2. 超时控制：为单条消息处理添加超时控制
 * 3. 错误处理：细化错误分类和统计
 * 4. 并行处理：支持并行处理（可选，通过配置开启）
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookInboxProcessorImpl implements WebhookInboxProcessor {

    private final WebhookInboxService inboxService;
    private final WebhookProcessService webhookProcessService;

    /**
     * 是否启用并行处理
     * 支持 Apollo 配置，默认值：false
     */
    @Value("${webhook.inbox.parallel-enabled:false}")
    private boolean parallelEnabled;

    /**
     * 并行处理线程数（默认：CPU核心数 * 2）
     * 支持 Apollo 配置，默认值：0（表示自动计算）
     */
    @Value("${webhook.inbox.parallel-threads:0}")
    private int parallelThreads;

    /**
     * 单条消息处理超时时间（秒）
     * 支持 Apollo 配置，默认值：30
     */
    @Value("${webhook.inbox.message-timeout-seconds:30}")
    private int messageTimeoutSecondsRaw;

    /**
     * 批量处理超时时间（分钟）
     * 支持 Apollo 配置，默认值：5
     */
    @Value("${webhook.inbox.batch-timeout-minutes:5}")
    private int batchTimeoutMinutesRaw;

    /**
     * 是否启用动态批量大小
     * 支持 Apollo 配置，默认值：false
     */
    @Value("${webhook.inbox.dynamic-batch-size-enabled:false}")
    private boolean dynamicBatchSizeEnabled;

    /**
     * 最大批量大小
     * 支持 Apollo 配置，默认值：500
     */
    @Value("${webhook.inbox.max-batch-size:500}")
    private int maxBatchSizeRaw;
    
    /**
     * 验证后的配置值（经过验证和修正）
     */
    private int messageTimeoutSeconds;
    private int batchTimeoutMinutes;
    private int maxBatchSize;
    
    /**
     * 设备线程池过期时间（小时）
     * Apollo配置：webhook.inbox.device-executor-expire-hours
     * 默认值：1（1小时后自动清理不活跃的设备线程池）
     */
    @Value("${webhook.inbox.device-executor-expire-hours:1}")
    private int deviceExecutorExpireHours;

    /**
     * 设备线程池最大数量
     * Apollo配置：webhook.inbox.device-executor-max-size
     * 默认值：1000（最多为1000个设备创建专用线程池）
     */
    @Value("${webhook.inbox.device-executor-max-size:1000}")
    private int deviceExecutorMaxSize;

    /**
     * 设备专用的单线程线程池缓存
     * Key: 设备代码（deviceCode）
     * Value: 该设备专用的单线程线程池
     * <p>
     * 使用Caffeine缓存，自动清理不活跃的设备线程池，避免内存泄漏
     * 与 WebhookReceiveService 使用相同的设备分片机制，确保同一设备的消息串行处理
     * </p>
     */
    private Cache<String, ExecutorService> deviceExecutorsCache;
    
    /**
     * 初始化配置验证（确保配置值合理，防止 Apollo 配置错误导致报错）
     */
    @PostConstruct
    private void validateConfig() {
        // 验证并修正超时时间
        if (messageTimeoutSecondsRaw <= 0) {
            log.warn("[Webhook-Processor] message-timeout-seconds 配置无效: {}，使用默认值: 30", messageTimeoutSecondsRaw);
            messageTimeoutSeconds = 30;
        } else if (messageTimeoutSecondsRaw > 300) {
            log.warn("[Webhook-Processor] message-timeout-seconds 配置过大: {}，限制为: 300", messageTimeoutSecondsRaw);
            messageTimeoutSeconds = 300;
        } else {
            messageTimeoutSeconds = messageTimeoutSecondsRaw;
        }
        
        if (batchTimeoutMinutesRaw <= 0) {
            log.warn("[Webhook-Processor] batch-timeout-minutes 配置无效: {}，使用默认值: 5", batchTimeoutMinutesRaw);
            batchTimeoutMinutes = 5;
        } else if (batchTimeoutMinutesRaw > 60) {
            log.warn("[Webhook-Processor] batch-timeout-minutes 配置过大: {}，限制为: 60", batchTimeoutMinutesRaw);
            batchTimeoutMinutes = 60;
        } else {
            batchTimeoutMinutes = batchTimeoutMinutesRaw;
        }
        
        // 验证并修正批量大小
        if (maxBatchSizeRaw <= 0) {
            log.warn("[Webhook-Processor] max-batch-size 配置无效: {}，使用默认值: 500", maxBatchSizeRaw);
            maxBatchSize = 500;
        } else if (maxBatchSizeRaw > 2000) {
            log.warn("[Webhook-Processor] max-batch-size 配置过大: {}，限制为: 2000", maxBatchSizeRaw);
            maxBatchSize = 2000;
        } else {
            maxBatchSize = maxBatchSizeRaw;
        }
        
        // 验证并行线程数
        int validatedParallelThreads = parallelThreads;
        if (parallelThreads < 0) {
            log.warn("[Webhook-Processor] parallel-threads 配置无效: {}，使用自动计算", parallelThreads);
            validatedParallelThreads = 0;
        } else if (parallelThreads > 100) {
            log.warn("[Webhook-Processor] parallel-threads 配置过大: {}，限制为: 100", parallelThreads);
            validatedParallelThreads = 100;
        }
        // 注意：parallelThreads 字段不能修改，在 getExecutorService() 中会进行验证
        
        log.info("[Webhook-Processor] 配置验证完成: parallelEnabled={}, parallelThreads={}, messageTimeoutSeconds={}, " +
                "batchTimeoutMinutes={}, dynamicBatchSizeEnabled={}, maxBatchSize={}",
                parallelEnabled, validatedParallelThreads, messageTimeoutSeconds, batchTimeoutMinutes, 
                dynamicBatchSizeEnabled, maxBatchSize);
        
        // 初始化设备线程池缓存（用于设备分片，确保同一设备的消息串行处理）
        initDeviceExecutorsCache();
    }

    /**
     * 初始化设备线程池缓存
     * <p>
     * 与 WebhookReceiveService 使用相同的设备分片机制，确保同一设备的消息串行处理
     * </p>
     */
    private void initDeviceExecutorsCache() {
        deviceExecutorsCache = Caffeine.newBuilder()
                .expireAfterAccess(deviceExecutorExpireHours, TimeUnit.HOURS)
                .maximumSize(deviceExecutorMaxSize)
                .removalListener((key, value, cause) -> {
                    if (value instanceof ExecutorService) {
                        ExecutorService executor = (ExecutorService) value;
                        log.debug("[Webhook-Processor] 清理设备线程池: deviceCode={}, cause={}", key, cause);
                        executor.shutdown();
                        try {
                            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                                log.warn("[Webhook-Processor] 设备线程池未能完全关闭，强制关闭: deviceCode={}", key);
                                executor.shutdownNow();
                            }
                        } catch (InterruptedException e) {
                            log.warn("[Webhook-Processor] 设备线程池关闭被中断: deviceCode={}", key, e);
                            executor.shutdownNow();
                            Thread.currentThread().interrupt();
                        }
                    }
                })
                .build();
        log.info("[Webhook-Processor] 设备线程池缓存初始化完成: expireHours={}, maxSize={}", 
                deviceExecutorExpireHours, deviceExecutorMaxSize);
    }

    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    private void destroyDeviceExecutorsCache() {
        if (deviceExecutorsCache != null) {
            log.info("[Webhook-Processor] 关闭所有设备线程池");
            deviceExecutorsCache.asMap().forEach((deviceCode, executor) -> {
                if (executor != null) {
                    executor.shutdown();
                    try {
                        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                            executor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        executor.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                }
            });
            deviceExecutorsCache.invalidateAll();
        }
    }

    /**
     * 获取或创建设备专用的单线程线程池
     * <p>
     * 与 WebhookReceiveService 使用相同的设备分片机制，确保同一设备的消息串行处理
     * </p>
     *
     * @param deviceCode 设备代码
     * @return 设备专用的单线程线程池
     */
    private ExecutorService getOrCreateDeviceExecutor(String deviceCode) {
        final String finalDeviceCode = StringUtils.isBlank(deviceCode) ? "unknown" : deviceCode;
        return deviceExecutorsCache.get(finalDeviceCode, k -> {
            log.debug("[Webhook-Processor] 创建设备专用线程池: deviceCode={}", finalDeviceCode);
            return Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "webhook-device-" + finalDeviceCode);
                t.setDaemon(true);
                return t;
            });
        });
    }

    /**
     * 并行处理线程池（懒加载）
     */
    private ExecutorService executorService;
    
    /**
     * 超时控制线程池（用于串行处理模式的超时控制，懒加载）
     */
    private ExecutorService timeoutExecutorService;

    /**
     * 获取或创建并行处理线程池
     */
    private ExecutorService getExecutorService() {
        if (executorService == null) {
            synchronized (this) {
                if (executorService == null) {
                    // 验证并修正线程数（防止 Apollo 配置错误）
                    int threads = parallelThreads > 0 
                        ? Math.min(Math.max(parallelThreads, 1), 100)  // 限制在 1-100 之间
                        : Runtime.getRuntime().availableProcessors() * 2;
                    executorService = Executors.newFixedThreadPool(threads, r -> {
                        Thread t = new Thread(r, "webhook-processor-" + System.currentTimeMillis());
                        t.setDaemon(true);
                        return t;
                    });
                    log.info("[Webhook-Processor] 创建并行处理线程池: threads={} (配置值: {})", threads, parallelThreads);
                }
            }
        }
        return executorService;
    }
    
    /**
     * 获取或创建超时控制线程池（用于串行处理模式的超时控制）
     */
    private ExecutorService getTimeoutExecutorService() {
        if (timeoutExecutorService == null) {
            synchronized (this) {
                if (timeoutExecutorService == null) {
                    // 使用单线程池用于超时控制
                    timeoutExecutorService = Executors.newCachedThreadPool(r -> {
                        Thread t = new Thread(r, "webhook-timeout-" + System.currentTimeMillis());
                        t.setDaemon(true);
                        return t;
                    });
                    log.debug("[Webhook-Processor] 创建超时控制线程池");
                }
            }
        }
        return timeoutExecutorService;
    }

    @Override
    public ProcessResult processBatch() {
        long batchStartTime = System.currentTimeMillis();
        log.debug("[Webhook-Processor] ====== 开始批量处理收件箱消息 ======");
        
        // 动态批量大小：根据待处理消息数量动态调整批量大小
        int dynamicBatchSize = calculateDynamicBatchSize();
        List<WebhookInboxDO> inboxList = inboxService.fetchDue(dynamicBatchSize);
        log.debug("[Webhook-Processor] 查询到待处理消息数: {}, 并行处理: {}, 批量大小: {}", 
                inboxList.size(), parallelEnabled, dynamicBatchSize);

        if (inboxList.isEmpty()) {
            return ProcessResult.empty();
        }

        // 根据配置选择串行或并行处理
        if (parallelEnabled) {
            return processBatchParallel(inboxList, batchStartTime);
        } else {
            return processBatchSequential(inboxList, batchStartTime);
        }
    }

    /**
     * 计算动态批量大小（根据待处理消息数量）
     */
    private int calculateDynamicBatchSize() {
        if (!dynamicBatchSizeEnabled) {
            // 使用默认批量大小
            return inboxService.getBatchSize();
        }

        // 查询待处理消息总数
        Map<String, Long> stats = inboxService.getStatistics();
        long pendingCount = stats.getOrDefault("PENDING", 0L);
        long failedCount = stats.getOrDefault("FAILED", 0L);
        long totalPending = pendingCount + failedCount;

        // 根据消息数量动态调整批量大小
        int defaultBatchSize = inboxService.getBatchSize();
        if (totalPending < 100) {
            return defaultBatchSize; // 消息少，使用默认大小
        } else if (totalPending < 1000) {
            return Math.min(defaultBatchSize * 2, maxBatchSize); // 消息中等，增加批量大小
        } else {
            return maxBatchSize; // 消息多，使用最大批量大小
        }
    }

    /**
     * 串行处理（原有逻辑，优化后）
     */
    private ProcessResult processBatchSequential(List<WebhookInboxDO> inboxList, long batchStartTime) {
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger skip = new AtomicInteger(0);
        AtomicInteger error = new AtomicInteger(0);

        for (WebhookInboxDO inbox : inboxList) {
            // 串行处理模式：使用设备分片机制
            processSingleMessage(inbox, success, skip, error, true);
        }

        long batchCost = System.currentTimeMillis() - batchStartTime;
        log.info("[Webhook-Processor] ====== 批量处理完成（串行） ====== 成功: {}, 跳过: {}, 失败: {}, 总数: {}, 总耗时: {}ms",
                success.get(), skip.get(), error.get(), inboxList.size(), batchCost);
        return new ProcessResult(success.get(), skip.get(), error.get());
    }

    /**
     * 并行处理（优化：按设备ID分片）
     * <p>
     * 优化说明（阶段2）：
     * 1. 按设备ID（deviceCode）分组，同一设备的消息串行处理，避免锁竞争
     * 2. 不同设备的消息并行处理，提高吞吐量
     * 3. 预期效果：锁竞争减少80-90%，处理吞吐量提升50-100%
     * </p>
     */
    private ProcessResult processBatchParallel(List<WebhookInboxDO> inboxList, long batchStartTime) {
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger skip = new AtomicInteger(0);
        AtomicInteger error = new AtomicInteger(0);

        // 按设备ID（deviceCode）分组
        Map<String, List<WebhookInboxDO>> deviceGroups = inboxList.stream()
                .collect(Collectors.groupingBy(
                        inbox -> inbox.getDeviceCode() != null ? inbox.getDeviceCode() : "unknown",
                        Collectors.toList()
                ));

        log.debug("[Webhook-Processor] 按设备分片: 总消息数={}, 设备数={}, 平均每设备消息数={}",
                inboxList.size(), deviceGroups.size(), 
                deviceGroups.size() > 0 ? inboxList.size() / deviceGroups.size() : 0);

        ExecutorService executor = getExecutorService();
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        // 为每个设备创建一个任务，同一设备内的消息串行处理
        CompletableFuture<?>[] futures = deviceGroups.entrySet().stream()
                .map(entry -> {
                    String deviceCode = entry.getKey();
                    List<WebhookInboxDO> deviceMessages = entry.getValue();
                    
                    return CompletableFuture.runAsync(() -> {
                        // 在异步线程中恢复 MDC 上下文
                        if (mdcContext != null && !mdcContext.isEmpty()) {
                            MDC.setContextMap(mdcContext);
                        }
                        try {
                            // 同一设备的消息串行处理，避免锁竞争
                            // 注意：并行处理模式中已按设备分组，不需要设备分片（传递 false）
                            for (WebhookInboxDO inbox : deviceMessages) {
                                processSingleMessage(inbox, success, skip, error, false);
                            }
                        } finally {
                            // 清除 MDC，避免线程复用导致设备编号污染
                            MDC.clear();
                        }
                    }, executor);
                })
                .toArray(CompletableFuture[]::new);

        // 等待所有任务完成（设置超时）
        try {
            CompletableFuture.allOf(futures).get(batchTimeoutMinutes, TimeUnit.MINUTES);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("[Webhook-Processor] 批量处理超时（{}分钟），部分消息可能未处理完成", batchTimeoutMinutes);
        } catch (Exception e) {
            log.error("[Webhook-Processor] 批量处理异常", e);
        }

        long batchCost = System.currentTimeMillis() - batchStartTime;
        log.info("[Webhook-Processor] ====== 批量处理完成（并行-按设备分片） ====== 成功: {}, 跳过: {}, 失败: {}, 总数: {}, 设备数: {}, 总耗时: {}ms",
                success.get(), skip.get(), error.get(), inboxList.size(), deviceGroups.size(), batchCost);
        return new ProcessResult(success.get(), skip.get(), error.get());
    }

    /**
     * 处理单条消息（优化后：减少重复查询、超时控制、错误分类、设备分片）
     * <p>
     * 优化：使用设备分片机制，确保同一设备的消息串行处理，避免锁竞争
     * </p>
     *
     * @param inbox 消息对象
     * @param success 成功计数器
     * @param skip 跳过计数器
     * @param error 错误计数器
     * @param useDeviceSharding 是否使用设备分片（并行处理模式中已按设备分组，不需要设备分片）
     */
    private void processSingleMessage(WebhookInboxDO inbox, 
                                     AtomicInteger success, 
                                     AtomicInteger skip, 
                                     AtomicInteger error,
                                     boolean useDeviceSharding) {
        try {
            log.debug("[Webhook-Processor] ====== 开始处理单条消息 ======");
            log.debug("[Webhook-Processor] messageId={}, eventType={}, deviceCode={}, status={}, processCount={}",
                    inbox.getMessageId(), inbox.getEventType(), inbox.getDeviceCode(),
                    inbox.getStatus(), inbox.getProcessCount());

            // 使用乐观锁标记为处理中，如果失败说明已被其他线程处理，跳过
            boolean marked = inboxService.markProcessing(inbox);
            if (!marked) {
                log.debug("[Webhook-Processor] 消息已被其他线程处理，跳过: messageId={}", inbox.getMessageId());
                skip.incrementAndGet();
                return;
            }
            log.debug("[Webhook-Processor] 已标记为处理中状态");

            // 优化：使用已更新的 inbox 对象（markProcessing 已更新状态），避免重复查询
            // 如果状态已更新为 PROCESSING，直接使用，否则重新查询
            WebhookInboxDO processingInbox = inbox;
            if (!"PROCESSING".equals(inbox.getStatus())) {
                // 防御性检查：如果状态未更新，重新查询
                processingInbox = inboxService.findByMessageId(inbox.getMessageId());
                if (processingInbox == null || !"PROCESSING".equals(processingInbox.getStatus())) {
                    log.debug("[Webhook-Processor] 消息状态异常，跳过处理: messageId={}, status={}",
                            inbox.getMessageId(), processingInbox != null ? processingInbox.getStatus() : "null");
                    skip.incrementAndGet();
                    return;
                }
            }

            // 创建 final 变量，以便在 lambda 表达式中使用
            final WebhookInboxDO finalProcessingInbox = processingInbox;

            // 根据是否使用设备分片选择不同的执行方式
            if (useDeviceSharding) {
                // 串行处理模式：使用设备分片机制，确保同一设备的消息串行处理
                // 获取设备代码，用于设备分片
                String deviceCode = finalProcessingInbox.getDeviceCode();
                final String finalDeviceCode = StringUtils.isBlank(deviceCode) ? "unknown" : deviceCode;
                
                // 获取或创建设备专用的单线程线程池（保证同一设备的消息串行处理）
                ExecutorService deviceExecutor = getOrCreateDeviceExecutor(finalDeviceCode);
                
                // 获取当前线程的 MDC 上下文，以便在异步执行时传递
                Map<String, String> mdcContext = MDC.getCopyOfContextMap();
                
                // 在设备专用线程池中执行处理逻辑（使用 CompletableFuture 实现超时控制）
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    // 在异步线程中恢复 MDC 上下文
                    if (mdcContext != null && !mdcContext.isEmpty()) {
                        MDC.setContextMap(mdcContext);
                    }
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug("[Webhook-Processor] 在设备专用线程池中处理: messageId={}, deviceCode={}", 
                                    finalProcessingInbox.getMessageId(), finalDeviceCode);
                        }
                        // processSingle 内部会设置设备编号到 MDC，覆盖之前的值
                        // 优化：传递 false，表示不强制重新查询，使用已查询的对象
                        webhookProcessService.processSingle(finalProcessingInbox, false);
                    } finally {
                        // 清除 MDC，避免线程复用导致设备编号污染
                        MDC.clear();
                    }
                }, deviceExecutor);

                // 等待处理完成，设置超时时间
                future.get(messageTimeoutSeconds, TimeUnit.SECONDS);
            } else {
                // 并行处理模式：已经按设备分组，同一设备的消息在同一线程中串行处理，不需要设备分片
                // 直接调用处理逻辑（不使用设备分片线程池，避免双重串行化）
                webhookProcessService.processSingle(finalProcessingInbox, false);
            }
            
            success.incrementAndGet();

        } catch (TimeoutException e) {
            // 超时异常：记录错误并标记为失败
            log.error("[Webhook-Processor] 消息处理超时: messageId={}, eventType={}, timeout={}秒",
                    inbox.getMessageId(), inbox.getEventType(), messageTimeoutSeconds);
            error.incrementAndGet();
            // 标记为失败，设置超时错误信息
            try {
                WebhookInboxDO latestInbox = inboxService.findByMessageId(inbox.getMessageId());
                if (latestInbox != null && "PROCESSING".equals(latestInbox.getStatus())) {
                    inboxService.markFailed(latestInbox, "处理超时（超过" + messageTimeoutSeconds + "秒）");
                }
            } catch (Exception ex) {
                log.error("[Webhook-Processor] 标记超时失败异常: messageId={}", inbox.getMessageId(), ex);
            }
        } catch (IotPortalException e) {
            // 业务异常：记录错误类型
            log.error("[Webhook-Processor] 业务异常: messageId={}, eventType={}, errorCode={}",
                    inbox.getMessageId(), inbox.getEventType(), e.getCode(), e);
            error.incrementAndGet();
            // 具体状态标记、失败日志等逻辑在 WebhookProcessService 中处理
        } catch (Exception ex) {
            // 系统异常：记录异常类型
            log.error("[Webhook-Processor] 系统异常: messageId={}, eventType={}, exception={}",
                    inbox.getMessageId(), inbox.getEventType(), ex.getClass().getSimpleName(), ex);
            error.incrementAndGet();
            // 具体状态标记、失败日志等逻辑在 WebhookProcessService 中处理
        }
    }
}

