package com.weili.iot_portal.task.device;

import com.weili.iot_portal.domain.ingestion.BatchProcessResult;
import com.weili.iot_portal.service.device.IDeviceShiftSummaryService;
import com.weili.iot_portal.task.device.config.DeviceStateSummaryConfig;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 设备状态汇总定时任务
 * <p>
     * 功能：周期性补偿上一个班次的状态数据，更新 device_state_summary 表
 * <p>
 * 配置说明：
 * - shift.summary.batch-size: 每批处理的设备数量（默认50）
 * - shift.summary.checkpoint-ttl-seconds: 检查点TTL（默认24小时）
 * - shift.summary.timeout-millis: 任务超时时间（默认25分钟）
 * - 建议在XXL-Job中配置cron表达式，例如：每5分钟执行一次
 * <p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceStateSummaryJob extends BaseScheduledJob {

    private final IDeviceShiftSummaryService shiftSummaryService;
    private final DeviceStateSummaryConfig config;

    @Override
    protected String getJobName() {
        return "设备状态汇总任务";
    }

    /**
     * 执行入口
     */
    @Override
    @XxlJob("deviceStateSummaryJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        // 直接使用毫秒，统一时间单位
        long statisticsTimeMillis = System.currentTimeMillis();

        XxlJobHelper.log("统计时间点: {}", Instant.ofEpochMilli(statisticsTimeMillis));

        BatchProcessResult result =
                shiftSummaryService.processAllDevicesWithCheckpoint(
                        statisticsTimeMillis,
                        config.getBatchSize(),
                        config.getTimeoutMillis());

        return JobExecutionResult.of(
                result.getSuccessCount(),
                result.getSkipCount(),
                result.getErrorCount());
    }

}
