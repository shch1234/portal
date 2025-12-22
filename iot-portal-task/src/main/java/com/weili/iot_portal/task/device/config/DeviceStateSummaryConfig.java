package com.weili.iot_portal.task.device.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 设备状态汇总任务配置
 * <p>
 * 所有配置项均在 Apollo 配置中心管理
 * Apollo 配置路径：shift.summary.*
 * <p>
 * 配置项说明：
 * - shift.summary.batch-size: 每批处理的设备数量
 * - shift.summary.checkpoint-ttl-seconds: 检查点TTL（秒）
 * - shift.summary.timeout-millis: 任务超时时间（毫秒）
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "shift.summary")
public class DeviceStateSummaryConfig {

    /**
     * 每批处理的设备数量
     * Apollo 配置：shift.summary.batch-size
     */
    @NotNull(message = "shift.summary.batch-size 未配置，将使用默认值 50")
    @Min(1)
    private Integer batchSize = 50;

    /**
     * 检查点TTL（秒）
     * Apollo 配置：shift.summary.checkpoint-ttl-seconds
     */
    @NotNull(message = "shift.summary.checkpoint-ttl-seconds 未配置，将使用默认值 86400")
    @Min(1)
    private Long checkpointTtlSeconds = 86_400L; // 24小时

    /**
     * 任务超时时间（毫秒）
     * Apollo 配置：shift.summary.timeout-millis
     */
    @NotNull(message = "shift.summary.timeout-millis 未配置，将使用默认值 1500000")
    @Min(1)
    private Long timeoutMillis = 1_500_000L; // 25分钟
}

