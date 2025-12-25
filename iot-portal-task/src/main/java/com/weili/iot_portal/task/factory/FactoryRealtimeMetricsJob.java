package com.weili.iot_portal.task.factory;

import com.weili.iot_portal.domain.ingestion.BatchProcessResult;
import com.weili.iot_portal.service.factory.IFactoryMetricsService;
import com.weili.iot_portal.task.device.config.DeviceMetricsConfig;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 工厂级实时指标计算任务
 * 依赖设备实时指标，对每个工厂按加权平均
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FactoryRealtimeMetricsJob extends BaseScheduledJob {

    private final IFactoryMetricsService factoryMetricsService;
    // 复用设备实时指标配置（batch/timeout）
    private final DeviceMetricsConfig deviceMetricsConfig;

    @Override
    protected String getJobName() {
        return "工厂实时指标计算任务";
    }

    @Override
    @XxlJob("factoryRealtimeMetricsJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        long calculationTimeSeconds = System.currentTimeMillis() / 1000;
        XxlJobHelper.log("工厂实时指标计算时间点: {}", Instant.ofEpochSecond(calculationTimeSeconds));

        BatchProcessResult result =
                factoryMetricsService.processAllFactoriesRealtimeWithCheckpoint(
                        calculationTimeSeconds,
                        deviceMetricsConfig.getBatchSize(),
                        deviceMetricsConfig.getTimeoutMillis());

        return JobExecutionResult.of(
                result.getSuccessCount(),
                result.getSkipCount(),
                result.getErrorCount());
    }
}

