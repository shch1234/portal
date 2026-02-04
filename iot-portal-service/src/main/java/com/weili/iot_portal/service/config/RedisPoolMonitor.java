package com.weili.iot_portal.service.config;

import io.lettuce.core.support.ConnectionPoolSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
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
    
    @Autowired(required = false)
    private Environment environment;

    private GenericObjectPool<?> pool;
    private boolean poolAvailable = false;

    @PostConstruct
    public void init() {
        if (connectionFactory == null) {
            log.warn("[RedisPoolMonitor] LettuceConnectionFactory未找到，监控功能不可用");
            return;
        }

        try {
            // 打印配置文件中读取的配置值
            printConfigurationFromProperties();
            
            // 通过反射获取连接池对象
            pool = extractPoolFromFactory(connectionFactory);
            if (pool != null) {
                poolAvailable = true;
                
                // 验证并记录连接池配置（实际生效的值）
                int maxTotal = pool.getMaxTotal();
                int maxIdle = pool.getMaxIdle();
                int minIdle = pool.getMinIdle();
                long maxWaitMillis = pool.getMaxWaitMillis();
                
                log.info("[RedisPoolMonitor] ========== Redis 连接池配置验证 ==========");
                log.info("[RedisPoolMonitor] 【实际生效的连接池配置（从连接池对象读取）】");
                log.info("[RedisPoolMonitor]   max-active (maxTotal) = {}", maxTotal);
                log.info("[RedisPoolMonitor]   max-idle (maxIdle) = {}", maxIdle);
                log.info("[RedisPoolMonitor]   min-idle (minIdle) = {}", minIdle);
                log.info("[RedisPoolMonitor]   max-wait (maxWaitMillis) = {}ms", maxWaitMillis);
                
                // 对比配置值和实际值
                String configMaxActive = environment != null ? 
                    environment.getProperty("spring.data.redis.lettuce.pool.max-active", "未配置") : "未配置";
                String configMaxIdle = environment != null ? 
                    environment.getProperty("spring.data.redis.lettuce.pool.max-idle", "未配置") : "未配置";
                String configMinIdle = environment != null ? 
                    environment.getProperty("spring.data.redis.lettuce.pool.min-idle", "未配置") : "未配置";
                String configMaxWait = environment != null ? 
                    environment.getProperty("spring.data.redis.lettuce.pool.max-wait", "未配置") : "未配置";
                
                log.info("[RedisPoolMonitor] 【配置对比】");
                log.info("[RedisPoolMonitor]   配置项                    | 配置文件值      | 实际生效值");
                log.info("[RedisPoolMonitor]   max-active                | {} | {}", 
                        String.format("%-15s", configMaxActive), maxTotal);
                log.info("[RedisPoolMonitor]   max-idle                  | {} | {}", 
                        String.format("%-15s", configMaxIdle), maxIdle);
                log.info("[RedisPoolMonitor]   min-idle                  | {} | {}", 
                        String.format("%-15s", configMinIdle), minIdle);
                log.info("[RedisPoolMonitor]   max-wait                  | {} | {}ms", 
                        String.format("%-15s", configMaxWait), maxWaitMillis);
                
                // 验证配置是否匹配
                boolean configMatch = true;
                if (!"未配置".equals(configMaxActive) && !String.valueOf(maxTotal).equals(configMaxActive)) {
                    log.warn("[RedisPoolMonitor] ⚠️ max-active 配置不匹配: 配置文件={}, 实际值={}", configMaxActive, maxTotal);
                    configMatch = false;
                }
                if (!"未配置".equals(configMaxIdle) && !String.valueOf(maxIdle).equals(configMaxIdle)) {
                    log.warn("[RedisPoolMonitor] ⚠️ max-idle 配置不匹配: 配置文件={}, 实际值={}", configMaxIdle, maxIdle);
                    configMatch = false;
                }
                if (!"未配置".equals(configMinIdle) && !String.valueOf(minIdle).equals(configMinIdle)) {
                    log.warn("[RedisPoolMonitor] ⚠️ min-idle 配置不匹配: 配置文件={}, 实际值={}", configMinIdle, minIdle);
                    configMatch = false;
                }
                if (!"未配置".equals(configMaxWait) && !String.valueOf(maxWaitMillis).equals(configMaxWait)) {
                    log.warn("[RedisPoolMonitor] ⚠️ max-wait 配置不匹配: 配置文件={}, 实际值={}ms", configMaxWait, maxWaitMillis);
                    configMatch = false;
                }
                
                if (configMatch && !"未配置".equals(configMaxActive)) {
                    log.info("[RedisPoolMonitor] ✅ 配置值与实际值匹配");
                } else if ("未配置".equals(configMaxActive)) {
                    log.warn("[RedisPoolMonitor] ⚠️ 配置文件中未找到连接池配置，使用默认值");
                }
                
                log.info("[RedisPoolMonitor] ==========================================");
                
                // 验证配置是否正确
                if (maxWaitMillis < 1000) {
                    log.error("[RedisPoolMonitor] ⚠️ 连接池 max-wait 配置异常: {}ms，期望 >= 1000ms（当前配置可能导致连接获取超时）", 
                            maxWaitMillis);
                }
                if (maxTotal < 100) {
                    log.warn("[RedisPoolMonitor] ⚠️ 连接池 max-active 配置较小: {}，建议 >= 100", maxTotal);
                }
                if (minIdle < 10) {
                    log.warn("[RedisPoolMonitor] ⚠️ 连接池 min-idle 配置较小: {}，建议 >= 10", minIdle);
                }
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
            
            // 尝试获取等待线程数（如果可用）
            int numWaiters = 0;
            try {
                numWaiters = pool.getNumWaiters();
            } catch (Exception e) {
                // 某些版本的连接池可能不支持此方法
            }

            // 计算使用率
            double usageRate = maxTotal > 0 ? (double) active / maxTotal * 100 : 0;

            // 记录指标
            log.info("[RedisPoolMonitor] 连接池状态: active={}, idle={}, total={}, maxTotal={}, usage={}%, maxWait={}ms, waiters={}",
                    active, idle, total, maxTotal, String.format("%.2f", usageRate), maxWaitMillis, numWaiters);

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
            
            // 告警：如果连接池使用率超过90%
            if (usageRate > 90) {
                log.error("[RedisPoolMonitor] 🚨 连接池使用率超过90%: active={}, maxTotal={}, usage={}%",
                        active, maxTotal, String.format("%.2f", usageRate));
            }

            // 告警：如果等待线程数过多
            if (numWaiters > 10) {
                log.error("[RedisPoolMonitor] 🚨 连接池等待线程数过多: waiters={}, active={}, maxTotal={}",
                        numWaiters, active, maxTotal);
            }

            // 告警：如果空闲连接数为0且活跃连接数较高
            if (idle == 0 && active > maxTotal * 0.5) {
                log.warn("[RedisPoolMonitor] ⚠️ 连接池空闲连接数为0: active={}, idle={}, maxTotal={}",
                        active, idle, maxTotal);
            }
            
            // 告警：如果 max-wait 配置异常
            if (maxWaitMillis < 1000) {
                log.error("[RedisPoolMonitor] 🚨 连接池 max-wait 配置异常: {}ms，期望 >= 1000ms（可能导致连接获取超时）",
                        maxWaitMillis);
            }

        } catch (Exception e) {
            log.error("[RedisPoolMonitor] 监控连接池时发生异常", e);
        }
    }

    /**
     * 打印配置文件中读取的配置值
     */
    private void printConfigurationFromProperties() {
        if (environment == null) {
            log.warn("[RedisPoolMonitor] Environment未注入，无法读取配置值");
            return;
        }
        
        log.info("[RedisPoolMonitor] ========== Redis 配置值验证 ==========");
        log.info("[RedisPoolMonitor] 【从配置文件读取的配置值】");
        
        // Redis 基本配置
        String host = environment.getProperty("spring.data.redis.host", "未配置");
        String port = environment.getProperty("spring.data.redis.port", "未配置");
        String password = environment.getProperty("spring.data.redis.password");
        if (password != null && !password.isEmpty()) {
            password = "***已配置***";
        } else {
            password = "未配置";
        }
        String timeout = environment.getProperty("spring.data.redis.timeout", "未配置");
        
        log.info("[RedisPoolMonitor]   spring.data.redis.host = {}", host);
        log.info("[RedisPoolMonitor]   spring.data.redis.port = {}", port);
        log.info("[RedisPoolMonitor]   spring.data.redis.password = {}", password);
        log.info("[RedisPoolMonitor]   spring.data.redis.timeout = {}", timeout);
        
        // Lettuce 连接池配置
        String maxActive = environment.getProperty("spring.data.redis.lettuce.pool.max-active", "未配置");
        String maxIdle = environment.getProperty("spring.data.redis.lettuce.pool.max-idle", "未配置");
        String minIdle = environment.getProperty("spring.data.redis.lettuce.pool.min-idle", "未配置");
        String maxWait = environment.getProperty("spring.data.redis.lettuce.pool.max-wait", "未配置");
        
        log.info("[RedisPoolMonitor]   spring.data.redis.lettuce.pool.max-active = {}", maxActive);
        log.info("[RedisPoolMonitor]   spring.data.redis.lettuce.pool.max-idle = {}", maxIdle);
        log.info("[RedisPoolMonitor]   spring.data.redis.lettuce.pool.min-idle = {}", minIdle);
        log.info("[RedisPoolMonitor]   spring.data.redis.lettuce.pool.max-wait = {}", maxWait);
        
        // 兼容格式检查（spring.redis.*）
        String legacyMaxActive = environment.getProperty("spring.redis.lettuce.pool.max-active", (String) null);
        String legacyMaxIdle = environment.getProperty("spring.redis.lettuce.pool.max-idle", (String) null);
        String legacyMinIdle = environment.getProperty("spring.redis.lettuce.pool.min-idle", (String) null);
        String legacyMaxWait = environment.getProperty("spring.redis.lettuce.pool.max-wait", (String) null);
        String legacyTimeout = environment.getProperty("spring.redis.timeout", (String) null);
        
        if (legacyMaxActive != null || legacyMaxIdle != null || legacyMinIdle != null || legacyMaxWait != null || legacyTimeout != null) {
            log.warn("[RedisPoolMonitor] ⚠️ 检测到使用旧格式配置（spring.redis.*），建议使用新格式（spring.data.redis.*）");
            if (legacyMaxActive != null) {
                log.warn("[RedisPoolMonitor]   spring.redis.lettuce.pool.max-active = {} (旧格式)", legacyMaxActive);
            }
            if (legacyMaxIdle != null) {
                log.warn("[RedisPoolMonitor]   spring.redis.lettuce.pool.max-idle = {} (旧格式)", legacyMaxIdle);
            }
            if (legacyMinIdle != null) {
                log.warn("[RedisPoolMonitor]   spring.redis.lettuce.pool.min-idle = {} (旧格式)", legacyMinIdle);
            }
            if (legacyMaxWait != null) {
                log.warn("[RedisPoolMonitor]   spring.redis.lettuce.pool.max-wait = {} (旧格式)", legacyMaxWait);
            }
            if (legacyTimeout != null) {
                log.warn("[RedisPoolMonitor]   spring.redis.timeout = {} (旧格式)", legacyTimeout);
            }
        }
        
        log.info("[RedisPoolMonitor] ======================================");
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
