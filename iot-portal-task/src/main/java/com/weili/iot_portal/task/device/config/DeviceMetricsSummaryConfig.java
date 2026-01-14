package com.weili.iot_portal.task.device.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 设备班次指标汇总任务配置
 * Apollo 配置路径：metrics.summary.*
 *
 * 建议配置示例（Apollo）：
 * - metrics.summary.batch-size=50                # 每批处理设备数
 * - metrics.summary.checkpoint-ttl-seconds=86400 # 检查点TTL秒（默认1天）
 * - metrics.summary.timeout-millis=1500000       # 任务超时毫秒（默认25分钟）
 * - metrics.summary.lookback-days=7              # 处理时间范围（天），默认7天
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "metrics.summary")
public class DeviceMetricsSummaryConfig {

    @NotNull(message = "metrics.summary.batch-size 未配置，将使用默认值 50")
    @Min(1)
    private Integer batchSize = 50;

    @NotNull(message = "metrics.summary.checkpoint-ttl-seconds 未配置，将使用默认值 86400")
    @Min(1)
    private Long checkpointTtlSeconds = 86_400L; // 24小时

    @NotNull(message = "metrics.summary.timeout-millis 未配置，将使用默认值 1500000")
    @Min(1)
    private Long timeoutMillis = 1_500_000L; // 25分钟

    /**
     * 处理时间范围（天）
     * 只处理当前时间往前推N天内的数据，用于限制处理范围，避免处理过多历史数据
     * Apollo 配置：metrics.summary.lookback-days
     * 注意：此字段有默认值，即使 Apollo 未配置也不会报错
     */
    @Min(1)
    private Integer lookbackDays = 7; // 默认7天，即使 Apollo 未配置也不会报错

    /**
     * 数据就绪延迟时间（小时）
     * 只处理班次结束时间在统计时间点之前至少N小时的班次，确保上游任务（状态汇总、产量汇总）有足够时间完成数据生成
     * Apollo 配置：metrics.summary.data-ready-delay-hours
     * 默认值：2小时（可根据上游任务执行频率和数据处理耗时调整）
     * 注意：此字段有默认值，即使 Apollo 未配置也不会报错
     * 
     * 示例：
     * - 当前时间：15:00
     * - dataReadyDelayHours = 2
     * - 只处理班次结束时间 <= 13:00 的班次
     */
    @Min(0)
    private Integer dataReadyDelayHours = 2; // 默认2小时，即使 Apollo 未配置也不会报错

    /**
     * 数据不完整记录的重算间隔时间（小时）
     * 对于标记为 INCOMPLETE_DATA 的记录，如果距离上次计算时间超过此间隔，即使状态汇总未更新，也会重新计算
     * Apollo 配置：metrics.summary.recalculation-interval-hours
     * 默认值：1小时（可根据实际情况调整）
     * 注意：此字段有默认值，即使 Apollo 未配置也不会报错
     * 
     * 示例：
     * - 记录标记为 INCOMPLETE_DATA，上次计算时间：10:00
     * - 当前时间：11:30，recalculationIntervalHours = 1
     * - 距离上次计算已超过1小时，会重新计算
     */
    @Min(0)
    private Integer recalculationIntervalHours = 1; // 默认1小时，即使 Apollo 未配置也不会报错
}

