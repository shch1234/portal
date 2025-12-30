package com.weili.iot_portal.task.factory;

import com.weili.iot_portal.domain.ingestion.BatchProcessResult;
import com.weili.iot_portal.service.factory.impl.FactoryShiftMetricsSummaryService;
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

    private final FactoryShiftMetricsSummaryService factoryShiftMetricsSummaryService;
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
        long statisticsTimeMillis = System.currentTimeMillis();

        XxlJobHelper.log("工厂班次指标汇总统计时间点: {}, 批量大小: {}, 超时时间: {} 毫秒, 处理时间范围: {} 天, 数据就绪延迟: {} 小时", 
                Instant.ofEpochMilli(statisticsTimeMillis),
                config.getBatchSize(),
                config.getTimeoutMillis(),
                config.getLookbackDays(),
                config.getDataReadyDelayHours());

        BatchProcessResult result =
                factoryShiftMetricsSummaryService.processAllFactoriesShiftSummaryWithCheckpoint(
                        statisticsTimeMillis / 1000,  // 转换为秒（服务层需要）
                        config.getBatchSize(),
                        config.getTimeoutMillis(),
                        config.getLookbackDays(),  // 传递回看天数参数
                        config.getDataReadyDelayHours());  // 传递数据就绪延迟参数

        return JobExecutionResult.of(
                result.getSuccessCount(),
                result.getSkipCount(),
                result.getErrorCount());
    }
}

