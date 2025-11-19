package com.weili.iot_portal.business.device_mgmt.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceStateSummaryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备状态汇总 Mapper
 */
@Mapper
public interface DeviceStateSummaryMapper extends BaseMapper<DeviceStateSummaryDO> {
}


