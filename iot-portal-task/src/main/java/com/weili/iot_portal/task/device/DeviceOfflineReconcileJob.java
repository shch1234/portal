package com.weili.iot_portal.task.device;

import com.weili.iot_portal.domain.ingestion.BatchProcessResult;
import com.weili.iot_portal.service.device.IDeviceOfflineReconciliationService;
import com.weili.iot_portal.task.device.config.DeviceOfflineReconcileConfig;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 设备离线纠正定时任务
 * <p>
 * 功能：基于心跳状态定期扫描设备，对离线设备的状态时间线进行纠正：
 * - 结束正在进行中的状态（标记为异常结束）；
 * - 插入 UNKNOWN(255) 状态表示离线期间。
 * <p>
 * 调度建议：
 * - 在 XXL-Job 中配置 cron 表达式为每2分钟执行一次，例如：0 0/2 * * * ?
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceOfflineReconcileJob extends BaseScheduledJob {

    private final IDeviceOfflineReconciliationService offlineReconciliationService;
    private final DeviceOfflineReconcileConfig config;

    @Override
    protected String getJobName() {
        return "设备离线纠正任务";
    }

    /**
     * XXL-Job 执行入口
     */
    @Override
    @XxlJob("deviceOfflineReconcileJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        long now = System.currentTimeMillis();
        BatchProcessResult result = offlineReconciliationService.processAllDevices(
                now,
                config.getBatchSize(),
                config.getTimeoutMillis());

        log.info("[DeviceOfflineReconcileJob] 执行完成: success={}, skip={}, error={}",
                result.getSuccessCount(), result.getSkipCount(), result.getErrorCount());

        return JobExecutionResult.of(
                result.getSuccessCount(),
                result.getSkipCount(),
                result.getErrorCount());
    }
}

