package com.weili.iot_portal.dal.mapper.devicebase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceRelationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备关系 Mapper
 */
@Mapper
public interface DeviceRelationMapper extends BaseMapper<DeviceRelationDO> {
}

