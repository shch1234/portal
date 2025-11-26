package com.weili.iot_portal.dal.mapper.devicebase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备基础信息 Mapper
 */
@Mapper
public interface DeviceBaseInfoMapper extends BaseMapper<DeviceBaseInfoDO> {
}

