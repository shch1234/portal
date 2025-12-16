package com.weili.iot_portal.service.device.config;

import com.weili.basic.redis.client.RedisClient;
import com.weili.iot_portal.service.device.ICheckpointService;
import com.weili.iot_portal.service.device.impl.GenericCheckpointService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 检查点服务配置
 * 为不同业务场景创建不同的检查点服务实例
 */
@Configuration
public class CheckpointServiceConfig {

    /**
     * 设备状态汇总检查点服务
     */
    @Bean("deviceStateSummaryCheckpointService")
    public ICheckpointService<ICheckpointService.CheckpointData> deviceStateSummaryCheckpointService(
            RedisClient redisClient,
            @Value("${shift.summary.checkpoint-ttl-seconds:86400}") long ttlSeconds) {
        return new GenericCheckpointService(
                redisClient,
                "device_state_summary:checkpoint:",
                ttlSeconds
        );
    }

    /**
     * 设备指标计算检查点服务
     */
    @Bean("deviceMetricsCheckpointService")
    public ICheckpointService<ICheckpointService.CheckpointData> deviceMetricsCheckpointService(
            RedisClient redisClient,
            @Value("${rt.metrics.checkpoint-ttl-seconds:3600}") long ttlSeconds) {
        return new GenericCheckpointService(
                redisClient,
                "device_metrics:checkpoint:",
                ttlSeconds
        );
    }

    /**
     * 设备指标汇总检查点服务
     */
    @Bean("deviceMetricsSummaryCheckpointService")
    public ICheckpointService<ICheckpointService.CheckpointData> deviceMetricsSummaryCheckpointService(
            RedisClient redisClient,
            @Value("${metrics.summary.checkpoint-ttl-seconds:86400}") long ttlSeconds) {
        return new GenericCheckpointService(
                redisClient,
                "device_metrics_summary:checkpoint:",
                ttlSeconds
        );
    }

    /**
     * 设备产量汇总检查点服务
     */
    @Bean("deviceProductionSummaryCheckpointService")
    public ICheckpointService<ICheckpointService.CheckpointData> deviceProductionSummaryCheckpointService(
            RedisClient redisClient,
            @Value("${production.summary.checkpoint-ttl-seconds:86400}") long ttlSeconds) {
        return new GenericCheckpointService(
                redisClient,
                "device_production_summary:checkpoint:",
                ttlSeconds
        );
    }
}

