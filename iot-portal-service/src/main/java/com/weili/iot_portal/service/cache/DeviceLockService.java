package com.weili.iot_portal.service.cache;

import com.weili.basic.redis.client.RedisClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
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

    private final RedisClient redisClient;

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

    /**
     * 设备加工状态锁键前缀
     */
    private static final String LOCK_KEY_PREFIX_PRODUCTION = "device_production_lock:";

    // ==================== 状态锁相关 ====================

    /**
     * 尝试获取设备状态锁
     *
     * @param deviceId       设备ID
     * @param timeoutSeconds 锁超时时间（秒）
     * @return true 如果成功获取锁
     */
    public boolean tryLockState(Long deviceId, long timeoutSeconds) {
        return tryLock(buildStateLockKey(deviceId), timeoutSeconds);
    }

    /**
     * 尝试获取设备状态锁（使用默认超时时间）
     *
     * @param deviceId 设备ID
     * @return true 如果成功获取锁
     */
    public boolean tryLockState(Long deviceId) {
        return tryLockState(deviceId, DEFAULT_LOCK_TIMEOUT_SECONDS);
    }

    /**
     * 释放设备状态锁
     *
     * @param deviceId 设备ID
     */
    public void unlockState(Long deviceId) {
        unlock(buildStateLockKey(deviceId));
    }

    /**
     * 带状态锁执行任务
     *
     * @param deviceId       设备ID
     * @param timeoutSeconds 锁超时时间（秒）
     * @param task           要执行的任务
     * @param <T>            返回值类型
     * @return 任务执行结果
     * @throws RuntimeException 如果获取锁失败或任务执行异常
     */
    public <T> T executeWithStateLock(Long deviceId, long timeoutSeconds, Supplier<T> task) {
        return executeWithLock(buildStateLockKey(deviceId), timeoutSeconds, task);
    }

    /**
     * 带状态锁执行任务（使用默认超时时间）
     *
     * @param deviceId 设备ID
     * @param task     要执行的任务
     * @param <T>      返回值类型
     * @return 任务执行结果
     * @throws RuntimeException 如果获取锁失败或任务执行异常
     */
    public <T> T executeWithStateLock(Long deviceId, Supplier<T> task) {
        return executeWithStateLock(deviceId, DEFAULT_LOCK_TIMEOUT_SECONDS, task);
    }

    // ==================== 刀具变更锁相关 ====================

    /**
     * 尝试获取设备刀具变更锁
     *
     * @param deviceId       设备ID
     * @param timeoutSeconds 锁超时时间（秒）
     * @return true 如果成功获取锁
     */
    public boolean tryLockToolChange(Long deviceId, long timeoutSeconds) {
        return tryLock(buildToolChangeLockKey(deviceId), timeoutSeconds);
    }

    /**
     * 尝试获取设备刀具变更锁（使用默认超时时间）
     *
     * @param deviceId 设备ID
     * @return true 如果成功获取锁
     */
    public boolean tryLockToolChange(Long deviceId) {
        return tryLockToolChange(deviceId, DEFAULT_LOCK_TIMEOUT_SECONDS);
    }

    /**
     * 释放设备刀具变更锁
     *
     * @param deviceId 设备ID
     */
    public void unlockToolChange(Long deviceId) {
        unlock(buildToolChangeLockKey(deviceId));
    }

    /**
     * 带刀具变更锁执行任务
     *
     * @param deviceId       设备ID
     * @param timeoutSeconds 锁超时时间（秒）
     * @param task           要执行的任务
     * @param <T>            返回值类型
     * @return 任务执行结果
     * @throws RuntimeException 如果获取锁失败或任务执行异常
     */
    public <T> T executeWithToolChangeLock(Long deviceId, long timeoutSeconds, Supplier<T> task) {
        return executeWithLock(buildToolChangeLockKey(deviceId), timeoutSeconds, task);
    }

    /**
     * 带刀具变更锁执行任务（使用默认超时时间）
     *
     * @param deviceId 设备ID
     * @param task     要执行的任务
     * @param <T>      返回值类型
     * @return 任务执行结果
     * @throws RuntimeException 如果获取锁失败或任务执行异常
     */
    public <T> T executeWithToolChangeLock(Long deviceId, Supplier<T> task) {
        return executeWithToolChangeLock(deviceId, DEFAULT_LOCK_TIMEOUT_SECONDS, task);
    }

    // ==================== 加工状态锁相关 ====================

    /**
     * 尝试获取设备加工状态锁
     *
     * @param deviceId       设备ID
     * @param timeoutSeconds 锁超时时间（秒）
     * @return true 如果成功获取锁
     */
    public boolean tryLockProduction(Long deviceId, long timeoutSeconds) {
        return tryLock(buildProductionLockKey(deviceId), timeoutSeconds);
    }

    /**
     * 尝试获取设备加工状态锁（使用默认超时时间）
     *
     * @param deviceId 设备ID
     * @return true 如果成功获取锁
     */
    public boolean tryLockProduction(Long deviceId) {
        return tryLockProduction(deviceId, DEFAULT_LOCK_TIMEOUT_SECONDS);
    }

    /**
     * 释放设备加工状态锁
     *
     * @param deviceId 设备ID
     */
    public void unlockProduction(Long deviceId) {
        unlock(buildProductionLockKey(deviceId));
    }

    // ==================== 通用锁操作方法 ====================

    /**
     * 尝试获取锁
     * 使用 RedisClient.tryLock() 方法，直接使用原生 Redis 连接，不受 Spring 事务管理影响
     *
     * @param lockKey        锁键
     * @param timeoutSeconds 超时时间（秒）
     * @return true 如果成功获取锁
     */
    private boolean tryLock(String lockKey, long timeoutSeconds) {
        if (StringUtils.isBlank(lockKey)) {
            log.warn("[DeviceLock] 锁键为空，无法获取锁");
            return false;
        }
        try {
            // 使用 RedisClient.tryLock() 方法，直接使用原生 Redis 连接
            // 不受 Spring 事务管理影响，确保在事务外执行
            boolean result = redisClient.tryLock(lockKey, timeoutSeconds, TimeUnit.SECONDS);
            if (!result) {
                log.debug("[DeviceLock] 获取锁失败: lockKey={}", lockKey);
            } else {
                log.debug("[DeviceLock] 成功获取锁: lockKey={}, timeout={}秒", lockKey, timeoutSeconds);
            }
            return result;
        } catch (Exception e) {
            log.error("[DeviceLock] 获取锁异常: lockKey={}, error={}", lockKey, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 释放锁
     * 使用 RedisClient.releaseLock() 方法，直接使用原生 Redis 连接，不受 Spring 事务管理影响
     *
     * @param lockKey 锁键
     */
    private void unlock(String lockKey) {
        if (StringUtils.isBlank(lockKey)) {
            return;
        }
        try {
            // 使用 RedisClient.releaseLock() 方法，直接使用原生 Redis 连接
            // 不受 Spring 事务管理影响，确保在事务外执行
            redisClient.releaseLock(lockKey);
            log.debug("[DeviceLock] 释放锁: lockKey={}", lockKey);
        } catch (Exception e) {
            log.error("[DeviceLock] 释放锁异常: lockKey={}, error={}", lockKey, e.getMessage(), e);
        }
    }

    /**
     * 带锁执行任务（自动释放锁）
     *
     * @param lockKey        锁键
     * @param timeoutSeconds 超时时间（秒）
     * @param task           要执行的任务
     * @param <T>            返回值类型
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
    private String buildStateLockKey(Long deviceId) {
        if (deviceId == null) {
            throw new IllegalArgumentException("DeviceId cannot be blank for lock key");
        }
        return LOCK_KEY_PREFIX_STATE + deviceId;
    }

    /**
     * 构建设备刀具变更锁键
     *
     * @param deviceId 设备ID
     * @return 锁键
     */
    private String buildToolChangeLockKey(Long deviceId) {
        if (deviceId == null) {
            throw new IllegalArgumentException("DeviceId cannot be blank for lock key");
        }
        return LOCK_KEY_PREFIX_TOOL_CHANGE + deviceId;
    }

    /**
     * 构建设备加工状态锁键
     *
     * @param deviceId 设备ID
     * @return 锁键
     */
    private String buildProductionLockKey(Long deviceId) {
        if (deviceId == null) {
            throw new IllegalArgumentException("DeviceId cannot be blank for lock key");
        }
        return LOCK_KEY_PREFIX_PRODUCTION + deviceId;
    }

}




