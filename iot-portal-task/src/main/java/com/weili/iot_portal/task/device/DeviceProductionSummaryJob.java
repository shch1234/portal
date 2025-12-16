package com.weili.iot_portal.task.device;

import com.weili.iot_portal.service.device.IDeviceProductionSummaryService;
import com.weili.iot_portal.task.device.config.DeviceProductionSummaryConfig;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 设备产量汇总定时任务
 * 按班次统计 device_production_record 到 device_production_summary
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceProductionSummaryJob extends BaseScheduledJob {

    private final IDeviceProductionSummaryService productionSummaryService;
    private final DeviceProductionSummaryConfig config;

    @Override
    protected String getJobName() {
        return "设备产量汇总任务";
    }

    @Override
    @XxlJob("deviceProductionSummaryJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        long statisticsTimeSeconds = System.currentTimeMillis() / 1000;
        XxlJobHelper.log("产量汇总统计时间: {}", Instant.ofEpochSecond(statisticsTimeSeconds));

        IDeviceProductionSummaryService.BatchProcessResult result =
                productionSummaryService.processAllDevicesWithCheckpoint(
                        statisticsTimeSeconds,
                        config.getBatchSize(),
                        config.getTimeoutMillis());

        return JobExecutionResult.of(
                result.getSuccessCount(),
                result.getSkipCount(),
                result.getErrorCount());
    }
}


