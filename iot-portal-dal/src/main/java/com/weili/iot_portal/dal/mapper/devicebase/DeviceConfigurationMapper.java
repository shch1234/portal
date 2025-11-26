package com.weili.iot_portal.dal.mapper.devicebase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceConfigurationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备配置 Mapper
 */
@Mapper
public interface DeviceConfigurationMapper extends BaseMapper<DeviceConfigurationDO> {
}

