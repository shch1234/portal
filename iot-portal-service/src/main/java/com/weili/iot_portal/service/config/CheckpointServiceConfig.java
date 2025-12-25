package com.weili.iot_portal.service.config;

import com.weili.basic.redis.client.RedisClient;
import com.weili.iot_portal.domain.ingestion.CheckpointData;
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
    public ICheckpointService<CheckpointData> deviceStateSummaryCheckpointService(
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
    public ICheckpointService<CheckpointData> deviceMetricsCheckpointService(
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
    public ICheckpointService<CheckpointData> deviceMetricsSummaryCheckpointService(
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
    public ICheckpointService<CheckpointData> deviceProductionSummaryCheckpointService(
            RedisClient redisClient,
            @Value("${production.summary.checkpoint-ttl-seconds:86400}") long ttlSeconds) {
        return new GenericCheckpointService(
                redisClient,
                "device_production_summary:checkpoint:",
                ttlSeconds
        );
    }

    /**
     * 工厂实时指标检查点服务
     */
    @Bean("factoryMetricsCheckpointService")
    public ICheckpointService<CheckpointData> factoryMetricsCheckpointService(
            RedisClient redisClient,
            @Value("${factory.metrics.checkpoint-ttl-seconds:3600}") long ttlSeconds) {
        return new GenericCheckpointService(
                redisClient,
                "factory_metrics:checkpoint:",
                ttlSeconds
        );
    }

    /**
     * 工厂班次指标汇总检查点服务
     */
    @Bean("factoryMetricsSummaryCheckpointService")
    public ICheckpointService<CheckpointData> factoryMetricsSummaryCheckpointService(
            RedisClient redisClient,
            @Value("${factory.metrics.summary.checkpoint-ttl-seconds:86400}") long ttlSeconds) {
        return new GenericCheckpointService(
                redisClient,
                "factory_metrics_summary:checkpoint:",
                ttlSeconds
        );
    }
}

