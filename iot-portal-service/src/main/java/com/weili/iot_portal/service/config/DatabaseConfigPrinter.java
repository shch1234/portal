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
@org.springframework.context.annotation.DependsOn("hikariPoolSizePostProcessor")
public class DatabaseConfigPrinter {

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private Environment environment;

    @PostConstruct
    public void printDatabaseConfig() {
        if (dataSource == null) {
            log.debug("[DatabaseConfig] DataSource未找到，跳过配置打印");
            return;
        }

        // 打印实际生效的配置（关键信息）
        printActualConfig();
    }

    /**
     * 打印 Apollo 配置值（仅用于调试，降级为DEBUG）
     */
    private void printApolloConfig() {
        if (!log.isDebugEnabled()) {
            return;
        }
        
        log.debug("[DatabaseConfig] 【Apollo 配置值】");
        
        // 主数据源配置
        String masterDatabase = environment.getProperty("spring.datasource.master.database", "未配置");
        log.debug("[DatabaseConfig]   spring.datasource.master.database = {}", masterDatabase);
        
        // HikariCP 配置（通用配置）
        String maxPoolSize = environment.getProperty("spring.datasource.hikari.maximum-pool-size", "未配置");
        String minIdle = environment.getProperty("spring.datasource.hikari.minimum-idle", "未配置");
        String connectionTimeout = environment.getProperty("spring.datasource.hikari.connection-timeout", "未配置");
        String maxLifetime = environment.getProperty("spring.datasource.hikari.max-lifetime", "未配置");
        String idleTimeout = environment.getProperty("spring.datasource.hikari.idle-timeout", "未配置");
        String validationTimeout = environment.getProperty("spring.datasource.hikari.validation-timeout", "未配置");
        String leakDetectionThreshold = environment.getProperty("spring.datasource.hikari.leak-detection-threshold", "未配置");
        
        log.debug("[DatabaseConfig]   spring.datasource.hikari.maximum-pool-size = {}", maxPoolSize);
        log.debug("[DatabaseConfig]   spring.datasource.hikari.minimum-idle = {}", minIdle);
        log.debug("[DatabaseConfig]   spring.datasource.hikari.connection-timeout = {}ms", connectionTimeout);
        log.debug("[DatabaseConfig]   spring.datasource.hikari.max-lifetime = {}ms", maxLifetime);
        log.debug("[DatabaseConfig]   spring.datasource.hikari.idle-timeout = {}ms", idleTimeout);
        log.debug("[DatabaseConfig]   spring.datasource.hikari.validation-timeout = {}ms", validationTimeout);
        log.debug("[DatabaseConfig]   spring.datasource.hikari.leak-detection-threshold = {}ms", leakDetectionThreshold);
        
        // 动态数据源配置（master数据源）
        String masterMaxPoolSize = environment.getProperty("spring.datasource.master.hikari.maximum-pool-size", "未配置");
        String masterMinIdle = environment.getProperty("spring.datasource.master.hikari.minimum-idle", "未配置");
        if (!"未配置".equals(masterMaxPoolSize) || !"未配置".equals(masterMinIdle)) {
            log.debug("[DatabaseConfig] 【动态数据源 master 配置】");
            log.debug("[DatabaseConfig]   spring.datasource.master.hikari.maximum-pool-size = {}", masterMaxPoolSize);
            log.debug("[DatabaseConfig]   spring.datasource.master.hikari.minimum-idle = {}", masterMinIdle);
        }
    }

    /**
     * 打印实际生效的配置（仅保留关键信息）
     */
    private void printActualConfig() {
        HikariDataSource hikariDataSource = extractHikariDataSource(dataSource);
        if (hikariDataSource == null) {
            log.debug("[DatabaseConfig] 无法提取 HikariCP 数据源，跳过配置打印");
            return;
        }

        try {
            int maximumPoolSize = hikariDataSource.getMaximumPoolSize();
            int minimumIdle = hikariDataSource.getMinimumIdle();
            
            // 只打印关键配置信息（INFO级别）
            log.info("[DatabaseConfig] 数据库连接池: maximum-pool-size={}, minimum-idle={}", 
                    maximumPoolSize, minimumIdle);
            
            // 配置对比（仅检查关键配置）
            if (environment != null) {
                // 对比 maximum-pool-size（优先使用 master 专用配置）
                String masterMaxPoolSize = environment.getProperty("spring.datasource.master.hikari.maximum-pool-size");
                String commonMaxPoolSize = environment.getProperty("spring.datasource.hikari.maximum-pool-size");
                String apolloMaxPoolSize = masterMaxPoolSize != null ? masterMaxPoolSize : commonMaxPoolSize;
                
                // 对比 minimum-idle（优先使用 master 专用配置）
                String masterMinIdle = environment.getProperty("spring.datasource.master.hikari.minimum-idle");
                String commonMinIdle = environment.getProperty("spring.datasource.hikari.minimum-idle");
                String apolloMinIdle = masterMinIdle != null ? masterMinIdle : commonMinIdle;
                
                // 检查实际配置是否与 Apollo 配置一致
                boolean maxPoolSizeMismatch = apolloMaxPoolSize != null && 
                        !apolloMaxPoolSize.equals(String.valueOf(maximumPoolSize));
                boolean minIdleMismatch = apolloMinIdle != null && 
                        !apolloMinIdle.equals(String.valueOf(minimumIdle));
                
                if (maxPoolSizeMismatch || minIdleMismatch) {
                    // 配置不一致：输出警告（关键信息，必须保留）
                    log.warn("[DatabaseConfig] ⚠️ 配置未生效！Apollo配置与实际配置不一致:");
                    if (maxPoolSizeMismatch) {
                        log.warn("[DatabaseConfig]   maximum-pool-size: Apollo={}, 实际={}", 
                                apolloMaxPoolSize, maximumPoolSize);
                    }
                    if (minIdleMismatch) {
                        log.warn("[DatabaseConfig]   minimum-idle: Apollo={}, 实际={}", 
                                apolloMinIdle, minimumIdle);
                    }
                }
                // 配置一致时不再输出日志（降级：正常情况不需要关注）
                
                // 详细的Apollo配置值仅在DEBUG级别输出
                if (log.isDebugEnabled()) {
                    printApolloConfig();
                    // 打印其他详细配置
                    String poolName = hikariDataSource.getPoolName();
                    long connectionTimeout = hikariDataSource.getConnectionTimeout();
                    long maxLifetime = hikariDataSource.getMaxLifetime();
                    long idleTimeout = hikariDataSource.getIdleTimeout();
                    long validationTimeout = hikariDataSource.getValidationTimeout();
                    long leakDetectionThreshold = hikariDataSource.getLeakDetectionThreshold();
                    
                    log.debug("[DatabaseConfig] 详细配置: PoolName={}, connectionTimeout={}ms, " +
                            "maxLifetime={}ms, idleTimeout={}ms, validationTimeout={}ms, " +
                            "leakDetectionThreshold={}ms",
                            poolName, connectionTimeout, maxLifetime, idleTimeout, 
                            validationTimeout, leakDetectionThreshold);
                }
            }
            
        } catch (Exception e) {
            log.error("[DatabaseConfig] 读取实际配置失败", e);
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

        // 处理 ItemDataSource（动态数据源的包装类）
        if (dataSource.getClass().getName().equals("com.baomidou.dynamic.datasource.ds.ItemDataSource")) {
            try {
                java.lang.reflect.Field dataSourceField = dataSource.getClass().getDeclaredField("dataSource");
                dataSourceField.setAccessible(true);
                DataSource wrappedDataSource = (DataSource) dataSourceField.get(dataSource);
                log.debug("[DatabaseConfig] 从 ItemDataSource 中提取内部数据源: {}", 
                        wrappedDataSource != null ? wrappedDataSource.getClass().getName() : "null");
                return extractHikariDataSource(wrappedDataSource);
            } catch (Exception e) {
                log.debug("[DatabaseConfig] 从 ItemDataSource 提取数据源失败: {}", e.getMessage());
            }
        }

        // 动态数据源：尝试提取
        if (dataSource.getClass().getName().equals("com.baomidou.dynamic.datasource.DynamicRoutingDataSource")) {
            try {
                java.lang.reflect.Method getDataSourcesMethod = dataSource.getClass().getMethod("getDataSources");
                @SuppressWarnings("unchecked")
                Map<String, DataSource> dataSources = (Map<String, DataSource>) getDataSourcesMethod.invoke(dataSource);

                if (dataSources == null || dataSources.isEmpty()) {
                    log.debug("[DatabaseConfig] DynamicRoutingDataSource 中没有数据源");
                    return null;
                }

                if (log.isDebugEnabled()) {
                    log.debug("[DatabaseConfig] 发现动态数据源，共 {} 个数据源: {}", dataSources.size(), dataSources.keySet());
                }

                // 优先获取主数据源
                String[] primaryKeys = {"master", "primary", "default"};
                for (String key : primaryKeys) {
                    DataSource ds = dataSources.get(key);
                    if (ds != null) {
                        HikariDataSource hikariDs = extractHikariDataSource(ds);
                        if (hikariDs != null) {
                            if (log.isDebugEnabled()) {
                                log.debug("[DatabaseConfig] 使用主数据源: {}", key);
                            }
                            return hikariDs;
                        }
                    }
                }

                // 获取第一个 HikariCP 数据源
                for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
                    HikariDataSource hikariDs = extractHikariDataSource(entry.getValue());
                    if (hikariDs != null) {
                        if (log.isDebugEnabled()) {
                            log.debug("[DatabaseConfig] 使用数据源: {}", entry.getKey());
                        }
                        return hikariDs;
                    }
                }

                log.debug("[DatabaseConfig] 动态数据源中没有找到 HikariCP 数据源");
                return null;

            } catch (Exception e) {
                log.error("[DatabaseConfig] 从 DynamicRoutingDataSource 提取数据源失败", e);
                return null;
            }
        }

        log.debug("[DatabaseConfig] DataSource类型不支持: {}", dataSource.getClass().getName());
        return null;
    }
}
