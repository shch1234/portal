package com.weili.iot_portal.task.device;

import com.weili.iot_portal.domain.ingestion.BatchProcessResult;
import com.weili.iot_portal.service.device.IDeviceMetricsSummaryService;
import com.weili.iot_portal.service.device.IDeviceProductionSummaryService;
import com.weili.iot_portal.service.device.IDeviceShiftSummaryService;
import com.weili.iot_portal.task.device.config.DeviceMetricsSummaryConfig;
import com.weili.iot_portal.task.device.config.DeviceProductionSummaryConfig;
import com.weili.iot_portal.task.device.config.DeviceStateSummaryConfig;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.*;

/**
 * 设备班次指标汇总任务
 * <p>
 * 执行流程：
 * 1. 并行执行状态汇总和产量汇总任务（确保数据已就绪）
 *    - 使用异步执行 + 超时控制，避免卡死
 *    - 如果任务超时或失败，记录警告但继续执行指标汇总
 * 2. 等待前置任务完成后，再执行指标汇总任务
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceMetricsSummaryJob extends BaseScheduledJob {

    private final IDeviceMetricsSummaryService metricsSummaryService;
    private final IDeviceProductionSummaryService productionSummaryService;
    private final IDeviceShiftSummaryService shiftSummaryService;
    private final DeviceMetricsSummaryConfig config;
    private final DeviceProductionSummaryConfig productionConfig;
    private final DeviceStateSummaryConfig stateSummaryConfig;

    /**
     * 用于异步执行前置任务的线程池
     * 使用守护线程，避免阻塞应用关闭
     */
    private static final ExecutorService PREREQUISITE_TASKS_EXECUTOR = 
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "prerequisite-tasks-executor");
                t.setDaemon(true);
                return t;
            });

    @Override
    protected String getJobName() {
        return "设备班次指标汇总任务";
    }

    @Override
    @XxlJob("deviceMetricsSummaryJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        long statisticsTimeMillis = System.currentTimeMillis();
        long statisticsTimeSeconds = statisticsTimeMillis / 1000;

        XxlJobHelper.log("指标汇总统计时间点: {}, 处理时间范围: {} 天, 数据就绪延迟: {} 小时", 
                Instant.ofEpochMilli(statisticsTimeMillis), config.getLookbackDays(), config.getDataReadyDelayHours());

        // 步骤1：并行执行状态汇总和产量汇总任务，确保数据已就绪（带超时和异常保护）
        XxlJobHelper.log("开始并行执行前置任务（状态汇总 + 产量汇总）...");
        long prerequisiteStartTime = System.currentTimeMillis();
        
        CompletableFuture<BatchProcessResult> stateSummaryFuture = executeStateSummaryWithTimeout(statisticsTimeMillis);
        CompletableFuture<BatchProcessResult> productionSummaryFuture = executeProductionSummaryWithTimeout(statisticsTimeSeconds);
        
        // 等待两个前置任务都完成（或超时）
        try {
            CompletableFuture.allOf(stateSummaryFuture, productionSummaryFuture).join();
        } catch (Exception e) {
            log.warn("前置任务执行过程中出现异常，继续执行指标汇总", e);
        }
        
        long prerequisiteCostTime = System.currentTimeMillis() - prerequisiteStartTime;
        XxlJobHelper.log("前置任务执行完成，总耗时: {}ms", prerequisiteCostTime);

        // 步骤2：等待一小段时间，确保数据已持久化到数据库
        // 注意：这里等待时间很短（1秒），主要是为了确保数据库事务已提交
        Thread.sleep(1000);
        XxlJobHelper.log("等待数据持久化完成（1秒）...");

        // 步骤3：执行指标汇总任务（无论产量汇总是否成功都继续执行）
        XxlJobHelper.log("开始执行指标汇总任务...");
        long metricsStartTime = System.currentTimeMillis();
        
        BatchProcessResult metricsResult = metricsSummaryService.processAllDevicesWithCheckpoint(
                statisticsTimeMillis,
                config.getBatchSize(),
                config.getTimeoutMillis(),
                config.getLookbackDays(),
                config.getDataReadyDelayHours(),
                config.getRecalculationIntervalHours());
        
        long metricsCostTime = System.currentTimeMillis() - metricsStartTime;
        XxlJobHelper.log("指标汇总任务完成: 成功={}, 跳过={}, 失败={}, 耗时={}ms", 
                metricsResult.getSuccessCount(), 
                metricsResult.getSkipCount(), 
                metricsResult.getErrorCount(),
                metricsCostTime);

        // 返回指标汇总的结果（产量汇总的结果已在日志中记录）
        return JobExecutionResult.of(
                metricsResult.getSuccessCount(),
                metricsResult.getSkipCount(),
                metricsResult.getErrorCount());
    }

    /**
     * 执行状态汇总任务（带超时和异常保护）
     * <p>
     * 如果状态汇总任务超时或失败，不会阻塞指标汇总任务的执行
     * 
     * @param statisticsTimeMillis 统计时间点（毫秒）
     * @return 状态汇总结果的 CompletableFuture
     */
    private CompletableFuture<BatchProcessResult> executeStateSummaryWithTimeout(long statisticsTimeMillis) {
        XxlJobHelper.log("开始执行状态汇总任务（前置依赖，带超时保护）...");
        long stateSummaryStartTime = System.currentTimeMillis();
        
        // 计算超时时间：使用状态汇总任务的超时时间 + 20% 的缓冲时间
        long timeoutMillis = stateSummaryConfig.getTimeoutMillis() + 
                (long) (stateSummaryConfig.getTimeoutMillis() * 0.2); // 增加20%缓冲
        
        // 使用 CompletableFuture 异步执行，支持超时控制
        CompletableFuture<BatchProcessResult> stateSummaryFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return shiftSummaryService.processAllDevicesWithCheckpoint(
                        statisticsTimeMillis,
                        stateSummaryConfig.getBatchSize(),
                        stateSummaryConfig.getTimeoutMillis());
            } catch (Exception e) {
                log.error("状态汇总任务执行异常", e);
                return BatchProcessResult.completed(0, 0, 1);
            }
        }, PREREQUISITE_TASKS_EXECUTOR);

        // 设置超时处理
        return stateSummaryFuture
                .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .handle((result, throwable) -> {
                    long stateSummaryCostTime = System.currentTimeMillis() - stateSummaryStartTime;
                    if (throwable != null) {
                        if (throwable instanceof TimeoutException) {
                            XxlJobHelper.log("警告: 状态汇总任务执行超时（{}ms），强制继续执行指标汇总任务。已耗时: {}ms", 
                                    timeoutMillis, stateSummaryCostTime);
                        } else {
                            XxlJobHelper.log("警告: 状态汇总任务执行异常，强制继续执行指标汇总任务。已耗时: {}ms, 异常: {}", 
                                    stateSummaryCostTime, throwable.getMessage());
                            log.error("状态汇总任务执行异常", throwable);
                        }
                        return BatchProcessResult.completed(0, 0, 1);
                    } else {
                        XxlJobHelper.log("状态汇总任务完成: 成功={}, 跳过={}, 失败={}, 耗时={}ms", 
                                result.getSuccessCount(), result.getSkipCount(), result.getErrorCount(), stateSummaryCostTime);
                        if (result.getErrorCount() > 0) {
                            XxlJobHelper.log("警告: 状态汇总任务有 {} 个失败，但继续执行指标汇总任务", result.getErrorCount());
                        }
                        return result;
                    }
                });
    }

    /**
     * 执行产量汇总任务（带超时和异常保护）
     * <p>
     * 如果产量汇总任务超时或失败，不会阻塞指标汇总任务的执行
     * 
     * @param statisticsTimeSeconds 统计时间点（秒）
     * @return 产量汇总结果的 CompletableFuture
     */
    private CompletableFuture<BatchProcessResult> executeProductionSummaryWithTimeout(long statisticsTimeSeconds) {
        XxlJobHelper.log("开始执行产量汇总任务（前置依赖，带超时保护）...");
        long productionStartTime = System.currentTimeMillis();
        
        // 计算超时时间：使用产量汇总任务的超时时间 + 20% 的缓冲时间
        long timeoutMillis = productionConfig.getTimeoutMillis() + 
                (long) (productionConfig.getTimeoutMillis() * 0.2); // 增加20%缓冲
        
        // 使用 CompletableFuture 异步执行，支持超时控制
        CompletableFuture<BatchProcessResult> productionFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return productionSummaryService.processAllDevicesWithCheckpoint(
                        statisticsTimeSeconds,
                        productionConfig.getBatchSize(),
                        productionConfig.getTimeoutMillis(),
                        productionConfig.getLookbackDays());
            } catch (Exception e) {
                log.error("产量汇总任务执行异常", e);
                return BatchProcessResult.completed(0, 0, 1);
            }
        }, PREREQUISITE_TASKS_EXECUTOR);

        // 设置超时处理
        return productionFuture
                .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .handle((result, throwable) -> {
                    long productionCostTime = System.currentTimeMillis() - productionStartTime;
                    if (throwable != null) {
                        if (throwable instanceof TimeoutException) {
                            XxlJobHelper.log("警告: 产量汇总任务执行超时（{}ms），强制继续执行指标汇总任务。已耗时: {}ms", 
                                    timeoutMillis, productionCostTime);
                        } else {
                            XxlJobHelper.log("警告: 产量汇总任务执行异常，强制继续执行指标汇总任务。已耗时: {}ms, 异常: {}", 
                                    productionCostTime, throwable.getMessage());
                            log.error("产量汇总任务执行异常", throwable);
                        }
                        return BatchProcessResult.completed(0, 0, 1);
                    } else {
                        XxlJobHelper.log("产量汇总任务完成: 成功={}, 跳过={}, 失败={}, 耗时={}ms", 
                                result.getSuccessCount(), result.getSkipCount(), result.getErrorCount(), productionCostTime);
                        if (result.getErrorCount() > 0) {
                            XxlJobHelper.log("警告: 产量汇总任务有 {} 个失败，但继续执行指标汇总任务", result.getErrorCount());
                        }
                        return result;
                    }
                });
    }
}


