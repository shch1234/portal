package com.weili.iot_portal.task.device;

import com.weili.iot_portal.domain.ingestion.BatchProcessResult;
import com.weili.iot_portal.service.device.IDeviceMetricsService;
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
 * 设备实时指标计算任务（每5分钟刷新一次）
 * <p>
 * 功能：计算设备的OEE相关指标（时间开动率、性能率、可用率、故障率、OEE等）
 * <p>
 * 配置说明：
 * - rt.metrics.batch-size: 每批处理的设备数量（默认50）
 * - rt.metrics.checkpoint-ttl-seconds: 检查点TTL（默认1小时）
 * - rt.metrics.timeout-millis: 任务超时时间（默认25分钟）
 * - rt.metrics.ttl-seconds: Redis中指标数据的TTL（默认600秒）
 * - 建议在XXL-Job中配置cron表达式，例如：每5分钟执行一次
 * <p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceRealtimeMetricsCalcJob extends BaseScheduledJob {

    private final IDeviceMetricsService deviceMetricsService;
    private final DeviceMetricsConfig config;

    @Override
    protected String getJobName() {
        return "设备实时指标计算任务";
    }

    @Override
    @XxlJob("deviceRealtimeMetricsCalcJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        long calculationTimeSeconds = System.currentTimeMillis() / 1000;

        XxlJobHelper.log("计算时间点: {}", Instant.ofEpochSecond(calculationTimeSeconds));

        BatchProcessResult result =
                deviceMetricsService.processAllDevicesWithCheckpoint(
                        calculationTimeSeconds,
                        config.getBatchSize(),
                        config.getTimeoutMillis());

        return JobExecutionResult.of(
                result.getSuccessCount(),
                result.getSkipCount(),
                result.getErrorCount());
    }
}

