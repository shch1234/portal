package com.weili.iot_portal.dal.mapper.factory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.factory.FactoryMetricSummaryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工厂班次指标汇总 Mapper
 */
@Mapper
public interface FactoryMetricSummaryMapper extends BaseMapper<FactoryMetricSummaryDO> {
    /**
     * 查询工厂级效率指标（按班次）
     *
     * @param factoryId 工厂ID
     * @param shiftDate 班次日期
     * @param shiftCode 班次编码
     * @return 工厂级效率指标
     */
    FactoryMetricSummaryDO selectFactoryMetrics(
            @Param("factoryId") String factoryId,
            @Param("shiftDate") String shiftDate,
            @Param("shiftCode") Integer shiftCode);

    /**
     * 查询工厂级效率指标历史趋势（过去N天）
     *
     * @param factoryId 工厂ID
     * @param startDate 开始日期（包含）
     * @param endDate 结束日期（包含）
     * @return 工厂级效率指标列表
     */
    List<FactoryMetricSummaryDO> selectFactoryMetricsHistory(
            @Param("factoryId") String factoryId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);
}

