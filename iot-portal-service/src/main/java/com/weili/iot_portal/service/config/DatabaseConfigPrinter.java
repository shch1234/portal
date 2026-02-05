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
        
        // HikariCP 配置（通用配置）
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
        
        // 动态数据源配置（master数据源）
        String masterMaxPoolSize = environment.getProperty("spring.datasource.master.hikari.maximum-pool-size", "未配置");
        String masterMinIdle = environment.getProperty("spring.datasource.master.hikari.minimum-idle", "未配置");
        if (!"未配置".equals(masterMaxPoolSize) || !"未配置".equals(masterMinIdle)) {
            log.info("[DatabaseConfig] 【动态数据源 master 配置】");
            log.info("[DatabaseConfig]   spring.datasource.master.hikari.maximum-pool-size = {}", masterMaxPoolSize);
            log.info("[DatabaseConfig]   spring.datasource.master.hikari.minimum-idle = {}", masterMinIdle);
        }
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
                
                // 对比 maximum-pool-size（优先使用 master 专用配置）
                String masterMaxPoolSize = environment.getProperty("spring.datasource.master.hikari.maximum-pool-size");
                String commonMaxPoolSize = environment.getProperty("spring.datasource.hikari.maximum-pool-size");
                String apolloMaxPoolSize = masterMaxPoolSize != null ? masterMaxPoolSize : commonMaxPoolSize;
                String maxPoolSizeSource = masterMaxPoolSize != null ? "master专用" : "通用";
                compareConfig("maximum-pool-size", apolloMaxPoolSize, String.valueOf(maximumPoolSize), maxPoolSizeSource);
                
                // 对比 minimum-idle（优先使用 master 专用配置）
                String masterMinIdle = environment.getProperty("spring.datasource.master.hikari.minimum-idle");
                String commonMinIdle = environment.getProperty("spring.datasource.hikari.minimum-idle");
                String apolloMinIdle = masterMinIdle != null ? masterMinIdle : commonMinIdle;
                String minIdleSource = masterMinIdle != null ? "master专用" : "通用";
                compareConfig("minimum-idle", apolloMinIdle, String.valueOf(minimumIdle), minIdleSource);
                
                // 对比 connection-timeout
                compareConfig("connection-timeout", 
                        environment.getProperty("spring.datasource.hikari.connection-timeout"), 
                        String.valueOf(connectionTimeout) + "ms", "通用");
                
                // 如果配置不一致，给出提示
                // 检查实际配置是否与 Apollo 配置一致
                boolean maxPoolSizeMismatch = apolloMaxPoolSize != null && 
                        !apolloMaxPoolSize.equals(String.valueOf(maximumPoolSize));
                boolean minIdleMismatch = apolloMinIdle != null && 
                        !apolloMinIdle.equals(String.valueOf(minimumIdle));
                
                if (maxPoolSizeMismatch || minIdleMismatch) {
                    log.warn("[DatabaseConfig] ⚠️ 配置未生效！请检查：");
                    if (maxPoolSizeMismatch) {
                        log.warn("[DatabaseConfig]   maximum-pool-size: Apollo={}, 实际={}", 
                                apolloMaxPoolSize, maximumPoolSize);
                    }
                    if (minIdleMismatch) {
                        log.warn("[DatabaseConfig]   minimum-idle: Apollo={}, 实际={}", 
                                apolloMinIdle, minimumIdle);
                    }
                    log.warn("[DatabaseConfig]   1. Apollo配置路径是否正确：spring.datasource.master.hikari.*");
                    log.warn("[DatabaseConfig]   2. 动态数据源是否正确加载了Apollo配置");
                    log.warn("[DatabaseConfig]   3. 是否有其他配置覆盖了Apollo配置");
                    log.warn("[DatabaseConfig]   4. 建议检查动态数据源的自动配置类");
                } else {
                    log.info("[DatabaseConfig] ✅ 所有配置已正确生效！");
                }
            }
            
        } catch (Exception e) {
            log.error("[DatabaseConfig] 读取实际配置失败", e);
        }
    }

    /**
     * 对比配置值
     */
    private void compareConfig(String key, String apolloValue, String actualValue) {
        compareConfig(key, apolloValue, actualValue, "");
    }
    
    /**
     * 对比配置值（带配置来源）
     */
    private void compareConfig(String key, String apolloValue, String actualValue, String source) {
        String apollo = apolloValue != null ? apolloValue : "未配置";
        String sourceInfo = source != null && !source.isEmpty() ? "[" + source + "]" : "";
        
        // 处理数值比较（去掉ms后缀）
        String apolloNum = apollo.replace("ms", "").trim();
        String actualNum = actualValue.replace("ms", "").trim();
        
        if (apolloNum.equals(actualNum)) {
            log.info("[DatabaseConfig]   {}: Apollo{}={}, 实际={} ✅", key, sourceInfo, apollo, actualValue);
        } else {
            log.warn("[DatabaseConfig]   {}: Apollo{}={}, 实际={} ⚠️ 配置不一致！", key, sourceInfo, apollo, actualValue);
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
                    log.warn("[DatabaseConfig] DynamicRoutingDataSource 中没有数据源");
                    return null;
                }

                log.info("[DatabaseConfig] 发现动态数据源，共 {} 个数据源: {}", dataSources.size(), dataSources.keySet());

                // 优先获取主数据源
                String[] primaryKeys = {"master", "primary", "default"};
                for (String key : primaryKeys) {
                    DataSource ds = dataSources.get(key);
                    if (ds != null) {
                        HikariDataSource hikariDs = extractHikariDataSource(ds);
                        if (hikariDs != null) {
                        log.info("[DatabaseConfig] 使用主数据源: {}", key);
                            return hikariDs;
                        }
                    }
                }

                // 获取第一个 HikariCP 数据源
                for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
                    HikariDataSource hikariDs = extractHikariDataSource(entry.getValue());
                    if (hikariDs != null) {
                        log.info("[DatabaseConfig] 使用数据源: {}", entry.getKey());
                        return hikariDs;
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
