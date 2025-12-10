package com.weili.iot_portal.dal.mapper.device;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceShiftConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 班次配置 Mapper
 */
@Mapper
public interface DeviceShiftConfigMapper extends BaseMapper<DeviceShiftConfigDO> {
}

