package com.weili.iot_portal.service.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceToolCompensationDO;
import com.weili.iot_portal.dal.repository.device.DeviceToolCompensationRepository;
import com.weili.iot_portal.service.cache.DeviceLockService;
import com.weili.iot_portal.service.cache.DeviceToolCacheService;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceToolEventFields;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

/**
 * 设备刀具补偿数据写入服务
 * <p>
 * 提供统一的补偿数据写入逻辑，确保缓存与数据库的一致性
 * </p>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceToolCompensationService {

    private final DeviceToolCompensationRepository deviceToolCompensationRepository;
    private final DeviceToolCacheService deviceToolCacheService;
    private final DeviceLockService deviceLockService;

    /**
     * 版本化覆盖：刀补补偿数据的写入逻辑
     * <p>
     * 业务规则：
     * 1. 一个设备可以有多个刀补号（deviceId + toolHolderNo 唯一标识）
     * 2. 一个刀补号对应一组刀补数据（compValueJson）
     * 3. 如果表中没有该设备的该刀补号，就写入新记录（版本号=1）
     * 4. 如果已经有该刀补号，但刀补值不一样，则做版本管理：
     *    - 关闭旧记录（设置 endTs 和 active=0）
     *    - 创建新记录（版本号=旧版本号+1）
     * 5. 如果已经有该刀补号，且刀补值相同，则跳过（不做任何操作）
     * </p>
     * <p>
     * 性能优化：
     * - 先查Redis缓存，如果缓存命中且值相同，验证数据库存在后直接跳过（减少数据库查询）
     * - 如果缓存未命中或值不同，再查数据库
     * - 写入数据库后，同步更新缓存
     * </p>
     * <p>
     * 缓存一致性保障：
     * - 缓存命中时，验证数据库是否存在记录，防止缓存与数据库不一致
     * - 如果缓存存在但数据库不存在，删除缓存并强制写入
     * </p>
     *
     * @param deviceId 设备ID
     * @param factoryId 工厂ID
     * @param holderNumber 刀补号
     * @param compValue 刀补值（JSON Map）
     * @param eventTimestamp 事件时间戳（毫秒）
     * @param logPrefix 日志前缀（用于区分调用来源，如"[DeviceToolEventHandler]"或"[DeviceToolChangeEventHandler]"）
     */
    public void upsertCompensation(Long deviceId, Long factoryId,
                                   String holderNumber, Map<String, Object> compValue, Long eventTimestamp,
                                   String logPrefix) {
        // 使用分布式锁确保同一设备的补偿数据写入操作串行化，避免并发冲突
        // 注意：锁是基于设备ID的，同一设备的不同刀补号会串行处理，但这是可以接受的
        if (!deviceLockService.tryLockToolChange(deviceId, DeviceToolEventFields.LOCK_TIMEOUT_SECONDS_TOOL_CHANGE)) {
            log.warn("{} 获取设备刀具锁失败，跳过写入: deviceId={}, holderNumber={}",
                    logPrefix, deviceId, holderNumber);
            // 如果获取锁失败，可以选择抛出异常或跳过，这里选择跳过以避免阻塞
            // 如果后续需要重试，可以在调用方处理
            return;
        }

        try {
            // 1. 先查Redis缓存（性能优化：减少数据库查询）
            Map<String, Object> cachedCompValue = deviceToolCacheService.getActiveCompensation(deviceId, holderNumber);
            if (cachedCompValue != null && Objects.equals(cachedCompValue, compValue)) {
                // 缓存命中且值相同，但需要验证数据库是否真的存在记录（防止缓存与数据库不一致）
                try {
                    DeviceToolCompensationDO active = deviceToolCompensationRepository.findActive(deviceId, holderNumber);
                    if (active != null) {
                        // 数据库中存在记录，缓存一致，跳过写入
                        log.debug("{} 刀补值未变化（缓存命中且数据库确认），跳过写入: deviceId={}, holderNumber={}",
                                logPrefix, deviceId, holderNumber);
                        return;
                    } else {
                        // 缓存中有数据但数据库中不存在，可能是缓存不一致，需要写入
                        log.warn("{} 缓存命中但数据库无记录，可能存在缓存不一致，强制写入: deviceId={}, holderNumber={}",
                                logPrefix, deviceId, holderNumber);
                        // 删除不一致的缓存，继续执行写入流程
                        deviceToolCacheService.deleteActiveCompensation(deviceId, holderNumber);
                    }
                } catch (RecoverableDataAccessException e) {
                    // 数据库连接失败，但缓存命中且值相同，可以信任缓存（临时故障降级）
                    log.warn("{} 数据库连接失败，但缓存命中且值相同，信任缓存并跳过写入: deviceId={}, holderNumber={}, error={}",
                            logPrefix, deviceId, holderNumber, e.getMessage());
                    return;
                }
            }

            // 2. 缓存未命中或值不同，查询数据库（使用行锁防止并发修改）
            // 使用 SELECT FOR UPDATE NOWAIT 锁定行，避免长时间等待导致锁超时
            DeviceToolCompensationDO active;
            try {
                active = deviceToolCompensationRepository.findActiveWithLock(deviceId, holderNumber);
            } catch (org.springframework.dao.CannotAcquireLockException e) {
                // 行锁获取失败（NOWAIT），可能是其他事务正在处理，记录日志并跳过
                // 由于已有分布式锁保护，这种情况应该很少发生，如果发生则跳过本次写入
                log.warn("{} 无法获取数据库行锁，跳过写入（可能其他事务正在处理）: deviceId={}, holderNumber={}",
                        logPrefix, deviceId, holderNumber);
                return;
            } catch (RecoverableDataAccessException e) {
                // 数据库连接失败，无法继续处理，抛出异常让调用方处理（可能需要重试或记录到队列）
                log.error("{} 数据库连接失败，无法查询或写入补偿数据: deviceId={}, holderNumber={}, error={}",
                        logPrefix, deviceId, holderNumber, e.getMessage());
                throw e;
            }

            // 3. 如果找到活跃记录且补偿值相同，更新缓存并跳过写入
            if (active != null && Objects.equals(active.getCompValueJson(), compValue)) {
                // 缓存可能过期或不存在，更新缓存
                deviceToolCacheService.cacheActiveCompensation(deviceId, holderNumber, compValue);
                log.debug("{} 刀补值未变化（数据库确认），跳过写入: deviceId={}, holderNumber={}",
                        logPrefix, deviceId, holderNumber);
                return;
            }

            // 时间戳使用毫秒（数据库存储单位为毫秒）
            long ts = eventTimestamp != null
                    ? eventTimestamp
                    : System.currentTimeMillis();

            int nextVersion = DeviceToolEventFields.INITIAL_VERSION;

            // 4. 如果找到活跃记录但补偿值不同，关闭旧记录
            if (active != null) {
                log.debug("{} 刀补值变化，关闭旧记录并创建新记录: deviceId={}, holderNumber={}, oldVersion={}",
                        logPrefix, deviceId, holderNumber, active.getVersion());
                // 使用 LambdaUpdateWrapper 仅更新 active 和 end_ts 字段，避免更新其他字段导致唯一约束冲突
                deviceToolCompensationRepository.deactivateById(active.getId(), ts, DeviceToolEventFields.ACTIVE_STATUS_DISABLED);
                nextVersion = (active.getVersion() != null ? active.getVersion() + 1 : DeviceToolEventFields.INITIAL_VERSION);
                // 删除旧缓存（补偿值已变化）
                deviceToolCacheService.deleteActiveCompensation(deviceId, holderNumber);
            } else {
                log.debug("{} 首次写入刀补数据: deviceId={}, holderNumber={}",
                        logPrefix, deviceId, holderNumber);
            }

            // 5. 创建新记录
            DeviceToolCompensationDO record = new DeviceToolCompensationDO();
            record.setDeviceInfoId(deviceId);
            record.setOrgFactoryId(factoryId);
            record.setToolHolderNo(holderNumber);
            record.setCompValueJson(compValue);
            record.setVersion(nextVersion);
            record.setStartTs(ts);
            record.setEndTs(null);  // NULL 表示当前有效
            record.setActive(DeviceToolEventFields.ACTIVE_STATUS_ENABLED);
            deviceToolCompensationRepository.insert(record);

            // 6. 同步更新缓存（写入成功后）
            deviceToolCacheService.cacheActiveCompensation(deviceId, holderNumber, compValue);

            log.debug("{} 刀补数据写入成功: deviceId={}, holderNumber={}, version={}",
                    logPrefix, deviceId, holderNumber, nextVersion);
        } finally {
            // 释放分布式锁
            deviceLockService.unlockToolChange(deviceId);
        }
    }
}
