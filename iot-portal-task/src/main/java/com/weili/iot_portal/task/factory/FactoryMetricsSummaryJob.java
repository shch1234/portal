package com.weili.iot_portal.task.factory;

import com.weili.iot_portal.service.factory.IFactoryMetricsService;
import com.weili.iot_portal.task.device.config.DeviceMetricsSummaryConfig;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 工厂级班次指标汇总任务
 * 依赖设备班次指标，对每个工厂按加权平均汇总
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FactoryMetricsSummaryJob extends BaseScheduledJob {

    private final IFactoryMetricsService factoryMetricsService;
    // 复用设备班次指标的批量/超时配置
    private final DeviceMetricsSummaryConfig config;

    @Override
    protected String getJobName() {
        return "工厂班次指标汇总任务";
    }

    @Override
    @XxlJob("factoryMetricsSummaryJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        long statisticsTimeSeconds = System.currentTimeMillis() / 1000;
        XxlJobHelper.log("工厂班次指标统计时间点: {}", Instant.ofEpochSecond(statisticsTimeSeconds));

        IFactoryMetricsService.BatchProcessResult result =
                factoryMetricsService.processAllFactoriesShiftSummaryWithCheckpoint(
                        statisticsTimeSeconds,
                        config.getBatchSize(),
                        config.getTimeoutMillis());

        return JobExecutionResult.of(
                result.getSuccessCount(),
                result.getSkipCount(),
                result.getErrorCount());
    }
}

