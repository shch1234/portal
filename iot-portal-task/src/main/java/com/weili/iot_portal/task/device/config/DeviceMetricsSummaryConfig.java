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
}

