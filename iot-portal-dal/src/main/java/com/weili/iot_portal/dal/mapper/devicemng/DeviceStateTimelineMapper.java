package com.weili.iot_portal.dal.mapper.devicemng;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateTimelineDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备状态时间线 Mapper
 */
@Mapper
public interface DeviceStateTimelineMapper extends BaseMapper<DeviceStateTimelineDO> {
}


