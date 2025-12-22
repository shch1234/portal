package com.weili.iot_portal.task.device.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 设备实时指标计算任务配置
 * <p>
 * 所有配置项均在 Apollo 配置中心管理
 * Apollo 配置路径：rt.metrics.*
 * <p>
 * 配置项说明：
 * - rt.metrics.batch-size: 每批处理的设备数量
 * - rt.metrics.checkpoint-ttl-seconds: 检查点TTL（秒）
 * - rt.metrics.timeout-millis: 任务超时时间（毫秒）
 * - rt.metrics.ttl-seconds: Redis中指标数据的TTL（秒）
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "rt.metrics")
public class DeviceMetricsConfig {

    /**
     * 每批处理的设备数量
     * Apollo 配置：rt.metrics.batch-size
     */
    @NotNull(message = "rt.metrics.batch-size 未配置，将使用默认值 50")
    @Min(1)
    private Integer batchSize = 50;

    /**
     * 检查点TTL（秒）
     * Apollo 配置：rt.metrics.checkpoint-ttl-seconds
     */
    @NotNull(message = "rt.metrics.checkpoint-ttl-seconds 未配置，将使用默认值 3600")
    @Min(1)
    private Long checkpointTtlSeconds = 3600L;

    /**
     * 任务超时时间（毫秒）
     * Apollo 配置：rt.metrics.timeout-millis
     */
    @NotNull(message = "rt.metrics.timeout-millis 未配置，将使用默认值 1500000")
    @Min(1)
    private Long timeoutMillis = 1_500_000L; // 25分钟

    /**
     * Redis中指标数据的TTL（秒）
     * Apollo 配置：rt.metrics.ttl-seconds
     */
    @NotNull(message = "rt.metrics.ttl-seconds 未配置，将使用默认值 600")
    @Min(1)
    private Long ttlSeconds = 600L;
}

