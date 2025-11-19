package com.weili.iot_portal.business.device_base.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceConfigurationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备配置 Mapper
 */
@Mapper
public interface DeviceConfigurationMapper extends BaseMapper<DeviceConfigurationDO> {
}

