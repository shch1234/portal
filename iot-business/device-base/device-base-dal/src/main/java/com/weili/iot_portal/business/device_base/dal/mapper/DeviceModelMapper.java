package com.weili.iot_portal.business.device_base.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceModelDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备型号 Mapper
 */
@Mapper
public interface DeviceModelMapper extends BaseMapper<DeviceModelDO> {
}

