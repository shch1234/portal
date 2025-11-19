package com.weili.iot_portal.business.device_mgmt.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.ProductionCounterDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产量统计 Mapper
 */
@Mapper
public interface ProductionCounterMapper extends BaseMapper<ProductionCounterDO> {
}


