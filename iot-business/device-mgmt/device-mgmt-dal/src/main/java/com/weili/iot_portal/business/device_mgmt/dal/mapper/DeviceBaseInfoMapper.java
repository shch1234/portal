package com.weili.iot_portal.business.device_mgmt.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceBaseInfoDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备基础信息 Mapper
 */
@Mapper
public interface DeviceBaseInfoMapper extends BaseMapper<DeviceBaseInfoDO> {
}


