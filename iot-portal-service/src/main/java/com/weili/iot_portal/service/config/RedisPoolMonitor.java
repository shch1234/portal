package com.weili.iot_portal.service.config;

import io.lettuce.core.support.ConnectionPoolSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Field;

/**
 * Redis连接池监控组件
 * <p>
 * 定期监控连接池状态，记录指标并告警
 * </p>
 * <p>
 * 监控指标：
 * - 活跃连接数
 * - 空闲连接数
 * - 等待线程数（如果可用）
 * - 连接池使用率
 * </p>
 *
 * @author system
 */
@Slf4j
@Component
public class RedisPoolMonitor {

    @Autowired(required = false)
    private LettuceConnectionFactory connectionFactory;

    private GenericObjectPool<?> pool;
    private boolean poolAvailable = false;

    @PostConstruct
    public void init() {
        if (connectionFactory == null) {
            log.warn("[RedisPoolMonitor] LettuceConnectionFactory未找到，监控功能不可用");
            return;
        }

        try {
            // 通过反射获取连接池对象
            pool = extractPoolFromFactory(connectionFactory);
            if (pool != null) {
                poolAvailable = true;
                log.info("[RedisPoolMonitor] 初始化成功，开始监控Redis连接池");
            } else {
                log.warn("[RedisPoolMonitor] 无法获取连接池对象，监控功能不可用");
            }
        } catch (Exception e) {
            log.warn("[RedisPoolMonitor] 初始化失败，监控功能不可用", e);
        }
    }

    /**
     * 定期监控连接池状态（每分钟执行一次）
     */
    @Scheduled(fixedRate = 60000) // 60秒
    public void monitorPool() {
        if (!poolAvailable || pool == null) {
            return;
        }

        try {
            int active = pool.getNumActive();
            int idle = pool.getNumIdle();
            int total = active + idle;
            int maxTotal = pool.getMaxTotal();
            long maxWaitMillis = pool.getMaxWaitMillis();

            // 计算使用率
            double usageRate = maxTotal > 0 ? (double) active / maxTotal * 100 : 0;

            // 记录指标
            log.info("[RedisPoolMonitor] 连接池状态: active={}, idle={}, total={}, maxTotal={}, usage={}%, maxWait={}ms",
                    active, idle, total, maxTotal, String.format("%.2f", usageRate), maxWaitMillis);

            // 告警：如果活跃连接数超过80%
            if (usageRate > 80) {
                log.warn("[RedisPoolMonitor] ⚠️ 连接池使用率过高: active={}, maxTotal={}, usage={}%",
                        active, maxTotal, String.format("%.2f", usageRate));
            }

            // 告警：如果活跃连接数接近最大值
            if (active >= maxTotal * 0.9) {
                log.error("[RedisPoolMonitor] 🚨 连接池即将耗尽: active={}, maxTotal={}, usage={}%",
                        active, maxTotal, String.format("%.2f", usageRate));
            }

            // 告警：如果空闲连接数为0且活跃连接数较高
            if (idle == 0 && active > maxTotal * 0.5) {
                log.warn("[RedisPoolMonitor] ⚠️ 连接池空闲连接数为0: active={}, idle={}, maxTotal={}",
                        active, idle, maxTotal);
            }

        } catch (Exception e) {
            log.error("[RedisPoolMonitor] 监控连接池时发生异常", e);
        }
    }

    /**
     * 通过反射从LettuceConnectionFactory中提取连接池对象
     */
    private GenericObjectPool<?> extractPoolFromFactory(LettuceConnectionFactory factory) {
        try {
            // LettuceConnectionFactory内部使用LettucePoolingConnectionProvider
            // 需要通过反射获取pool对象
            Field providerField = LettuceConnectionFactory.class.getDeclaredField("connectionProvider");
            providerField.setAccessible(true);
            Object provider = providerField.get(factory);

            if (provider == null) {
                return null;
            }

            // 获取LettucePoolingConnectionProvider的pool字段
            Field poolField = provider.getClass().getDeclaredField("pool");
            poolField.setAccessible(true);
            Object poolObj = poolField.get(provider);

            if (poolObj instanceof GenericObjectPool) {
                return (GenericObjectPool<?>) poolObj;
            }

            // 如果直接获取失败，尝试通过ConnectionPoolSupport获取
            // ConnectionPoolSupport内部使用GenericObjectPool
            if (provider.getClass().getName().contains("LettucePoolingConnectionProvider")) {
                // 尝试多种方式获取pool
                for (Field field : provider.getClass().getDeclaredFields()) {
                    if (GenericObjectPool.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object obj = field.get(provider);
                        if (obj instanceof GenericObjectPool) {
                            return (GenericObjectPool<?>) obj;
                        }
                    }
                }
            }

            return null;
        } catch (Exception e) {
            log.debug("[RedisPoolMonitor] 提取连接池对象失败（可能使用非池化连接）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取连接池状态（用于健康检查）
     */
    public PoolStatus getPoolStatus() {
        if (!poolAvailable || pool == null) {
            return PoolStatus.unavailable();
        }

        try {
            int active = pool.getNumActive();
            int idle = pool.getNumIdle();
            int maxTotal = pool.getMaxTotal();
            double usageRate = maxTotal > 0 ? (double) active / maxTotal * 100 : 0;

            return PoolStatus.builder()
                    .active(active)
                    .idle(idle)
                    .maxTotal(maxTotal)
                    .usageRate(usageRate)
                    .available(true)
                    .build();
        } catch (Exception e) {
            log.error("[RedisPoolMonitor] 获取连接池状态失败", e);
            return PoolStatus.unavailable();
        }
    }

    /**
     * 连接池状态
     */
    public static class PoolStatus {
        private int active;
        private int idle;
        private int maxTotal;
        private double usageRate;
        private boolean available;

        public static PoolStatus unavailable() {
            PoolStatus status = new PoolStatus();
            status.available = false;
            return status;
        }

        public static PoolStatusBuilder builder() {
            return new PoolStatusBuilder();
        }

        // Getters
        public int getActive() { return active; }
        public int getIdle() { return idle; }
        public int getMaxTotal() { return maxTotal; }
        public double getUsageRate() { return usageRate; }
        public boolean isAvailable() { return available; }

        public static class PoolStatusBuilder {
            private PoolStatus status = new PoolStatus();

            public PoolStatusBuilder active(int active) {
                status.active = active;
                return this;
            }

            public PoolStatusBuilder idle(int idle) {
                status.idle = idle;
                return this;
            }

            public PoolStatusBuilder maxTotal(int maxTotal) {
                status.maxTotal = maxTotal;
                return this;
            }

            public PoolStatusBuilder usageRate(double usageRate) {
                status.usageRate = usageRate;
                return this;
            }

            public PoolStatusBuilder available(boolean available) {
                status.available = available;
                return this;
            }

            public PoolStatus build() {
                return status;
            }
        }
    }
}
