package com.weili.iot_portal.dal.mapper.device;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备基础信息 Mapper
 */
@Mapper
public interface DeviceInfoMapper extends BaseMapper<DeviceInfoDO> {
}

