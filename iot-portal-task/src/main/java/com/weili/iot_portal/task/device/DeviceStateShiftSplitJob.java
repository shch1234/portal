package com.weili.iot_portal.task.device;

import com.weili.iot_portal.service.device.IDeviceStateShiftSplitService;
import com.weili.iot_portal.task.device.config.DeviceStateShiftSplitConfig;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 设备状态跨班次拆分定时任务
 * <p>
 * 功能：定期检查进行中的状态记录，如果跨班次则自动拆分
 * </p>
 * <p>
 * 配置说明：
 * - device.state.shift.split.batch-size: 每批处理的记录数量（默认100）
 * - device.state.shift.split.start-ts-after-hours: 只处理开始时间在此时间之后的记录（小时，默认24小时前）
 * - device.state.shift.split.boundary-window-minutes: 班次边界时间窗口（分钟，默认30分钟）
 * - device.state.shift.split.boundary-times: 班次边界时间列表（HH:mm格式，多个用逗号分隔，默认08:00,20:00）
 * - device.state.shift.split.quick-return-threshold-minutes: 快速返回阈值（分钟，默认5分钟）
 * </p>
 * <p>
 * 执行策略：
 * - 在班次边界时间窗口内（如8:00前后30分钟）：执行完整逻辑
 * - 在班次边界时间窗口外：可以快速返回（如果距离上次执行时间较近）
 * - 建议在XXL-Job中配置高频cron表达式，例如：每2-3分钟执行一次
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceStateShiftSplitJob extends BaseScheduledJob {

    private final IDeviceStateShiftSplitService shiftSplitService;
    private final DeviceStateShiftSplitConfig config;

    // 记录上次执行时间，用于快速返回判断
    private volatile long lastExecuteTime = 0;

    @Override
    protected String getJobName() {
        return "设备状态跨班次拆分任务";
    }

    @Override
    @XxlJob("deviceStateShiftSplitJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        long currentTime = System.currentTimeMillis();
        LocalTime currentLocalTime = LocalTime.now(ZoneId.systemDefault());

        // 判断是否在班次边界时间窗口内
        boolean isNearBoundary = config.isNearBoundaryTime(currentLocalTime);

        // 如果不在边界窗口内，且距离上次执行时间较近，可以快速返回
        if (!isNearBoundary) {
            long timeSinceLastExecute = currentTime - lastExecuteTime;
            long quickReturnThresholdMs = config.getQuickReturnThresholdMinutes() * 60 * 1000L;

            if (timeSinceLastExecute < quickReturnThresholdMs && lastExecuteTime > 0) {
                XxlJobHelper.log("不在班次边界窗口内，且距离上次执行时间较近（{}分钟），快速返回", 
                        timeSinceLastExecute / (60 * 1000));
                return JobExecutionResult.of(0, 0, 0); // 快速返回，不处理
            }
        }

        // 更新上次执行时间
        lastExecuteTime = currentTime;

        Long startTsAfter = config.getStartTsAfter();
        Integer batchSize = config.getBatchSize();

        if (isNearBoundary) {
            XxlJobHelper.log("在班次边界时间窗口内，执行完整逻辑: currentTime={}, startTsAfter={}, batchSize={}", 
                    currentLocalTime, startTsAfter, batchSize);
        } else {
            XxlJobHelper.log("不在班次边界时间窗口内，执行完整逻辑: currentTime={}, startTsAfter={}, batchSize={}", 
                    currentLocalTime, startTsAfter, batchSize);
        }

        var result = shiftSplitService.processCrossShiftSplit(startTsAfter, batchSize);

        XxlJobHelper.log("跨班次拆分处理完成: 总处理={}, 拆分={}, 跳过={}, 错误={}", 
                result.totalProcessed(), result.splitCount(), result.skipCount(), result.errorCount());

        return JobExecutionResult.of(
                result.splitCount(),      // 成功数 = 拆分数
                result.skipCount(),       // 跳过数
                result.errorCount()      // 错误数
        );
    }
}
