package com.weili.iot_portal.dal.mapper.factory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.factory.FactoryMetricSummaryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工厂班次指标汇总 Mapper
 */
@Mapper
public interface FactoryMetricSummaryMapper extends BaseMapper<FactoryMetricSummaryDO> {
}

