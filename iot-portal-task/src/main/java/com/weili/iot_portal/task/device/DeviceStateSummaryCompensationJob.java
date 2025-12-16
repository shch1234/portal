package com.weili.iot_portal.task.device;

import com.weili.iot_portal.service.device.IDeviceShiftSummaryService;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 设备状态汇总补偿任务
 * <p>
 * 功能：扫描最近N天未完成的汇总记录，重新统计并补齐
 * <p>
 * 配置说明：
 * - shift.summary.compensation.days: 扫描最近N天的未处理记录（默认7天）
 * - 建议在XXL-Job中配置cron表达式，例如：每小时执行一次
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceStateSummaryCompensationJob extends BaseScheduledJob {

    @Value("${shift.summary.compensation.days:7}")
    private int compensationDays;

    private final IDeviceShiftSummaryService shiftSummaryService;

    @Override
    @XxlJob("deviceStateSummaryCompensationJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected String getJobName() {
        return "设备状态汇总补偿任务";
    }

    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        IDeviceShiftSummaryService.CompensationResult result =
                shiftSummaryService.compensatePendingSummaries(compensationDays);
        return JobExecutionResult.of(result.getSuccessCount(), result.getSkipCount(), result.getErrorCount());
    }
}

