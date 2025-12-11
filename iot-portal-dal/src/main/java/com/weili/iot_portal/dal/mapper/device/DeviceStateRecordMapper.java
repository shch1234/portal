package com.weili.iot_portal.dal.mapper.device;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备状态时间线 Mapper
 */
@Mapper
public interface DeviceStateRecordMapper extends BaseMapper<DeviceStateRecordDO> {
}


