package com.weili.iot_portal.dal.mapper.device;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateSummaryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备状态汇总 Mapper
 */
@Mapper
public interface DeviceStateSummaryMapper extends BaseMapper<DeviceStateSummaryDO> {
}


