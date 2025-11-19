package com.weili.iot_portal.business.device_mgmt.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceStateTimelineDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备状态时间线 Mapper
 */
@Mapper
public interface DeviceStateTimelineMapper extends BaseMapper<DeviceStateTimelineDO> {
}


