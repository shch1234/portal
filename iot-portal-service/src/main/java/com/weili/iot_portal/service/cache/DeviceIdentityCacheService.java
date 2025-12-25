package com.weili.iot_portal.service.cache;

import com.weili.basic.common.util.JsonUtils;
import com.weili.basic.redis.client.RedisClient;
import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.domain.ingestion.DeviceIdentity;
import com.weili.iot_portal.service.ingestion.support.UnknownDeviceAlertService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 设备身份缓存（威力编号 -> DeviceId/FactoryId）
 */
@Service
public class DeviceIdentityCacheService {

    private static final long DEFAULT_TTL_SECONDS = Duration.ofHours(6).toSeconds();

    private final RedisClient redisClient;
    private final DeviceInfoRepository deviceInfoRepository;
    private final UnknownDeviceAlertService unknownDeviceAlertService;

    public DeviceIdentityCacheService(RedisClient redisClient,
                                      DeviceInfoRepository deviceInfoRepository,
                                      UnknownDeviceAlertService unknownDeviceAlertService) {
        this.redisClient = redisClient;
        this.deviceInfoRepository = deviceInfoRepository;
        this.unknownDeviceAlertService = unknownDeviceAlertService;
    }

    public DeviceIdentity resolveByDeviceCode(String deviceCode, String tbDeviceId, String source) {
        if (StringUtils.isBlank(deviceCode)) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_CODE_EMPTY);
        }
        String cacheKey = buildKey(deviceCode);
        String cached = redisClient.get(cacheKey);
        if (StringUtils.isNotBlank(cached)) {
            DeviceIdentity identity = JsonUtils.parseObject(cached, DeviceIdentity.class);
            // 验证缓存中的身份信息是否完整
            if (identity != null && identity.deviceInfoId() != null
                    && identity.orgFactoryId() != null) {
                return identity;
            }
            // 缓存数据不完整，清除缓存并重新查询
            redisClient.delete(cacheKey);
        }
        DeviceInfoDO device = deviceInfoRepository.findByDeviceCode(deviceCode)
                .orElseGet(() -> handleUnknownDevice(deviceCode, tbDeviceId, source));
        if (device.getId() == null) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_INFO_NOT_FOUND,
                    "设备信息ID为空，设备编号: " + deviceCode);
        }
        if (device.getOrgFactoryId() == null) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_NOT_ASSOCIATED_FACTORY);
        }
        DeviceIdentity identity = new DeviceIdentity(device.getId(), device.getOrgFactoryId());
        cache(deviceCode, identity);
        return identity;
    }

    private DeviceInfoDO handleUnknownDevice(String deviceCode, String tbDeviceId, String source) {
        unknownDeviceAlertService.record(deviceCode, tbDeviceId, source);
        throw new IotPortalException(IotPortalErrorCode.DEVICE_NOT_REGISTERED);
    }

    /**
     * 刷新设备身份缓存（对应 device_info 表的 org_factory_id）
     */
    public void refresh(DeviceInfoDO device) {
        if (device == null || StringUtils.isBlank(device.getDeviceCode())) {
            return;
        }
        if (device.getOrgFactoryId() != null) {
            evict(device.getDeviceCode());
            return;
        }
        cache(device.getDeviceCode(), new DeviceIdentity(device.getId(), device.getOrgFactoryId()));
    }

    public void refresh(DeviceInfoDO device, String oldDeviceCode) {
        if (StringUtils.isNotBlank(oldDeviceCode) &&
                !StringUtils.equals(oldDeviceCode, device.getDeviceCode())) {
            evict(oldDeviceCode);
        }
        refresh(device);
    }

    public void evict(String deviceCode) {
        redisClient.delete(buildKey(deviceCode));
    }


    public String getFactoryId(Long deviceId) {
        return redisClient.get(formatKey(deviceId));
    }

    public void putFactoryId(Long deviceId, Long factoryId) {
        if (factoryId == null) {
            redisClient.delete(formatKey(deviceId));
            return;
        }
        redisClient.set(formatKey(deviceId), String.valueOf(factoryId), DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
    }


    private void cache(String deviceCode, DeviceIdentity identity) {
        redisClient.set(buildKey(deviceCode),
                JsonUtils.toJsonString(identity),
                DEFAULT_TTL_SECONDS,
                TimeUnit.SECONDS);
    }

    private String buildKey(String deviceCode) {
        return String.format(RedisConstant.DEVICE_CODE_IDENTITY, deviceCode);
    }

    private String formatKey(Long deviceId) {
        return String.format(RedisConstant.DEVICE_FACTORY, deviceId);
    }


}

