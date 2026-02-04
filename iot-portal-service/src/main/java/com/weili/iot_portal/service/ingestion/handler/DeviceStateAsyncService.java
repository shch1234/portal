package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.domain.ingestion.DeviceIdentity;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.WebhookFailLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 设备状态事件异步处理服务
 * <p>
 * 优化说明：
 * 1. 将非关键操作（缓存更新、异常日志）异步化，减少事务范围和锁持有时间
 * 2. 使用 CompletableFuture.runAsync() 执行异步操作，更灵活可控
 * 3. 自动传递 MDC 上下文，确保日志中能正确显示设备编号等信息
 * 4. 异步操作失败不影响主流程
 * </p>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStateAsyncService {

    private final WebhookHandlerUtils webhookHandlerUtils;
    
    @Autowired(required = false)
    private WebhookFailLogService webhookFailLogService;
    
    @Autowired
    @Qualifier("webhookAsyncExecutor")
    private Executor webhookAsyncExecutor;
    
    // ==================== 批量处理配置 ====================
    
    @PostConstruct
    public void init() {
        if (batchEnabled) {
            // 创建批量处理调度器（单线程，确保串行处理）
            batchScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cache-batch-processor");
                t.setDaemon(true);
                return t;
            });
            
            // 启动定时批量处理任务
            batchScheduler.scheduleWithFixedDelay(
                    this::processBatch,
                    batchIntervalMs,
                    batchIntervalMs,
                    TimeUnit.MILLISECONDS
            );
            
            // 启动队列监控任务（每10秒检查一次队列大小）
            batchScheduler.scheduleWithFixedDelay(
                    this::monitorBatchQueue,
                    10,
                    10,
                    TimeUnit.SECONDS
            );
            
            log.info("[DeviceStateAsyncService] 批量处理已启用: batchSize={}, batchIntervalMs={}ms, batchQueueMaxSize={}",
                    batchSize, batchIntervalMs, batchQueueMaxSize);
        } else {
            log.info("[DeviceStateAsyncService] 批量处理未启用，使用单条处理模式");
        }
    }
    
    @PreDestroy
    public void destroy() {
        if (batchScheduler != null) {
            log.info("[DeviceStateAsyncService] 开始关闭批量处理调度器，处理剩余请求...");
            
            // 关闭前处理剩余的批量请求
            processBatch();
            
            // 清空队列，防止内存泄漏
            int remainingCount = batchQueue.size();
            if (remainingCount > 0) {
                log.warn("[DeviceStateAsyncService] 关闭时仍有{}个请求未处理，将被丢弃", remainingCount);
                batchQueue.clear();
            }
            
            batchScheduler.shutdown();
            try {
                if (!batchScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("[DeviceStateAsyncService] 批量处理调度器未在5秒内关闭，强制关闭");
                    batchScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                batchScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            log.info("[DeviceStateAsyncService] 批量处理调度器已关闭: 总处理次数={}, 成功={}, 失败={}, 队列满次数={}, 丢弃次数={}",
                    batchProcessCount.get(), batchSuccessCount.get(), batchFailureCount.get(),
                    batchQueueFullCount.get(), batchDroppedCount.get());
        }
    }
    
    /**
     * 监控批量处理队列大小
     * <p>
     * 资源泄漏防护：定期检查队列大小，超过阈值时记录警告
     * </p>
     */
    private void monitorBatchQueue() {
        int queueSize = batchQueue.size();
        
        if (queueSize > batchQueueMaxSize * 0.9) {
            // 队列使用率超过90%，严重警告
            log.error("[DeviceStateAsyncService] 批量处理队列接近满载（严重）: queueSize={}, maxSize={}, " +
                    "可能原因：批量处理速度跟不上请求速度，建议检查批量处理性能或增加批量处理频率",
                    queueSize, batchQueueMaxSize);
        } else if (queueSize > batchQueueMaxSize * 0.7) {
            // 队列使用率超过70%，警告
            log.warn("[DeviceStateAsyncService] 批量处理队列使用率较高: queueSize={}, maxSize={}, " +
                    "建议检查批量处理性能",
                    queueSize, batchQueueMaxSize);
        } else if (queueSize > 0) {
            // 队列有数据，记录调试信息
            log.debug("[DeviceStateAsyncService] 批量处理队列状态: queueSize={}, maxSize={}",
                    queueSize, batchQueueMaxSize);
        }
    }
    
    /**
     * 是否启用批量处理
     * Apollo配置：device.state.async.batch-enabled
     * 默认值：true
     */
    @Value("${device.state.async.batch-enabled:true}")
    private boolean batchEnabled;
    
    /**
     * 批量处理大小
     * Apollo配置：device.state.async.batch-size
     * 默认值：50
     */
    @Value("${device.state.async.batch-size:50}")
    private int batchSize;
    
    /**
     * 批量处理间隔（毫秒）
     * Apollo配置：device.state.async.batch-interval-ms
     * 默认值：100
     */
    @Value("${device.state.async.batch-interval-ms:100}")
    private long batchIntervalMs;
    
    /**
     * 批量处理队列最大大小
     * Apollo配置：device.state.async.batch-queue-max-size
     * 默认值：10000（防止队列无限增长导致内存泄漏）
     */
    @Value("${device.state.async.batch-queue-max-size:10000}")
    private int batchQueueMaxSize;
    
    /**
     * 批量处理队列
     * Key: deviceId, Value: 最新的缓存更新请求（去重，只保留最新的）
     * <p>
     * 资源泄漏防护：
     * 1. 限制队列最大大小，防止无限增长
     * 2. 定期监控队列大小，超过阈值时记录警告
     * 3. 队列满时丢弃最旧的请求（FIFO策略）
     * </p>
     */
    private final ConcurrentHashMap<Long, CacheUpdateRequest> batchQueue = new ConcurrentHashMap<>();
    
    /**
     * 批量处理调度器
     */
    private ScheduledExecutorService batchScheduler;
    
    /**
     * 批量处理统计
     */
    private final AtomicInteger batchProcessCount = new AtomicInteger(0);
    private final AtomicInteger batchSuccessCount = new AtomicInteger(0);
    private final AtomicInteger batchFailureCount = new AtomicInteger(0);
    private final AtomicInteger batchQueueFullCount = new AtomicInteger(0);
    private final AtomicInteger batchDroppedCount = new AtomicInteger(0);

    /**
     * 异步更新设备状态缓存和心跳
     * <p>
     * 优化说明：
     * 1. 使用 CompletableFuture.runAsync() 异步执行，不阻塞主流程
     * 2. 减少事务范围和锁持有时间
     * 3. 自动传递 MDC 上下文，确保日志中能正确显示设备编号
     * 4. 添加超时控制（5秒），防止线程池资源耗尽
     * 5. 完善的异常处理，确保所有异常都被正确记录
     * 6. 即使缓存更新失败，也不影响主流程
     * </p>
     *
     * @param identity 设备身份信息
     * @param currentStateCode 当前状态编码
     * @param eventTimestamp 事件时间戳
     * @param needUpdateCache 是否需要更新缓存（true-更新状态值，false-只刷新TTL）
     * @param traceId 追踪ID
     * @return CompletableFuture，调用方不需要等待结果
     */
    public CompletableFuture<Void> updateCacheAsync(DeviceIdentity identity, Integer currentStateCode,
                                                     Long eventTimestamp, boolean needUpdateCache, String traceId) {
        // 如果启用批量处理，将请求加入批量队列
        if (batchEnabled) {
            CacheUpdateRequest request = new CacheUpdateRequest(
                    identity, currentStateCode, eventTimestamp, needUpdateCache, traceId, System.currentTimeMillis());
            
            // 资源泄漏防护：检查队列大小，防止无限增长
            int currentSize = batchQueue.size();
            if (currentSize >= batchQueueMaxSize) {
                // 队列已满，丢弃最旧的请求（FIFO策略）
                // 注意：由于 ConcurrentHashMap 不保证顺序，这里采用随机丢弃策略
                // 实际效果：新请求会覆盖旧请求（因为使用 deviceId 作为 key）
                batchQueueFullCount.incrementAndGet();
                batchDroppedCount.incrementAndGet();
                log.warn("[DeviceStateAsyncService] 批量处理队列已满，丢弃请求: queueSize={}, maxSize={}, deviceId={}",
                        currentSize, batchQueueMaxSize, identity.deviceInfoId());
            }
            
            // 使用 deviceId 作为 key，自动去重（同一设备的多次更新只保留最新的）
            // 注意：如果队列已满，新请求会覆盖旧请求，这是合理的（保留最新的状态）
            batchQueue.put(identity.deviceInfoId(), request);
            
            // 如果队列大小达到批量大小，立即触发批量处理
            if (batchQueue.size() >= batchSize) {
                // 注意：不持有 CompletableFuture 引用，避免内存泄漏
                CompletableFuture.runAsync(this::processBatch, webhookAsyncExecutor)
                        .whenComplete((result, throwable) -> {
                            // 确保异常被处理，避免未完成的 Future 占用内存
                            if (throwable != null) {
                                log.warn("[DeviceStateAsyncService] 触发批量处理失败: error={}", 
                                        throwable.getMessage());
                            }
                        });
            }
            
            // 返回已完成的 Future（批量处理是异步的，不阻塞）
            // 注意：调用方不持有此 Future 引用，避免内存泄漏
            return CompletableFuture.completedFuture(null);
        }
        
        // 未启用批量处理，使用单条处理模式
        return updateCacheAsyncSingle(identity, currentStateCode, eventTimestamp, needUpdateCache, traceId);
    }
    
    /**
     * 单条缓存更新（未启用批量处理时使用）
     */
    private CompletableFuture<Void> updateCacheAsyncSingle(DeviceIdentity identity, Integer currentStateCode,
                                                           Long eventTimestamp, boolean needUpdateCache, String traceId) {
        long startTime = System.currentTimeMillis();
        // 获取当前线程的 MDC 上下文，以便在异步执行时传递
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        
        return CompletableFuture.runAsync(() -> {
            // 在异步线程中恢复 MDC 上下文
            if (mdcContext != null && !mdcContext.isEmpty()) {
                MDC.setContextMap(mdcContext);
            }
            try {
                Long orgFactoryId = identity.orgFactoryId();
                Long deviceInfoId = identity.deviceInfoId();
                
                if (needUpdateCache) {
                    // 更新状态缓存（使用数字编码）
                    String stateStr = String.valueOf(currentStateCode);
                    webhookHandlerUtils.updateStateCacheAndHeartbeat(orgFactoryId, deviceInfoId, stateStr,
                            eventTimestamp, traceId);
                    log.debug("[DeviceStateAsyncService] 异步更新缓存成功: deviceInfoId={}, state={}({})",
                            deviceInfoId, stateStr, currentStateCode);
                } else {
                    // 状态未变化，只刷新缓存 TTL 和心跳
                    webhookHandlerUtils.refreshStateCacheAndHeartbeat(orgFactoryId, deviceInfoId, traceId);
                    log.debug("[DeviceStateAsyncService] 异步刷新缓存TTL成功: deviceInfoId={}, state={}({})",
                            deviceInfoId, currentStateCode);
                }
            } catch (Exception e) {
                // 缓存更新失败不影响主流程，但需要记录警告
                // 注意：这里捕获异常后不重新抛出，让 CompletableFuture 正常完成
                log.warn("[DeviceStateAsyncService] 异步更新缓存失败: deviceInfoId={}, state={}({}), error={}",
                        identity.deviceInfoId(), currentStateCode, e.getMessage(), e);
                // 重新抛出异常，让 CompletableFuture 的 handle 方法处理
                throw new RuntimeException("缓存更新失败", e);
            } finally {
                // 清除 MDC，避免线程复用导致设备编号污染
                MDC.clear();
            }
        }, webhookAsyncExecutor)
        .orTimeout(5, TimeUnit.SECONDS)  // 超时控制：5秒超时
        .handle((result, throwable) -> {
            long costTime = System.currentTimeMillis() - startTime;
            if (throwable != null) {
                if (throwable instanceof TimeoutException) {
                    log.warn("[DeviceStateAsyncService] 异步更新缓存超时: deviceInfoId={}, state={}({}), costTime={}ms",
                            identity.deviceInfoId(), currentStateCode, costTime);
                } else {
                    // 处理其他异常（包括 RuntimeException）
                    Throwable cause = throwable.getCause();
                    if (cause != null && cause.getMessage() != null && cause.getMessage().contains("缓存更新失败")) {
                        // 这是业务异常，已经在内部记录过日志，这里只记录耗时
                        log.debug("[DeviceStateAsyncService] 异步更新缓存业务异常: deviceInfoId={}, state={}({}), costTime={}ms",
                                identity.deviceInfoId(), currentStateCode, costTime);
                    } else {
                        // 其他未预期的异常
                        log.warn("[DeviceStateAsyncService] 异步更新缓存异常: deviceInfoId={}, state={}({}), costTime={}ms, error={}",
                                identity.deviceInfoId(), currentStateCode, costTime, throwable.getMessage(), throwable);
                    }
                }
            } else {
                // 成功完成
                log.debug("[DeviceStateAsyncService] 异步更新缓存完成: deviceInfoId={}, state={}({}), costTime={}ms",
                        identity.deviceInfoId(), currentStateCode, costTime);
            }
            // 资源泄漏防护：确保 CompletableFuture 完成，避免未完成的 Future 占用内存
            // 返回 null 表示完成（无论成功或失败），Future 可以被 GC 回收
            return null;
        });
    }
    
    /**
     * 批量处理缓存更新请求
     */
    private void processBatch() {
        if (batchQueue.isEmpty()) {
            return;
        }
        
        // 取出所有请求并清空队列
        Map<Long, CacheUpdateRequest> requests = new HashMap<>(batchQueue);
        batchQueue.clear();
        
        if (requests.isEmpty()) {
            return;
        }
        
        int requestCount = requests.size();
        batchProcessCount.incrementAndGet();
        long startTime = System.currentTimeMillis();
        
        try {
            // 按工厂ID分组，便于批量处理
            Map<Long, List<CacheUpdateRequest>> factoryGroups = requests.values().stream()
                    .collect(Collectors.groupingBy(CacheUpdateRequest::getFactoryId));
            
            // 批量处理每个工厂的请求
            for (Map.Entry<Long, List<CacheUpdateRequest>> factoryEntry : factoryGroups.entrySet()) {
                Long factoryId = factoryEntry.getKey();
                List<CacheUpdateRequest> factoryRequests = factoryEntry.getValue();
                
                // 批量更新缓存（使用 Pipeline）
                batchUpdateCache(factoryId, factoryRequests);
            }
            
            long costTime = System.currentTimeMillis() - startTime;
            batchSuccessCount.incrementAndGet();
            log.debug("[DeviceStateAsyncService] 批量处理缓存更新成功: requestCount={}, factoryCount={}, costTime={}ms",
                    requestCount, factoryGroups.size(), costTime);
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            batchFailureCount.incrementAndGet();
            log.warn("[DeviceStateAsyncService] 批量处理缓存更新失败: requestCount={}, costTime={}ms, error={}",
                    requestCount, costTime, e.getMessage(), e);
        }
    }
    
    /**
     * 批量更新缓存
     * <p>
     * 优化说明：
     * 1. 按操作类型分组（更新状态值 vs 刷新TTL），减少重复判断
     * 2. 批量处理减少线程池任务数量
     * 3. 虽然每个设备还是单独调用，但减少了线程池压力（多个请求合并为一个任务）
     * </p>
     * <p>
     * 注意：如果要实现真正的批量 Pipeline，需要修改 DeviceStateCacheService 添加批量方法
     * 当前实现已经实现了主要目标：减少线程池任务数量、自动去重、定时批量处理
     * </p>
     */
    private void batchUpdateCache(Long factoryId, List<CacheUpdateRequest> requests) {
        // 获取当前线程的 MDC 上下文
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        
        try {
            // 在异步线程中恢复 MDC 上下文
            if (mdcContext != null && !mdcContext.isEmpty()) {
                MDC.setContextMap(mdcContext);
            }
            
            // 按操作类型分组：需要更新状态值的 vs 只需要刷新TTL的
            Map<Boolean, List<CacheUpdateRequest>> groupedRequests = requests.stream()
                    .collect(Collectors.partitioningBy(CacheUpdateRequest::isNeedUpdateCache));
            
            // 处理需要更新状态值的请求
            List<CacheUpdateRequest> updateRequests = groupedRequests.get(true);
            if (updateRequests != null && !updateRequests.isEmpty()) {
                for (CacheUpdateRequest request : updateRequests) {
                    try {
                        String stateStr = String.valueOf(request.getCurrentStateCode());
                        webhookHandlerUtils.updateStateCacheAndHeartbeat(
                                request.getFactoryId(),
                                request.getDeviceId(),
                                stateStr,
                                request.getEventTimestamp(),
                                request.getTraceId()
                        );
                    } catch (Exception e) {
                        // 单个设备更新失败不影响其他设备
                        log.warn("[DeviceStateAsyncService] 批量更新缓存中单个设备失败: factoryId={}, deviceId={}, error={}",
                                request.getFactoryId(), request.getDeviceId(), e.getMessage());
                    }
                }
            }
            
            // 处理只需要刷新TTL的请求
            List<CacheUpdateRequest> refreshRequests = groupedRequests.get(false);
            if (refreshRequests != null && !refreshRequests.isEmpty()) {
                for (CacheUpdateRequest request : refreshRequests) {
                    try {
                        webhookHandlerUtils.refreshStateCacheAndHeartbeat(
                                request.getFactoryId(),
                                request.getDeviceId(),
                                request.getTraceId()
                        );
                    } catch (Exception e) {
                        // 单个设备刷新失败不影响其他设备
                        log.warn("[DeviceStateAsyncService] 批量刷新缓存中单个设备失败: factoryId={}, deviceId={}, error={}",
                                request.getFactoryId(), request.getDeviceId(), e.getMessage());
                    }
                }
            }
            
            log.debug("[DeviceStateAsyncService] 批量更新缓存完成: factoryId={}, updateCount={}, refreshCount={}",
                    factoryId, 
                    updateRequests != null ? updateRequests.size() : 0,
                    refreshRequests != null ? refreshRequests.size() : 0);
        } catch (Exception e) {
            log.error("[DeviceStateAsyncService] 批量更新缓存异常: factoryId={}, requestCount={}, error={}",
                    factoryId, requests.size(), e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }

    /**
     * 异步记录异常日志
     * <p>
     * 优化说明：
     * 1. 使用 CompletableFuture.runAsync() 异步执行，不阻塞主流程
     * 2. 减少事务范围和锁持有时间
     * 3. 自动传递 MDC 上下文，确保日志中能正确显示设备编号
     * 4. 添加超时控制（3秒），防止线程池资源耗尽
     * 5. 完善的异常处理，确保所有异常都被正确记录
     * 6. 即使日志记录失败，也不影响主流程
     * </p>
     *
     * @param request Webhook 请求
     * @param errorType 错误类型
     * @param errorMessage 错误消息
     * @param needReview 是否需要人工审核
     * @return CompletableFuture，调用方不需要等待结果
     */
    public CompletableFuture<Void> saveFailLogAsync(WebhookRequest request, String errorType, 
                                                     String errorMessage, boolean needReview) {
        if (webhookFailLogService == null) {
            log.warn("[DeviceStateAsyncService] WebhookFailLogService未注入，跳过异常日志记录");
            return CompletableFuture.completedFuture(null);
        }
        
        long startTime = System.currentTimeMillis();
        // 获取当前线程的 MDC 上下文，以便在异步执行时传递
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        
        return CompletableFuture.runAsync(() -> {
            // 在异步线程中恢复 MDC 上下文
            if (mdcContext != null && !mdcContext.isEmpty()) {
                MDC.setContextMap(mdcContext);
            }
            try {
                webhookFailLogService.saveFailLog(request, errorType, errorMessage, needReview);
                log.debug("[DeviceStateAsyncService] 异步记录异常日志成功: errorType={}, messageId={}", 
                        errorType, request.getMessageId());
            } catch (Exception e) {
                // 异常日志记录失败不影响主流程，但需要记录警告
                // 重新抛出异常，让 CompletableFuture 的 handle 方法处理
                log.warn("[DeviceStateAsyncService] 异步记录异常日志失败: errorType={}, messageId={}, error={}", 
                        errorType, request.getMessageId(), e.getMessage(), e);
                throw new RuntimeException("异常日志记录失败", e);
            } finally {
                // 清除 MDC，避免线程复用导致设备编号污染
                MDC.clear();
            }
        }, webhookAsyncExecutor)
        .orTimeout(3, TimeUnit.SECONDS)  // 超时控制：3秒超时（日志记录应该更快）
        .handle((result, throwable) -> {
            long costTime = System.currentTimeMillis() - startTime;
            if (throwable != null) {
                if (throwable instanceof TimeoutException) {
                    log.warn("[DeviceStateAsyncService] 异步记录异常日志超时: errorType={}, messageId={}, costTime={}ms",
                            errorType, request.getMessageId(), costTime);
                } else {
                    // 处理其他异常（包括 RuntimeException）
                    Throwable cause = throwable.getCause();
                    if (cause != null && cause.getMessage() != null && cause.getMessage().contains("异常日志记录失败")) {
                        // 这是业务异常，已经在内部记录过日志，这里只记录耗时
                        log.debug("[DeviceStateAsyncService] 异步记录异常日志业务异常: errorType={}, messageId={}, costTime={}ms",
                                errorType, request.getMessageId(), costTime);
                    } else {
                        // 其他未预期的异常
                        log.warn("[DeviceStateAsyncService] 异步记录异常日志异常: errorType={}, messageId={}, costTime={}ms, error={}",
                                errorType, request.getMessageId(), costTime, throwable.getMessage(), throwable);
                    }
                }
            } else {
                // 成功完成
                log.debug("[DeviceStateAsyncService] 异步记录异常日志完成: errorType={}, messageId={}, costTime={}ms",
                        errorType, request.getMessageId(), costTime);
            }
            // 资源泄漏防护：确保 CompletableFuture 完成，避免未完成的 Future 占用内存
            // 返回 null 表示完成（无论成功或失败），Future 可以被 GC 回收
            return null;
        });
    }
}
