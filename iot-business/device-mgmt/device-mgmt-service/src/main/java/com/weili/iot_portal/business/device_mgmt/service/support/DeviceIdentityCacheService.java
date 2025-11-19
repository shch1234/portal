package com.weili.iot_portal.business.device_mgmt.service.support;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.util.JsonUtils;
import com.weili.basic.redis.client.RedisClient;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceBaseInfoDO;
import com.weili.iot_portal.business.device_mgmt.dal.repository.DeviceBaseInfoRepository;
import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.service.ingestion.support.UnknownDeviceAlertService;
import lombok.AllArgsConstructor;
import lombok.Data;
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
    private final DeviceBaseInfoRepository deviceBaseInfoRepository;
    private final UnknownDeviceAlertService unknownDeviceAlertService;

    public DeviceIdentityCacheService(RedisClient redisClient,
                                      DeviceBaseInfoRepository deviceBaseInfoRepository,
                                      UnknownDeviceAlertService unknownDeviceAlertService) {
        this.redisClient = redisClient;
        this.deviceBaseInfoRepository = deviceBaseInfoRepository;
        this.unknownDeviceAlertService = unknownDeviceAlertService;
    }

    public DeviceIdentity resolveByDeviceCode(String tenantId, String deviceCode, String tbDeviceId, String source) {
        if (StringUtils.isBlank(deviceCode)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备编号不能为空");
        }
        String cacheKey = buildKey(deviceCode);
        String cached = redisClient.get(cacheKey);
        if (StringUtils.isNotBlank(cached)) {
            return JsonUtils.parseObject(cached, DeviceIdentity.class);
        }
        DeviceBaseInfoDO device = deviceBaseInfoRepository.findByDeviceCode(tenantId, deviceCode)
                .orElseGet(() -> handleUnknownDevice(tenantId, deviceCode, tbDeviceId, source));
        if (StringUtils.isBlank(device.getFactoryId())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备未关联工厂");
        }
        DeviceIdentity identity = new DeviceIdentity(device.getId(), device.getFactoryId());
        cache(deviceCode, identity);
        return identity;
    }

    private DeviceBaseInfoDO handleUnknownDevice(String tenantId, String deviceCode, String tbDeviceId, String source) {
        unknownDeviceAlertService.record(tenantId, deviceCode, tbDeviceId, source);
        throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                "设备未建档，请检查设备编号或在组织架构中新增设备");
    }

    public void refresh(DeviceBaseInfoDO device) {
        if (device == null || StringUtils.isBlank(device.getDeviceCode())) {
            return;
        }
        if (StringUtils.isBlank(device.getFactoryId())) {
            evict(device.getDeviceCode());
            return;
        }
        cache(device.getDeviceCode(), new DeviceIdentity(device.getId(), device.getFactoryId()));
    }

    public void refresh(DeviceBaseInfoDO device, String oldDeviceCode) {
        if (StringUtils.isNotBlank(oldDeviceCode) &&
                !StringUtils.equals(oldDeviceCode, device.getDeviceCode())) {
            evict(oldDeviceCode);
        }
        refresh(device);
    }

    public void evict(String deviceCode) {
        redisClient.delete(buildKey(deviceCode));
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

    @Data
    @AllArgsConstructor
    public static class DeviceIdentity {
        private String deviceId;
        private String factoryId;
    }
}

