package com.weili.iot_portal.dal.mapper.device;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceProductionSummaryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产量统计 Mapper
 */
@Mapper
public interface DeviceProductionSummaryMapper extends BaseMapper<DeviceProductionSummaryDO> {
}


