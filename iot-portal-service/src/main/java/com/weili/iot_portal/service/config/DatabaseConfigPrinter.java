package com.weili.iot_portal.service.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.Map;

/**
 * 数据库配置打印组件
 * <p>
 * 在应用启动时打印数据库连接池配置信息，用于验证 Apollo 配置是否生效
 * </p>
 *
 * @author system
 */
@Slf4j
@Component
public class DatabaseConfigPrinter {

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private Environment environment;

    @PostConstruct
    public void printDatabaseConfig() {
        if (dataSource == null) {
            log.warn("[DatabaseConfig] DataSource未找到，无法打印配置");
            return;
        }

        log.info("[DatabaseConfig] ========== 数据库连接池配置信息 ==========");

        // 打印 Apollo 配置值
        if (environment != null) {
            printApolloConfig();
        }

        // 打印实际生效的配置
        printActualConfig();

        log.info("[DatabaseConfig] ==========================================");
    }

    /**
     * 打印 Apollo 配置值
     */
    private void printApolloConfig() {
        log.info("[DatabaseConfig] 【Apollo 配置值】");
        
        // 主数据源配置
        String masterDatabase = environment.getProperty("spring.datasource.master.database", "未配置");
        log.info("[DatabaseConfig]   spring.datasource.master.database = {}", masterDatabase);
        
        // HikariCP 配置
        String maxPoolSize = environment.getProperty("spring.datasource.hikari.maximum-pool-size", "未配置");
        String minIdle = environment.getProperty("spring.datasource.hikari.minimum-idle", "未配置");
        String connectionTimeout = environment.getProperty("spring.datasource.hikari.connection-timeout", "未配置");
        String maxLifetime = environment.getProperty("spring.datasource.hikari.max-lifetime", "未配置");
        String idleTimeout = environment.getProperty("spring.datasource.hikari.idle-timeout", "未配置");
        String validationTimeout = environment.getProperty("spring.datasource.hikari.validation-timeout", "未配置");
        String leakDetectionThreshold = environment.getProperty("spring.datasource.hikari.leak-detection-threshold", "未配置");
        
        log.info("[DatabaseConfig]   spring.datasource.hikari.maximum-pool-size = {}", maxPoolSize);
        log.info("[DatabaseConfig]   spring.datasource.hikari.minimum-idle = {}", minIdle);
        log.info("[DatabaseConfig]   spring.datasource.hikari.connection-timeout = {}ms", connectionTimeout);
        log.info("[DatabaseConfig]   spring.datasource.hikari.max-lifetime = {}ms", maxLifetime);
        log.info("[DatabaseConfig]   spring.datasource.hikari.idle-timeout = {}ms", idleTimeout);
        log.info("[DatabaseConfig]   spring.datasource.hikari.validation-timeout = {}ms", validationTimeout);
        log.info("[DatabaseConfig]   spring.datasource.hikari.leak-detection-threshold = {}ms", leakDetectionThreshold);
    }

    /**
     * 打印实际生效的配置
     */
    private void printActualConfig() {
        HikariDataSource hikariDataSource = extractHikariDataSource(dataSource);
        if (hikariDataSource == null) {
            log.warn("[DatabaseConfig] 无法提取 HikariCP 数据源，跳过实际配置打印");
            return;
        }

        log.info("[DatabaseConfig] 【实际生效的配置】");
        
        try {
            String poolName = hikariDataSource.getPoolName();
            int maximumPoolSize = hikariDataSource.getMaximumPoolSize();
            int minimumIdle = hikariDataSource.getMinimumIdle();
            long connectionTimeout = hikariDataSource.getConnectionTimeout();
            long maxLifetime = hikariDataSource.getMaxLifetime();
            long idleTimeout = hikariDataSource.getIdleTimeout();
            long validationTimeout = hikariDataSource.getValidationTimeout();
            long leakDetectionThreshold = hikariDataSource.getLeakDetectionThreshold();
            
            log.info("[DatabaseConfig]   Pool Name: {}", poolName);
            log.info("[DatabaseConfig]   maximum-pool-size = {}", maximumPoolSize);
            log.info("[DatabaseConfig]   minimum-idle = {}", minimumIdle);
            log.info("[DatabaseConfig]   connection-timeout = {}ms", connectionTimeout);
            log.info("[DatabaseConfig]   max-lifetime = {}ms", maxLifetime);
            log.info("[DatabaseConfig]   idle-timeout = {}ms", idleTimeout);
            log.info("[DatabaseConfig]   validation-timeout = {}ms", validationTimeout);
            log.info("[DatabaseConfig]   leak-detection-threshold = {}ms", leakDetectionThreshold);
            
            // 配置对比
            if (environment != null) {
                log.info("[DatabaseConfig] 【配置对比】");
                compareConfig("maximum-pool-size", 
                        environment.getProperty("spring.datasource.hikari.maximum-pool-size"), 
                        String.valueOf(maximumPoolSize));
                compareConfig("minimum-idle", 
                        environment.getProperty("spring.datasource.hikari.minimum-idle"), 
                        String.valueOf(minimumIdle));
                compareConfig("connection-timeout", 
                        environment.getProperty("spring.datasource.hikari.connection-timeout"), 
                        String.valueOf(connectionTimeout) + "ms");
            }
            
        } catch (Exception e) {
            log.error("[DatabaseConfig] 读取实际配置失败", e);
        }
    }

    /**
     * 对比配置值
     */
    private void compareConfig(String key, String apolloValue, String actualValue) {
        String apollo = apolloValue != null ? apolloValue : "未配置";
        if (apollo.equals(actualValue) || apollo.equals(actualValue.replace("ms", ""))) {
            log.info("[DatabaseConfig]   {}: Apollo={}, 实际={} ✅", key, apollo, actualValue);
        } else {
            log.warn("[DatabaseConfig]   {}: Apollo={}, 实际={} ⚠️ 配置不一致！", key, apollo, actualValue);
        }
    }

    /**
     * 从 DataSource 中提取 HikariCP 数据源
     */
    private HikariDataSource extractHikariDataSource(DataSource dataSource) {
        if (dataSource == null) {
            return null;
        }

        // 直接是 HikariCP
        if (dataSource instanceof HikariDataSource) {
            return (HikariDataSource) dataSource;
        }

        // 动态数据源：尝试提取
        if (dataSource.getClass().getName().equals("com.baomidou.dynamic.datasource.DynamicRoutingDataSource")) {
            try {
                java.lang.reflect.Method getDataSourcesMethod = dataSource.getClass().getMethod("getDataSources");
                @SuppressWarnings("unchecked")
                Map<String, DataSource> dataSources = (Map<String, DataSource>) getDataSourcesMethod.invoke(dataSource);

                if (dataSources == null || dataSources.isEmpty()) {
                    log.warn("[DatabaseConfig] DynamicRoutingDataSource 中没有数据源");
                    return null;
                }

                log.info("[DatabaseConfig] 发现动态数据源，共 {} 个数据源: {}", dataSources.size(), dataSources.keySet());

                // 优先获取主数据源
                String[] primaryKeys = {"master", "primary", "default"};
                for (String key : primaryKeys) {
                    DataSource ds = dataSources.get(key);
                    if (ds instanceof HikariDataSource) {
                        log.info("[DatabaseConfig] 使用主数据源: {}", key);
                        return (HikariDataSource) ds;
                    }
                }

                // 获取第一个 HikariCP 数据源
                for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
                    if (entry.getValue() instanceof HikariDataSource) {
                        log.info("[DatabaseConfig] 使用数据源: {}", entry.getKey());
                        return (HikariDataSource) entry.getValue();
                    }
                }

                log.warn("[DatabaseConfig] 动态数据源中没有找到 HikariCP 数据源");
                return null;

            } catch (Exception e) {
                log.error("[DatabaseConfig] 从 DynamicRoutingDataSource 提取数据源失败", e);
                return null;
            }
        }

        log.warn("[DatabaseConfig] DataSource类型不支持: {}", dataSource.getClass().getName());
        return null;
    }
}
