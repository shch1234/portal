package com.weili.iot_portal.business.device_mgmt.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.ShiftConfigurationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 班次配置 Mapper
 */
@Mapper
public interface ShiftConfigurationMapper extends BaseMapper<ShiftConfigurationDO> {
}

