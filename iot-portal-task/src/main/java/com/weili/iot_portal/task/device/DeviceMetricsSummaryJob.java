package com.weili.iot_portal.task.device;

import com.weili.iot_portal.service.device.IDeviceMetricsSummaryService;
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
 * 设备班次指标汇总任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceMetricsSummaryJob extends BaseScheduledJob {

    private final IDeviceMetricsSummaryService metricsSummaryService;
    private final DeviceMetricsSummaryConfig config;

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
        long statisticsTimeSeconds = System.currentTimeMillis() / 1000;

        XxlJobHelper.log("指标汇总统计时间点: {}", Instant.ofEpochSecond(statisticsTimeSeconds));

        IDeviceMetricsSummaryService.BatchProcessResult result =
                metricsSummaryService.processAllDevicesWithCheckpoint(
                        statisticsTimeSeconds,
                        config.getBatchSize(),
                        config.getTimeoutMillis());

        return JobExecutionResult.of(
                result.getSuccessCount(),
                result.getSkipCount(),
                result.getErrorCount());
    }
}


