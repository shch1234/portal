package com.weili.iot_portal.service.ingestion.support;

import com.weili.basic.common.util.JsonUtils;
import com.weili.basic.redis.client.RedisClient;
import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 设备匹配服务：基于 device_code 查询 device_base_info
 * <p>
 * 职责：
 * 1. 根据 deviceCode 匹配设备（带缓存优化）
 * 2. 同步 TB 设备ID（如果需要）
 * </p>
 * <p>
 * 性能优化：
 * - 使用Redis缓存设备信息，减少数据库查询
 * - 缓存TTL：1小时（设备信息变化不频繁）
 * - 设备ID更新时自动清除缓存
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMatchingService {

    private static final long CACHE_TTL_SECONDS = Duration.ofHours(1).toSeconds();

    private final DeviceInfoRepository deviceInfoRepository;
    private final RedisClient redisClient;

    /**
     * 匹配设备（带缓存）
     * <p>
     * 缓存策略：
     * 1. 先查Redis缓存
     * 2. 缓存未命中时查询数据库
     * 3. 查询结果写入缓存
     * </p>
     *
     * @param deviceCode 设备编号
     * @return 设备信息，如果未匹配则返回空
     */
    public Optional<DeviceInfoDO> match(String deviceCode) {
        if (StringUtils.isBlank(deviceCode)) {
            log.warn("[DeviceMatching] device_code 为空，跳过匹配: deviceCode={}", deviceCode);
            return Optional.empty();
        }

        // 1. 尝试从缓存获取
        Optional<DeviceInfoDO> cached = getFromCache(deviceCode);
        if (cached.isPresent()) {
            return cached;
        }

        // 2. 从数据库查询
        Optional<DeviceInfoDO> deviceOpt = queryFromDatabase(deviceCode);
        if (deviceOpt.isEmpty()) {
            return Optional.empty();
        }

        // 3. 写入缓存
        DeviceInfoDO device = deviceOpt.get();
        cacheDeviceInfo(deviceCode, device);

        return Optional.of(device);
    }

    /**
     * 从缓存获取设备信息
     *
     * @param deviceCode 设备编号
     * @return 设备信息，如果缓存未命中或无效则返回空
     */
    private Optional<DeviceInfoDO> getFromCache(String deviceCode) {
        String cacheKey = buildCacheKey(deviceCode);
        String cached = redisClient.get(cacheKey);
        if (StringUtils.isBlank(cached)) {
            return Optional.empty();
        }

        try {
            CachedDeviceInfo cachedInfo = JsonUtils.parseObject(cached, CachedDeviceInfo.class);
            if (cachedInfo == null) {
                log.warn("[DeviceMatching] 缓存数据为null: deviceCode={}", deviceCode);
                evictCacheUnchecked(deviceCode);
                return Optional.empty();
            }

            if (!cachedInfo.isValid()) {
                log.warn("[DeviceMatching] 缓存数据无效: deviceCode={}, cachedInfo={}", deviceCode, cached);
                evictCacheUnchecked(deviceCode);
                return Optional.empty();
            }

            DeviceInfoDO device = cachedInfo.toDeviceInfoDO();
            if (log.isDebugEnabled()) {
                log.debug("[DeviceMatching] 缓存命中: deviceCode={}, deviceId={}", deviceCode, device.getId());
            }
            return Optional.of(device);

        } catch (Exception e) {
            log.warn("[DeviceMatching] 解析缓存数据失败: deviceCode={}, cachedLength={}, error={}",
                    deviceCode, cached.length(), e.getMessage(), e);
            evictCacheUnchecked(deviceCode);
            return Optional.empty();
        }
    }

    /**
     * 从数据库查询设备信息
     *
     * @param deviceCode 设备编号
     * @return 设备信息，如果未匹配则返回空
     */
    private Optional<DeviceInfoDO> queryFromDatabase(String deviceCode) {
        DeviceInfoDO device = deviceInfoRepository.findActiveMonitoredByDeviceCode(deviceCode).orElse(null);
        if (device == null) {
            log.warn("[DeviceMatching] 设备未匹配: deviceCode={}", deviceCode);
            return Optional.empty();
        }
        return Optional.of(device);
    }

    /**
     * 同步 TB 设备ID（如果需要）
     * <p>
     * 更新规则：
     * 1. 如果 TB 传了 deviceId（tbDeviceId 不为空）
     * 2. 且数据库中的 tb_device_id 为空或不匹配
     * 3. 则更新数据库中的 tb_device_id
     * 4. 清除缓存（确保下次查询获取最新数据）
     * 5. 否则跳过
     * </p>
     *
     * @param device     设备信息
     * @param tbDeviceId TB传过来的设备ID
     */
    public void syncTbDeviceIdIfNeeded(DeviceInfoDO device, String tbDeviceId) {
        if (!shouldSyncTbDeviceId(device, tbDeviceId)) {
            return;
        }

        updateTbDeviceId(device, tbDeviceId);
    }

    /**
     * 判断是否需要同步 TB 设备ID
     *
     * @param device     设备信息
     * @param tbDeviceId TB传过来的设备ID
     * @return true 如果需要同步，false 如果不需要
     */
    private boolean shouldSyncTbDeviceId(DeviceInfoDO device, String tbDeviceId) {
        if (StringUtils.isBlank(tbDeviceId)) {
            return false;
        }
        String currentTbDeviceId = device.getTbDeviceId();
        return StringUtils.isBlank(currentTbDeviceId) || !tbDeviceId.equals(currentTbDeviceId);
    }

    /**
     * 更新 TB 设备ID
     *
     * @param device     设备信息
     * @param tbDeviceId TB传过来的设备ID
     */
    private void updateTbDeviceId(DeviceInfoDO device, String tbDeviceId) {
        String currentTbDeviceId = device.getTbDeviceId();
        log.info("[DeviceMatching] 同步tb_device_id: deviceCode={}, oldTbDeviceId={}, newTbDeviceId={}",
                device.getDeviceCode(), currentTbDeviceId, tbDeviceId);

        device.setTbDeviceId(tbDeviceId);
        deviceInfoRepository.update(device);

        // 清除缓存，确保下次查询获取最新数据
        evictCache(device.getDeviceCode());

        log.debug("[DeviceMatching] tb_device_id同步成功，缓存已清除");
    }

    /**
     * 缓存设备信息
     *
     * @param deviceCode 设备编号
     * @param device     设备信息
     */
    private void cacheDeviceInfo(String deviceCode, DeviceInfoDO device) {
        try {
            CachedDeviceInfo cachedInfo = CachedDeviceInfo.fromDeviceInfoDO(device);
            String cacheKey = buildCacheKey(deviceCode);
            redisClient.set(cacheKey, JsonUtils.toJsonString(cachedInfo), CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            if (log.isDebugEnabled()) {
                log.debug("[DeviceMatching] 设备信息已缓存: deviceCode={}, deviceId={}", deviceCode, device.getId());
            }
        } catch (Exception e) {
            log.warn("[DeviceMatching] 缓存设备信息失败: deviceCode={}, error={}", deviceCode, e.getMessage());
        }
    }

    /**
     * 清除设备信息缓存（公共方法，供其他服务调用）
     * <p>
     * 当设备信息被更新时，应调用此方法清除缓存，确保下次查询获取最新数据
     * </p>
     *
     * @param deviceCode 设备编号
     */
    public void evictCache(String deviceCode) {
        if (StringUtils.isBlank(deviceCode)) {
            return;
        }
        evictCacheUnchecked(deviceCode);
    }

    /**
     * 清除设备信息缓存（内部方法，不检查参数）
     * <p>
     * 用于内部调用，参数已由调用方验证
     * </p>
     *
     * @param deviceCode 设备编号（非空）
     */
    private void evictCacheUnchecked(String deviceCode) {
        String cacheKey = buildCacheKey(deviceCode);
        redisClient.delete(cacheKey);
        if (log.isDebugEnabled()) {
            log.debug("[DeviceMatching] 设备信息缓存已清除: deviceCode={}", deviceCode);
        }
    }

    /**
     * 构建缓存键
     *
     * @param deviceCode 设备编号
     * @return 缓存键
     */
    private String buildCacheKey(String deviceCode) {
        return String.format(RedisConstant.DEVICE_INFO_MATCH, deviceCode);
    }

    /**
     * 缓存的设备信息（只包含匹配所需的核心字段）
     */
    @Data
    private static class CachedDeviceInfo {
        private Long id;
        private String deviceCode;
        private String tbDeviceId;
        private Long orgFactoryId;

        /**
         * 从 DeviceInfoDO 创建 CachedDeviceInfo
         */
        static CachedDeviceInfo fromDeviceInfoDO(DeviceInfoDO device) {
            CachedDeviceInfo cached = new CachedDeviceInfo();
            cached.setId(device.getId());
            cached.setDeviceCode(device.getDeviceCode());
            cached.setTbDeviceId(device.getTbDeviceId());
            cached.setOrgFactoryId(device.getOrgFactoryId());
            return cached;
        }

        /**
         * 转换为 DeviceInfoDO
         */
        DeviceInfoDO toDeviceInfoDO() {
            DeviceInfoDO device = new DeviceInfoDO();
            device.setId(this.id);
            device.setDeviceCode(this.deviceCode);
            device.setTbDeviceId(this.tbDeviceId);
            device.setOrgFactoryId(this.orgFactoryId);
            return device;
        }

        /**
         * 验证缓存数据是否有效
         */
        boolean isValid() {
            return id != null && StringUtils.isNotBlank(deviceCode) && orgFactoryId != null;
        }
    }
}

