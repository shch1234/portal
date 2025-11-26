package com.weili.iot_portal.dal.mapper.devicemng;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.devicemng.ProductionCounterDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产量统计 Mapper
 */
@Mapper
public interface ProductionCounterMapper extends BaseMapper<ProductionCounterDO> {
}


