package com.weili.iot_portal.business.device_base.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceTypeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备类型 Mapper
 */
@Mapper
public interface DeviceTypeMapper extends BaseMapper<DeviceTypeDO> {
}

