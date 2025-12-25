package com.weili.iot_portal.dal.cache;

import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * 缓存的设备信息（只包含匹配所需的核心字段）
 * 用于减少 Redis 缓存大小，提高性能
 */
@Data
public class CachedDeviceInfo {
    private Long id;
    private String deviceCode;
    private String tbDeviceId;
    private Long orgFactoryId;

    /**
     * 从 DeviceInfoDO 创建 CachedDeviceInfo
     */
    public static CachedDeviceInfo fromDeviceInfoDO(DeviceInfoDO device) {
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
    public DeviceInfoDO toDeviceInfoDO() {
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
    public boolean isValid() {
        return id != null && StringUtils.isNotBlank(deviceCode) && orgFactoryId != null;
    }
}

