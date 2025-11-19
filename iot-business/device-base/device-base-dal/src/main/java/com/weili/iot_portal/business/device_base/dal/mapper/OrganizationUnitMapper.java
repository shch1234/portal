package com.weili.iot_portal.business.device_base.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.business.device_base.dal.dataobject.OrganizationUnitDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 组织单元 Mapper
 */
@Mapper
public interface OrganizationUnitMapper extends BaseMapper<OrganizationUnitDO> {
}

