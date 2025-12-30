package com.weili.iot_portal.task.device.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 设备产量汇总任务配置
 * Apollo 配置路径：production.summary.*
 *
 * 建议配置示例（Apollo）：
 * - production.summary.batch-size=50
 * - production.summary.checkpoint-ttl-seconds=86400
 * - production.summary.timeout-millis=1500000
 * - production.summary.lookback-days=7
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "production.summary")
public class DeviceProductionSummaryConfig {

    @NotNull(message = "production.summary.batch-size 未配置，将使用默认值 50")
    @Min(1)
    private Integer batchSize = 50;

    @NotNull(message = "production.summary.checkpoint-ttl-seconds 未配置，将使用默认值 86400")
    @Min(1)
    private Long checkpointTtlSeconds = 86_400L; // 24小时

    @NotNull(message = "production.summary.timeout-millis 未配置，将使用默认值 1500000")
    @Min(1)
    private Long timeoutMillis = 1_500_000L; // 25分钟

    @NotNull(message = "production.summary.lookback-days 未配置，将使用默认值 7")
    @Min(1)
    private Integer lookbackDays = 7; // 默认处理最近7天的数据
}

