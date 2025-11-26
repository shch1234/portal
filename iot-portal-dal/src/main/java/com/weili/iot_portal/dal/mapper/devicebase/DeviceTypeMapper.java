package com.weili.iot_portal.dal.mapper.devicebase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceTypeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备类型 Mapper
 */
@Mapper
public interface DeviceTypeMapper extends BaseMapper<DeviceTypeDO> {
}

