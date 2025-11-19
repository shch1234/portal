package com.weili.iot_portal.business.device_mgmt.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceParameterDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备参数 Mapper
 */
@Mapper
public interface DeviceParameterMapper extends BaseMapper<DeviceParameterDO> {
}


