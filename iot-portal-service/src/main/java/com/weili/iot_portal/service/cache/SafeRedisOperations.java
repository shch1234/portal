package com.weili.iot_portal.service.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 安全的 Redis 操作工具类
 * <p>
 * 封装常见的 Redis 操作，确保连接在超时或异常时正确释放
 * </p>
 * <p>
 * 关键特性：
 * 1. 自动处理超时异常，确保连接释放
 * 2. 提供降级方案，避免业务阻塞
 * 3. 统一的异常处理和日志记录
 * </p>
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SafeRedisOperations {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 安全执行 Redis GET 操作
     * <p>
     * 连接管理：
     * - Spring Data Redis 自动管理连接
     * - 超时或异常时，连接会在 finally 块中自动释放
     * - 不需要手动释放连接
     * </p>
     *
     * @param key Redis 键
     * @return 值，如果操作失败则返回 null
     */
    public String safeGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (QueryTimeoutException e) {
            // ⚠️ 重要：连接已自动释放，这里只需要处理业务逻辑
            log.warn("[SafeRedis] GET操作超时: key={}, error={}", key, e.getMessage());
            return null;
        } catch (RedisConnectionFailureException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] GET操作连接失败: key={}, error={}", key, e.getMessage());
            return null;
        } catch (Exception e) {
            // ⚠️ 重要：连接已自动释放
            log.error("[SafeRedis] GET操作异常: key={}", key, e);
            return null;
        }
    }

    /**
     * 安全执行 Redis SET 操作
     *
     * @param key     Redis 键
     * @param value   值
     * @param timeout 过期时间
     * @return true 如果操作成功，false 如果操作失败
     */
    public boolean safeSet(String key, String value, Duration timeout) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout);
            return true;
        } catch (QueryTimeoutException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] SET操作超时: key={}, error={}", key, e.getMessage());
            return false;
        } catch (RedisConnectionFailureException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] SET操作连接失败: key={}, error={}", key, e.getMessage());
            return false;
        } catch (Exception e) {
            // ⚠️ 重要：连接已自动释放
            log.error("[SafeRedis] SET操作异常: key={}", key, e);
            return false;
        }
    }

    /**
     * 安全执行 Redis SETNX 操作（如果不存在则设置）
     *
     * @param key     Redis 键
     * @param value   值
     * @param timeout 过期时间
     * @return true 如果设置成功，false 如果键已存在或操作失败
     */
    public boolean safeSetIfAbsent(String key, String value, Duration timeout) {
        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, timeout);
            return Boolean.TRUE.equals(result);
        } catch (QueryTimeoutException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] SETNX操作超时: key={}, error={}", key, e.getMessage());
            return false;
        } catch (RedisConnectionFailureException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] SETNX操作连接失败: key={}, error={}", key, e.getMessage());
            return false;
        } catch (Exception e) {
            // ⚠️ 重要：连接已自动释放
            log.error("[SafeRedis] SETNX操作异常: key={}", key, e);
            return false;
        }
    }

    /**
     * 安全执行 Redis DELETE 操作
     *
     * @param key Redis 键
     * @return true 如果操作成功，false 如果操作失败
     */
    public boolean safeDelete(String key) {
        try {
            Boolean result = redisTemplate.delete(key);
            return Boolean.TRUE.equals(result);
        } catch (QueryTimeoutException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] DELETE操作超时: key={}, error={}", key, e.getMessage());
            return false;
        } catch (RedisConnectionFailureException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] DELETE操作连接失败: key={}, error={}", key, e.getMessage());
            return false;
        } catch (Exception e) {
            // ⚠️ 重要：连接已自动释放
            log.error("[SafeRedis] DELETE操作异常: key={}", key, e);
            return false;
        }
    }

    /**
     * 安全执行 Redis HGET 操作
     *
     * @param key   Redis 键
     * @param field Hash 字段
     * @return 值，如果操作失败则返回 null
     */
    public String safeHGet(String key, String field) {
        try {
            return (String) redisTemplate.opsForHash().get(key, field);
        } catch (QueryTimeoutException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] HGET操作超时: key={}, field={}, error={}", key, field, e.getMessage());
            return null;
        } catch (RedisConnectionFailureException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] HGET操作连接失败: key={}, field={}, error={}", key, field, e.getMessage());
            return null;
        } catch (Exception e) {
            // ⚠️ 重要：连接已自动释放
            log.error("[SafeRedis] HGET操作异常: key={}, field={}", key, field, e);
            return null;
        }
    }

    /**
     * 安全执行 Redis HGETALL 操作
     *
     * @param key Redis 键
     * @return Hash 所有字段和值，如果操作失败则返回 null
     */
    public Map<Object, Object> safeHGetAll(String key) {
        try {
            return redisTemplate.opsForHash().entries(key);
        } catch (QueryTimeoutException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] HGETALL操作超时: key={}, error={}", key, e.getMessage());
            return null;
        } catch (RedisConnectionFailureException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] HGETALL操作连接失败: key={}, error={}", key, e.getMessage());
            return null;
        } catch (Exception e) {
            // ⚠️ 重要：连接已自动释放
            log.error("[SafeRedis] HGETALL操作异常: key={}", key, e);
            return null;
        }
    }

    /**
     * 安全执行 Redis HSET 操作
     *
     * @param key   Redis 键
     * @param field Hash 字段
     * @param value 值
     * @return true 如果操作成功，false 如果操作失败
     */
    public boolean safeHSet(String key, String field, String value) {
        try {
            redisTemplate.opsForHash().put(key, field, value);
            return true;
        } catch (QueryTimeoutException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] HSET操作超时: key={}, field={}, error={}", key, field, e.getMessage());
            return false;
        } catch (RedisConnectionFailureException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] HSET操作连接失败: key={}, field={}, error={}", key, field, e.getMessage());
            return false;
        } catch (Exception e) {
            // ⚠️ 重要：连接已自动释放
            log.error("[SafeRedis] HSET操作异常: key={}, field={}", key, field, e);
            return false;
        }
    }

    /**
     * 安全执行 Redis Pipeline 操作
     * <p>
     * 注意：Pipeline 操作需要确保释放资源（如 Pipeline 许可）
     * </p>
     *
     * @param callback Pipeline 回调
     * @param <T>      返回类型
     * @return Pipeline 结果列表，如果操作失败则返回 null
     */
    public <T> List<T> safeExecutePipelined(RedisCallback<T> callback) {
        try {
            @SuppressWarnings("unchecked")
            List<T> results = (List<T>) redisTemplate.executePipelined(callback);
            return results;
        } catch (QueryTimeoutException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] Pipeline操作超时: error={}", e.getMessage());
            return null;
        } catch (RedisConnectionFailureException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] Pipeline操作连接失败: error={}", e.getMessage());
            return null;
        } catch (Exception e) {
            // ⚠️ 重要：连接已自动释放
            log.error("[SafeRedis] Pipeline操作异常", e);
            return null;
        }
    }

    /**
     * 安全执行自定义 Redis 操作
     * <p>
     * 适用于需要自定义 Redis 操作的场景
     * </p>
     *
     * @param operation 操作函数
     * @param fallback  降级函数（可选）
     * @param <T>       返回类型
     * @return 操作结果，如果操作失败则返回降级结果或 null
     */
    public <T> T safeExecute(Supplier<T> operation, Supplier<T> fallback) {
        try {
            return operation.get();
        } catch (QueryTimeoutException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] 自定义操作超时: error={}", e.getMessage());
            return fallback != null ? fallback.get() : null;
        } catch (RedisConnectionFailureException e) {
            // ⚠️ 重要：连接已自动释放
            log.warn("[SafeRedis] 自定义操作连接失败: error={}", e.getMessage());
            return fallback != null ? fallback.get() : null;
        } catch (Exception e) {
            // ⚠️ 重要：连接已自动释放
            log.error("[SafeRedis] 自定义操作异常", e);
            return fallback != null ? fallback.get() : null;
        }
    }

    /**
     * 检查是否是 Redis 超时或连接失败异常
     *
     * @param e 异常
     * @return true 如果是 Redis 超时或连接失败异常
     */
    public static boolean isRedisTimeoutOrConnectionFailure(Exception e) {
        return e instanceof QueryTimeoutException
                || e instanceof RedisConnectionFailureException
                || (e.getMessage() != null && (
                        e.getMessage().contains("Redis command timed out")
                        || e.getMessage().contains("Command timed out")
                        || e.getMessage().contains("Unable to connect to Redis")
                        || e.getMessage().contains("Could not get a resource from the pool")
                ));
    }
}
