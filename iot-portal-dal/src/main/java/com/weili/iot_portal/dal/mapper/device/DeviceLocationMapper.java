package com.weili.iot_portal.dal.mapper.device;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceLocationDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceLocationMapper extends BaseMapper<DeviceLocationDO> {
}

