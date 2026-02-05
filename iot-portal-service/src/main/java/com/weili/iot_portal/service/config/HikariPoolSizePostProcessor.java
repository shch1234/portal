package com.weili.iot_portal.service.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.Map;

/**
 * HikariCP 连接池大小后处理器
 * <p>
 * 在数据源初始化后，动态设置连接池大小，确保 Apollo 配置生效
 * </p>
 * <p>
 * 原因：动态数据源可能没有正确加载 Apollo 配置，导致使用默认值（20）
 * </p>
 * <p>
 * 注意：使用 @PostConstruct 确保在 DatabaseConfigPrinter 之后执行
 * </p>
 *
 * @author system
 */
@Slf4j
@Component("hikariPoolSizePostProcessor")
public class HikariPoolSizePostProcessor {

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private Environment environment;

    @PostConstruct
    public void adjustPoolSize() {
        if (dataSource == null) {
            log.debug("[HikariPoolSize] DataSource 未找到，跳过处理");
            return;
        }

        if (environment == null) {
            log.debug("[HikariPoolSize] Environment 未注入，跳过处理");
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("[HikariPoolSize] 开始调整连接池大小: dataSourceType={}", 
                    dataSource.getClass().getName());
        }
        
        processDataSource(dataSource, "dataSource");
    }

    /**
     * 处理数据源，设置 HikariCP 连接池大小
     */
    private void processDataSource(DataSource dataSource, String beanName) {
        if (environment == null) {
            log.debug("[HikariPoolSize] Environment 未注入，跳过处理: beanName={}", beanName);
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("[HikariPoolSize] 开始处理数据源: beanName={}, type={}", 
                    beanName, dataSource.getClass().getName());
        }
        
        HikariDataSource hikariDataSource = extractHikariDataSource(dataSource);
        if (hikariDataSource == null) {
            log.debug("[HikariPoolSize] 无法提取 HikariCP 数据源: beanName={}", beanName);
            return;
        }
        
        if (log.isDebugEnabled()) {
            log.debug("[HikariPoolSize] 成功提取 HikariCP 数据源: beanName={}, poolName={}", 
                    beanName, hikariDataSource.getPoolName());
        }

        // 读取 Apollo 配置
        String masterMaxPoolSize = environment.getProperty("spring.datasource.master.hikari.maximum-pool-size");
        String commonMaxPoolSize = environment.getProperty("spring.datasource.hikari.maximum-pool-size");
        String maxPoolSizeStr = masterMaxPoolSize != null ? masterMaxPoolSize : commonMaxPoolSize;

        String masterMinIdle = environment.getProperty("spring.datasource.master.hikari.minimum-idle");
        String commonMinIdle = environment.getProperty("spring.datasource.hikari.minimum-idle");
        String minIdleStr = masterMinIdle != null ? masterMinIdle : commonMinIdle;

        // 获取当前配置
        int currentMaxPoolSize = hikariDataSource.getMaximumPoolSize();
        int currentMinIdle = hikariDataSource.getMinimumIdle();

        // 如果配置了新的值，且与当前值不同，则更新
        boolean updated = false;
        if (maxPoolSizeStr != null) {
            try {
                int newMaxPoolSize = Integer.parseInt(maxPoolSizeStr);
                if (newMaxPoolSize != currentMaxPoolSize) {
                    hikariDataSource.setMaximumPoolSize(newMaxPoolSize);
                    if (log.isDebugEnabled()) {
                        log.debug("[HikariPoolSize] 更新连接池最大大小: 从 {} 改为 {}", 
                                currentMaxPoolSize, newMaxPoolSize);
                    }
                    updated = true;
                }
            } catch (NumberFormatException e) {
                log.warn("[HikariPoolSize] 无效的 maximum-pool-size 配置: {}", maxPoolSizeStr);
            }
        }

        if (minIdleStr != null) {
            try {
                int newMinIdle = Integer.parseInt(minIdleStr);
                if (newMinIdle != currentMinIdle) {
                    hikariDataSource.setMinimumIdle(newMinIdle);
                    if (log.isDebugEnabled()) {
                        log.debug("[HikariPoolSize] 更新连接池最小空闲: 从 {} 改为 {}", 
                                currentMinIdle, newMinIdle);
                    }
                    updated = true;
                }
            } catch (NumberFormatException e) {
                log.warn("[HikariPoolSize] 无效的 minimum-idle 配置: {}", minIdleStr);
            }
        }

        if (updated) {
            // 配置已更新：输出关键信息（INFO级别）
            log.info("[HikariPoolSize] ✅ 连接池配置已更新: maximumPoolSize={}, minimumIdle={}", 
                    hikariDataSource.getMaximumPoolSize(), hikariDataSource.getMinimumIdle());
        }
        // 配置无需更新时不再输出日志（降级：正常情况不需要关注）
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
                return extractHikariDataSource(wrappedDataSource);
            } catch (Exception e) {
                log.debug("[HikariPoolSize] 从 ItemDataSource 提取数据源失败: {}", e.getMessage());
            }
        }

        // 动态数据源：尝试提取
        if (dataSource.getClass().getName().equals("com.baomidou.dynamic.datasource.DynamicRoutingDataSource")) {
            try {
                java.lang.reflect.Method getDataSourcesMethod = dataSource.getClass().getMethod("getDataSources");
                @SuppressWarnings("unchecked")
                Map<String, DataSource> dataSources = (Map<String, DataSource>) getDataSourcesMethod.invoke(dataSource);

                if (dataSources == null || dataSources.isEmpty()) {
                    return null;
                }

                // 优先获取主数据源
                String[] primaryKeys = {"master", "primary", "default"};
                for (String key : primaryKeys) {
                    DataSource ds = dataSources.get(key);
                    if (ds != null) {
                        HikariDataSource hikariDs = extractHikariDataSource(ds);
                        if (hikariDs != null) {
                            return hikariDs;
                        }
                    }
                }

                // 获取第一个 HikariCP 数据源
                for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
                    HikariDataSource hikariDs = extractHikariDataSource(entry.getValue());
                    if (hikariDs != null) {
                        return hikariDs;
                    }
                }

            } catch (Exception e) {
                log.debug("[HikariPoolSize] 从 DynamicRoutingDataSource 提取数据源失败: {}", e.getMessage());
            }
        }

        return null;
    }
}
