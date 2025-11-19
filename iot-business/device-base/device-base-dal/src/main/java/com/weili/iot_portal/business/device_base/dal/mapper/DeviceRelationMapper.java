package com.weili.iot_portal.business.device_base.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceRelationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备关系 Mapper
 */
@Mapper
public interface DeviceRelationMapper extends BaseMapper<DeviceRelationDO> {
}

