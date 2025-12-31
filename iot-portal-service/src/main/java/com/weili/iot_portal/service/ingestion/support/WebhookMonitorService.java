package com.weili.iot_portal.service.ingestion.support;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookMonitorRecordDO;
import com.weili.iot_portal.dal.repository.ingestion.WebhookMonitorRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Webhook 监控与告警埋点（优化：异步批量插入）
 * <p>
 * 优化点：
 * 1. 异步批量插入，不阻塞主流程
 * 2. 使用内存队列缓冲，定时批量写入
 * 3. 支持配置批量大小和间隔时间
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookMonitorService {

    private final WebhookMonitorRecordRepository monitorRecordRepository;

    /**
     * 批量插入大小（默认：1000）
     */
    @Value("${webhook.monitor.batch-size:1000}")
    private int batchSize;

    /**
     * 批量插入间隔（秒，默认：5）
     */
    @Value("${webhook.monitor.batch-interval-seconds:5}")
    private int batchIntervalSeconds;

    /**
     * 队列最大大小（默认：10000）
     */
    @Value("${webhook.monitor.queue-size:10000}")
    private int queueSize;

    /**
     * 监控记录队列（异步缓冲）
     */
    private final BlockingQueue<WebhookMonitorRecordDO> recordQueue = new LinkedBlockingQueue<>();

    /**
     * 批量插入调度器
     */
    private ScheduledExecutorService scheduler;

    /**
     * 初始化：启动批量插入任务
     */
    @PostConstruct
    public void init() {
        // 验证配置
        if (batchSize <= 0) {
            log.warn("[Webhook-Monitor] batch-size 配置无效: {}，使用默认值: 1000", batchSize);
            batchSize = 1000;
        }
        if (batchIntervalSeconds <= 0) {
            log.warn("[Webhook-Monitor] batch-interval-seconds 配置无效: {}，使用默认值: 5", batchIntervalSeconds);
            batchIntervalSeconds = 5;
        }
        if (queueSize <= 0) {
            log.warn("[Webhook-Monitor] queue-size 配置无效: {}，使用默认值: 10000", queueSize);
            queueSize = 10000;
        }

        // 创建单线程调度器
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "webhook-monitor-batch-insert");
            t.setDaemon(true);
            return t;
        });

        // 定时批量插入：按时间间隔
        scheduler.scheduleWithFixedDelay(
            this::batchInsert,
            batchIntervalSeconds,
            batchIntervalSeconds,
            TimeUnit.SECONDS
        );

        log.info("[Webhook-Monitor] 监控服务初始化完成: batchSize={}, batchIntervalSeconds={}, queueSize={}",
                batchSize, batchIntervalSeconds, queueSize);
    }

    /**
     * 销毁：关闭调度器，处理剩余数据
     */
    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            // 关闭前，处理剩余数据
            batchInsert();
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("[Webhook-Monitor] 监控服务已关闭");
    }

    /**
     * 记录未匹配到 Handler
     */
    public void recordUnmatched(String eventType) {
        log.warn("Webhook unmatched handler: eventType={}", eventType);
        persist(eventType, null, "UNMATCHED", null, null, null);
    }

    /**
     * 记录处理成功
     * <p>
     * 优化：合并记录，同时记录 handlerName，减少数据冗余（从 2条/消息 减少到 1条/消息）
     * </p>
     * @param eventType 事件类型
     * @param handlerName 处理器名称（可为空）
     * @param elapsedMs 处理耗时（毫秒）
     */
    public void recordSuccess(String eventType, String handlerName, long elapsedMs) {
        log.info("Webhook handled success: eventType={}, handler={}, cost={}ms", eventType, handlerName, elapsedMs);
        persist(eventType, handlerName, "SUCCESS", elapsedMs, null, null);
    }

    /**
     * 记录处理失败
     * <p>
     * 优化：合并记录，同时记录 handlerName，减少数据冗余（从 2条/消息 减少到 1条/消息）
     * </p>
     * @param eventType 事件类型
     * @param handlerName 处理器名称（可为空）
     * @param error 错误信息
     * @param elapsedMs 处理耗时（毫秒）
     * @param willRetry 是否重试
     */
    public void recordFailure(String eventType, String handlerName, String error, long elapsedMs, boolean willRetry) {
        log.error("Webhook handled failure: eventType={}, handler={}, cost={}ms, willRetry={}, error={}",
                eventType, handlerName, elapsedMs, willRetry, error);
        persist(eventType, handlerName, "FAILURE", elapsedMs, error, willRetry);
    }

    /**
     * 将监控指标放入队列（异步，不阻塞）
     */
    private void persist(String eventType, String handlerName, String status,
                         Long elapsedMs, String error, Boolean willRetry) {
        WebhookMonitorRecordDO record = new WebhookMonitorRecordDO();
        record.setEventType(safe(eventType));
        record.setHandlerName(StringUtils.isNotBlank(handlerName) ? handlerName : null);
        record.setStatus(status);
        record.setElapsedMs(elapsedMs);
        record.setErrorMessage(error);
        record.setWillRetry(willRetry);
        record.setCreateTime(LocalDateTime.now());

        // 异步放入队列，不阻塞
        if (!recordQueue.offer(record)) {
            // 队列满时，记录警告日志（但不阻塞主流程）
            log.warn("[Webhook-Monitor] 监控记录队列已满，丢弃记录: eventType={}, status={}, queueSize={}",
                    eventType, status, recordQueue.size());
        }
    }

    /**
     * 批量插入监控记录
     */
    private void batchInsert() {
        List<WebhookMonitorRecordDO> batch = new ArrayList<>();
        
        // 批量取出记录（最多 batchSize 条）
        recordQueue.drainTo(batch, batchSize);

        if (batch.isEmpty()) {
            return;
        }

        try {
            // 批量插入
            monitorRecordRepository.insertBatch(batch);
            log.debug("[Webhook-Monitor] 批量插入监控记录成功: count={}", batch.size());
        } catch (Exception e) {
            log.error("[Webhook-Monitor] 批量插入监控记录失败: count={}", batch.size(), e);
            // 失败时，可以选择重新放入队列（但要注意避免无限循环）
            // 这里选择记录日志，不重新放入队列（避免阻塞）
        }
    }

    /**
     * 安全字符串处理
     */
    private String safe(String v) {
        return StringUtils.defaultIfBlank(v, "unknown");
    }

    /**
     * 获取队列大小（用于监控）
     */
    public int getQueueSize() {
        return recordQueue.size();
    }
}

