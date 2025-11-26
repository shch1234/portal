package com.weili.iot_portal.dal.mapper.devicebase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceModelDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备型号 Mapper
 */
@Mapper
public interface DeviceModelMapper extends BaseMapper<DeviceModelDO> {
}

