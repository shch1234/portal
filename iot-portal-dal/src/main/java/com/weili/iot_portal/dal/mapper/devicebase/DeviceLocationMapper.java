package com.weili.iot_portal.dal.mapper.devicebase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceLocationDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceLocationMapper extends BaseMapper<DeviceLocationDO> {
}

