package com.weili.iot_portal.dal.mapper.device;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceMetricSummaryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备指标 Mapper
 */
@Mapper
public interface DeviceMetricSummaryMapper extends BaseMapper<DeviceMetricSummaryDO> {
}


