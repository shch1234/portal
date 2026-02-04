package com.weili.iot_portal.service.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库连接池监控组件
 * <p>
 * 定期监控 HikariCP 连接池状态，记录指标并告警
 * </p>
 * <p>
 * 监控指标：
 * - 总连接数
 * - 活跃连接数
 * - 空闲连接数
 * - 等待线程数
 * - 连接池使用率
 * </p>
 * <p>
 * 配置建议：
 * - maximum-pool-size: 建议设置为 Tomcat 最大线程数的 1/3 到 1/2
 *   （例如：Tomcat maxThreads=300，建议 maximum-pool-size=50-100）
 * - connection-timeout: 建议设置为 30000ms（30秒）
 * - minimum-idle: 建议设置为 maximum-pool-size 的 1/3
 * </p>
 *
 * @author system
 */
@Slf4j
@Component
public class DatabasePoolMonitor {

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private Environment environment;

    private HikariDataSource hikariDataSource;
    private boolean poolAvailable = false;

    @PostConstruct
    public void init() {
        if (dataSource == null) {
            log.warn("[DatabasePoolMonitor] DataSource未找到，监控功能不可用");
            return;
        }

        // 检查是否是 HikariCP
        if (dataSource instanceof HikariDataSource) {
            hikariDataSource = (HikariDataSource) dataSource;
            poolAvailable = true;
            printConfiguration();
        } else {
            log.warn("[DatabasePoolMonitor] DataSource不是HikariCP类型: {}", dataSource.getClass().getName());
        }
    }

    /**
     * 打印连接池配置
     */
    private void printConfiguration() {
        if (hikariDataSource == null) {
            return;
        }

        try {
            int maximumPoolSize = hikariDataSource.getMaximumPoolSize();
            int minimumIdle = hikariDataSource.getMinimumIdle();
            long connectionTimeout = hikariDataSource.getConnectionTimeout();
            long maxLifetime = hikariDataSource.getMaxLifetime();
            long idleTimeout = hikariDataSource.getIdleTimeout();
            String poolName = hikariDataSource.getPoolName();

            log.info("[DatabasePoolMonitor] ========== 数据库连接池配置 ==========");
            log.info("[DatabasePoolMonitor] Pool Name: {}", poolName);
            log.info("[DatabasePoolMonitor] 【实际生效的连接池配置】");
            log.info("[DatabasePoolMonitor]   maximum-pool-size = {}", maximumPoolSize);
            log.info("[DatabasePoolMonitor]   minimum-idle = {}", minimumIdle);
            log.info("[DatabasePoolMonitor]   connection-timeout = {}ms", connectionTimeout);
            log.info("[DatabasePoolMonitor]   max-lifetime = {}ms", maxLifetime);
            log.info("[DatabasePoolMonitor]   idle-timeout = {}ms", idleTimeout);

            // 从配置文件读取配置值（如果可用）
            if (environment != null) {
                String configMaxPoolSize = environment.getProperty("spring.datasource.hikari.maximum-pool-size", "未配置");
                String configMinIdle = environment.getProperty("spring.datasource.hikari.minimum-idle", "未配置");
                String configConnectionTimeout = environment.getProperty("spring.datasource.hikari.connection-timeout", "未配置");

                log.info("[DatabasePoolMonitor] 【配置对比】");
                log.info("[DatabasePoolMonitor]   配置项                    | 配置文件值      | 实际生效值");
                log.info("[DatabasePoolMonitor]   maximum-pool-size        | {} | {}",
                        String.format("%-15s", configMaxPoolSize), maximumPoolSize);
                log.info("[DatabasePoolMonitor]   minimum-idle             | {} | {}",
                        String.format("%-15s", configMinIdle), minimumIdle);
                log.info("[DatabasePoolMonitor]   connection-timeout       | {} | {}ms",
                        String.format("%-15s", configConnectionTimeout), connectionTimeout);
            }

            // 配置合理性检查
            checkConfiguration(maximumPoolSize, minimumIdle, connectionTimeout);

        } catch (Exception e) {
            log.error("[DatabasePoolMonitor] 读取连接池配置失败", e);
        }
    }

    /**
     * 检查配置合理性
     */
    private void checkConfiguration(int maximumPoolSize, int minimumIdle, long connectionTimeout) {
        boolean hasWarning = false;

        // 检查连接池大小是否过小
        if (maximumPoolSize < 20) {
            log.warn("[DatabasePoolMonitor] ⚠️ 连接池大小过小: maximum-pool-size={}，建议至少设置为20-50", maximumPoolSize);
            hasWarning = true;
        }

        // 检查连接池大小是否过大
        if (maximumPoolSize > 200) {
            log.warn("[DatabasePoolMonitor] ⚠️ 连接池大小过大: maximum-pool-size={}，建议不超过200", maximumPoolSize);
            hasWarning = true;
        }

        // 检查 minimum-idle 是否合理
        if (minimumIdle > maximumPoolSize) {
            log.error("[DatabasePoolMonitor] 🚨 配置错误: minimum-idle({}) > maximum-pool-size({})", minimumIdle, maximumPoolSize);
            hasWarning = true;
        } else if (minimumIdle < maximumPoolSize / 3) {
            log.warn("[DatabasePoolMonitor] ⚠️ minimum-idle 过小: minimum-idle={}，建议设置为 maximum-pool-size 的 1/3", minimumIdle);
            hasWarning = true;
        }

        // 检查连接超时时间
        if (connectionTimeout < 10000) {
            log.warn("[DatabasePoolMonitor] ⚠️ 连接超时时间过短: connection-timeout={}ms，建议至少设置为30000ms（30秒）", connectionTimeout);
            hasWarning = true;
        } else if (connectionTimeout > 60000) {
            log.warn("[DatabasePoolMonitor] ⚠️ 连接超时时间过长: connection-timeout={}ms，建议不超过60000ms（60秒）", connectionTimeout);
            hasWarning = true;
        }

        if (!hasWarning) {
            log.info("[DatabasePoolMonitor] ✅ 连接池配置检查通过");
        }
    }

    /**
     * 定期监控连接池状态（每分钟执行一次）
     */
    @Scheduled(fixedRate = 60000) // 60秒
    public void monitorPool() {
        if (!poolAvailable || hikariDataSource == null) {
            return;
        }

        try {
            HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
            if (poolBean == null) {
                return;
            }

            int total = poolBean.getTotalConnections();
            int active = poolBean.getActiveConnections();
            int idle = poolBean.getIdleConnections();
            int threadsAwaitingConnection = poolBean.getThreadsAwaitingConnection();
            int maximumPoolSize = hikariDataSource.getMaximumPoolSize();

            // 计算使用率
            double usageRate = maximumPoolSize > 0 ? (double) active / maximumPoolSize * 100 : 0;

            // 记录指标
            log.info("[DatabasePoolMonitor] 连接池状态: total={}, active={}, idle={}, max={}, usage={}%, waiting={}",
                    total, active, idle, maximumPoolSize, String.format("%.2f", usageRate), threadsAwaitingConnection);

            // 告警：如果活跃连接数超过80%
            if (usageRate > 80) {
                log.warn("[DatabasePoolMonitor] ⚠️ 连接池使用率过高: active={}, max={}, usage={}%",
                        active, maximumPoolSize, String.format("%.2f", usageRate));
            }

            // 告警：如果活跃连接数接近最大值
            if (active >= maximumPoolSize * 0.9) {
                log.error("[DatabasePoolMonitor] 🚨 连接池即将耗尽: active={}, max={}, usage={}%, waiting={}",
                        active, maximumPoolSize, String.format("%.2f", usageRate), threadsAwaitingConnection);
            }

            // 告警：如果有等待线程
            if (threadsAwaitingConnection > 0) {
                log.warn("[DatabasePoolMonitor] ⚠️ 有线程等待连接: waiting={}, active={}, max={}",
                        threadsAwaitingConnection, active, maximumPoolSize);
            }

            // 告警：如果连接池使用率超过90%
            if (usageRate > 90) {
                log.error("[DatabasePoolMonitor] 🚨 连接池使用率超过90%: active={}, max={}, usage={}%, waiting={}",
                        active, maximumPoolSize, String.format("%.2f", usageRate), threadsAwaitingConnection);
                log.error("[DatabasePoolMonitor] 🚨 建议立即增加连接池大小！当前配置: maximum-pool-size={}", maximumPoolSize);
            }

            // 连接泄露检测：如果活跃连接数持续接近最大值且空闲连接为0
            if (active >= maximumPoolSize * 0.95 && idle == 0 && threadsAwaitingConnection > 0) {
                log.error("[DatabasePoolMonitor] 🚨 疑似连接泄露！active={}, idle={}, waiting={}, max={}",
                        active, idle, threadsAwaitingConnection, maximumPoolSize);
                log.error("[DatabasePoolMonitor] 🚨 可能原因：1)事务超时未设置 2)长时间运行的事务 3)死锁 4)异常未正确回滚");
            }

        } catch (Exception e) {
            log.error("[DatabasePoolMonitor] 监控连接池状态失败", e);
        }
    }

    /**
     * 测试连接（用于健康检查）
     */
    public boolean testConnection() {
        if (!poolAvailable || hikariDataSource == null) {
            return false;
        }

        try (Connection connection = hikariDataSource.getConnection()) {
            return connection.isValid(5); // 5秒超时
        } catch (SQLException e) {
            log.error("[DatabasePoolMonitor] 测试连接失败", e);
            return false;
        }
    }
}
