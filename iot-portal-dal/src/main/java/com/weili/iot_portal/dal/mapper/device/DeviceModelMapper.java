package com.weili.iot_portal.dal.mapper.device;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceModelDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备型号 Mapper
 */
@Mapper
public interface DeviceModelMapper extends BaseMapper<DeviceModelDO> {
}

