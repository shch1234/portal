package com.weili.iot_portal.task.device.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 设备离线纠正任务配置
 * <p>
 * Apollo 配置前缀：rt.offline.*
 * <p>
 * 配置项说明：
 * - rt.offline.batch-size: 每批处理的设备数量
 * - rt.offline.timeout-millis: 任务超时时间（毫秒）
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "rt.offline")
public class DeviceOfflineReconcileConfig {

    /**
     * 每批处理的设备数量
     * Apollo 配置：rt.offline.batch-size
     */
    @NotNull(message = "rt.offline.batch-size 未配置，将使用默认值 100")
    @Min(1)
    private Integer batchSize = 100;

    /**
     * 任务超时时间（毫秒）
     * Apollo 配置：rt.offline.timeout-millis
     */
    @NotNull(message = "rt.offline.timeout-millis 未配置，将使用默认值 600000")
    @Min(1)
    private Long timeoutMillis = 600_000L; // 10分钟
}

