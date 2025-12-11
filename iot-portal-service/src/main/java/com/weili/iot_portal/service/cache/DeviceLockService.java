package com.weili.iot_portal.service.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 设备分布式锁服务
 * <p>
 * 统一管理所有设备相关的分布式锁，提供统一的锁获取、释放和带锁执行方法
 * </p>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceLockService {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 锁值占位符
     */
    private static final String LOCK_VALUE = "1";

    /**
     * 默认锁超时时间（秒）
     */
    private static final long DEFAULT_LOCK_TIMEOUT_SECONDS = 5L;

    // ==================== 锁键前缀 ====================
    /**
     * 设备状态锁键前缀
     */
    private static final String LOCK_KEY_PREFIX_STATE = "device_state_lock:";

    /**
     * 设备刀具变更锁键前缀
     */
    private static final String LOCK_KEY_PREFIX_TOOL_CHANGE = "device_tool_lock:";

    // ==================== 状态锁相关 ====================
    /**
     * 尝试获取设备状态锁
     *
     * @param deviceId 设备ID
     * @param timeoutSeconds 锁超时时间（秒）
     * @return true 如果成功获取锁
     */
    public boolean tryLockState(String deviceId, long timeoutSeconds) {
        return tryLock(buildStateLockKey(deviceId), timeoutSeconds);
    }

    /**
     * 尝试获取设备状态锁（使用默认超时时间）
     *
     * @param deviceId 设备ID
     * @return true 如果成功获取锁
     */
    public boolean tryLockState(String deviceId) {
        return tryLockState(deviceId, DEFAULT_LOCK_TIMEOUT_SECONDS);
    }

    /**
     * 释放设备状态锁
     *
     * @param deviceId 设备ID
     */
    public void unlockState(String deviceId) {
        unlock(buildStateLockKey(deviceId));
    }

    /**
     * 带状态锁执行任务
     *
     * @param deviceId 设备ID
     * @param timeoutSeconds 锁超时时间（秒）
     * @param task 要执行的任务
     * @param <T> 返回值类型
     * @return 任务执行结果
     * @throws RuntimeException 如果获取锁失败或任务执行异常
     */
    public <T> T executeWithStateLock(String deviceId, long timeoutSeconds, Supplier<T> task) {
        return executeWithLock(buildStateLockKey(deviceId), timeoutSeconds, task);
    }

    /**
     * 带状态锁执行任务（使用默认超时时间）
     *
     * @param deviceId 设备ID
     * @param task 要执行的任务
     * @param <T> 返回值类型
     * @return 任务执行结果
     * @throws RuntimeException 如果获取锁失败或任务执行异常
     */
    public <T> T executeWithStateLock(String deviceId, Supplier<T> task) {
        return executeWithStateLock(deviceId, DEFAULT_LOCK_TIMEOUT_SECONDS, task);
    }

    // ==================== 刀具变更锁相关 ====================
    /**
     * 尝试获取设备刀具变更锁
     *
     * @param deviceId 设备ID
     * @param timeoutSeconds 锁超时时间（秒）
     * @return true 如果成功获取锁
     */
    public boolean tryLockToolChange(String deviceId, long timeoutSeconds) {
        return tryLock(buildToolChangeLockKey(deviceId), timeoutSeconds);
    }

    /**
     * 尝试获取设备刀具变更锁（使用默认超时时间）
     *
     * @param deviceId 设备ID
     * @return true 如果成功获取锁
     */
    public boolean tryLockToolChange(String deviceId) {
        return tryLockToolChange(deviceId, DEFAULT_LOCK_TIMEOUT_SECONDS);
    }

    /**
     * 释放设备刀具变更锁
     *
     * @param deviceId 设备ID
     */
    public void unlockToolChange(String deviceId) {
        unlock(buildToolChangeLockKey(deviceId));
    }

    /**
     * 带刀具变更锁执行任务
     *
     * @param deviceId 设备ID
     * @param timeoutSeconds 锁超时时间（秒）
     * @param task 要执行的任务
     * @param <T> 返回值类型
     * @return 任务执行结果
     * @throws RuntimeException 如果获取锁失败或任务执行异常
     */
    public <T> T executeWithToolChangeLock(String deviceId, long timeoutSeconds, Supplier<T> task) {
        return executeWithLock(buildToolChangeLockKey(deviceId), timeoutSeconds, task);
    }

    /**
     * 带刀具变更锁执行任务（使用默认超时时间）
     *
     * @param deviceId 设备ID
     * @param task 要执行的任务
     * @param <T> 返回值类型
     * @return 任务执行结果
     * @throws RuntimeException 如果获取锁失败或任务执行异常
     */
    public <T> T executeWithToolChangeLock(String deviceId, Supplier<T> task) {
        return executeWithToolChangeLock(deviceId, DEFAULT_LOCK_TIMEOUT_SECONDS, task);
    }

    // ==================== 通用锁操作方法 ====================
    /**
     * 尝试获取锁
     *
     * @param lockKey 锁键
     * @param timeoutSeconds 超时时间（秒）
     * @return true 如果成功获取锁
     */
    private boolean tryLock(String lockKey, long timeoutSeconds) {
        if (StringUtils.isBlank(lockKey)) {
            return false;
        }
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, LOCK_VALUE, Duration.ofSeconds(timeoutSeconds));
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 释放锁
     *
     * @param lockKey 锁键
     */
    private void unlock(String lockKey) {
        if (StringUtils.isNotBlank(lockKey)) {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 带锁执行任务（自动释放锁）
     *
     * @param lockKey 锁键
     * @param timeoutSeconds 超时时间（秒）
     * @param task 要执行的任务
     * @param <T> 返回值类型
     * @return 任务执行结果
     * @throws RuntimeException 如果获取锁失败或任务执行异常
     */
    private <T> T executeWithLock(String lockKey, long timeoutSeconds, Supplier<T> task) {
        if (!tryLock(lockKey, timeoutSeconds)) {
            throw new RuntimeException("Failed to acquire lock: " + lockKey);
        }
        try {
            return task.get();
        } finally {
            unlock(lockKey);
        }
    }

    // ==================== 锁键构建方法 ====================
    /**
     * 构建设备状态锁键
     *
     * @param deviceId 设备ID
     * @return 锁键
     */
    private String buildStateLockKey(String deviceId) {
        return LOCK_KEY_PREFIX_STATE + defaultBlank(deviceId);
    }

    /**
     * 构建设备刀具变更锁键
     *
     * @param deviceId 设备ID
     * @return 锁键
     */
    private String buildToolChangeLockKey(String deviceId) {
        return LOCK_KEY_PREFIX_TOOL_CHANGE + defaultBlank(deviceId);
    }

    /**
     * 默认空值处理
     */
    private String defaultBlank(String value) {
        return StringUtils.defaultIfBlank(value, "unknown");
    }
}

