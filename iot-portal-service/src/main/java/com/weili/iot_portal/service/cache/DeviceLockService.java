package com.weili.iot_portal.service.cache;

import com.weili.basic.redis.client.RedisClient;
import com.weili.iot_portal.common.constant.RedisConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 设备分布式锁服务
 * <p>
 * 统一管理所有设备相关的分布式锁，提供统一的锁获取、释放和带锁执行方法
 * </p>
 *
 * @author luying
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceLockService {

    private final RedisClient redisClient;


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
     * 释放设备状态锁
     *
     * @param deviceId 设备ID
     */
    public void unlockState(Long deviceId) {
        unlock(buildStateLockKey(deviceId));
    }


    /**
     * 尝试获取设备告警锁
     *
     * @param deviceId       设备ID
     * @param timeoutSeconds 锁超时时间（秒）
     * @return true 如果成功获取锁
     */
    public boolean tryLockAlarm(Long deviceId, long timeoutSeconds) {
        return tryLock(buildAlarmLockKey(deviceId), timeoutSeconds);
    }

    /**
     * 释放设备告警锁
     *
     * @param deviceId 设备ID
     */
    public void unlockAlarm(Long deviceId) {
        unlock(buildAlarmLockKey(deviceId));
    }


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
     * 释放设备刀具变更锁
     *
     * @param deviceId 设备ID
     */
    public void unlockToolChange(Long deviceId) {
        unlock(buildToolChangeLockKey(deviceId));
    }


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
     * 释放设备加工状态锁
     *
     * @param deviceId 设备ID
     */
    public void unlockProduction(Long deviceId) {
        unlock(buildProductionLockKey(deviceId));
    }

    /**
     * 尝试获取理论节拍计算锁
     * <p>
     * 用于防止同一设备的理论节拍被多个线程同时计算，造成重复数据库查询
     * </p>
     *
     * @param deviceId       设备ID
     * @param timeoutSeconds 锁超时时间（秒）
     * @return true 如果成功获取锁
     */
    public boolean tryLockTheoreticalCycleCalculation(Long deviceId, long timeoutSeconds) {
        return tryLock(buildTheoreticalCycleCalculationLockKey(deviceId), timeoutSeconds);
    }

    /**
     * 释放理论节拍计算锁
     *
     * @param deviceId 设备ID
     */
    public void unlockTheoreticalCycleCalculation(Long deviceId) {
        unlock(buildTheoreticalCycleCalculationLockKey(deviceId));
    }

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
            boolean result = redisClient.tryLock(lockKey, timeoutSeconds, TimeUnit.SECONDS);
            if (!result) {
                log.debug("[DeviceLock] 获取锁失败: lockKey={}", lockKey);
            } else {
                log.debug("[DeviceLock] 成功获取锁: lockKey={}, timeout={}秒", lockKey, timeoutSeconds);
            }
            return result;
        } catch (RejectedExecutionException e) {
            // 应用关闭时，Netty 事件循环已终止，这是正常现象，降低日志级别
            log.debug("[DeviceLock] 获取锁失败（应用可能正在关闭）: lockKey={}, error={}", 
                    lockKey, e.getMessage());
            return false;
        } catch (Exception e) {
            // 检查是否是关闭相关的异常
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("event executor terminated") 
                    || errorMsg.contains("shutdown") 
                    || errorMsg.contains("terminated"))) {
                log.debug("[DeviceLock] 获取锁失败（应用可能正在关闭）: lockKey={}, error={}", 
                        lockKey, e.getMessage());
            } else {
                log.error("[DeviceLock] 获取锁异常: lockKey={}, error={}", lockKey, e.getMessage(), e);
            }
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
            redisClient.releaseLock(lockKey);
            log.debug("[DeviceLock] 释放锁: lockKey={}", lockKey);
        } catch (RejectedExecutionException e) {
            // 应用关闭时，Netty 事件循环已终止，这是正常现象，降低日志级别
            log.debug("[DeviceLock] 释放锁失败（应用可能正在关闭）: lockKey={}, error={}", 
                    lockKey, e.getMessage());
        } catch (Exception e) {
            // 检查是否是关闭相关的异常
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("event executor terminated") 
                    || errorMsg.contains("shutdown") 
                    || errorMsg.contains("terminated"))) {
                log.debug("[DeviceLock] 释放锁失败（应用可能正在关闭）: lockKey={}, error={}", 
                        lockKey, e.getMessage());
            } else {
                log.error("[DeviceLock] 释放锁异常: lockKey={}, error={}", lockKey, e.getMessage(), e);
            }
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
        return RedisConstant.LOCK_KEY_PREFIX_STATE + deviceId;
    }

    /**
     * 构建设备告警锁键
     *
     * @param deviceId 设备ID
     * @return 锁键
     */
    private String buildAlarmLockKey(Long deviceId) {
        if (deviceId == null) {
            throw new IllegalArgumentException("DeviceId cannot be blank for lock key");
        }
        return RedisConstant.LOCK_KEY_PREFIX_ALARM + deviceId;
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
        return RedisConstant.LOCK_KEY_PREFIX_TOOL_CHANGE + deviceId;
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
        return RedisConstant.LOCK_KEY_PREFIX_PRODUCTION + deviceId;
    }

    /**
     * 构建理论节拍计算锁键
     *
     * @param deviceId 设备ID
     * @return 锁键
     */
    private String buildTheoreticalCycleCalculationLockKey(Long deviceId) {
        if (deviceId == null) {
            throw new IllegalArgumentException("DeviceId cannot be blank for lock key");
        }
        return RedisConstant.LOCK_KEY_PREFIX_THEORETICAL_CYCLE_CALCULATION + deviceId;
    }
}




