package com.weili.iot_portal.dal.mapper.effiency;

import com.weili.iot_portal.dal.dataobject.effiency.FactoryMetricsSummaryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工厂级效率指标Mapper
 */
@Mapper
public interface FactoryMetricsSummaryMapper {

    /**
     * 查询工厂级效率指标（按班次）
     * 
     * @param factoryId 工厂ID
     * @param shiftDate 班次日期
     * @param shiftCode 班次编码
     * @return 工厂级效率指标
     */
    FactoryMetricsSummaryDO selectFactoryMetrics(
            @Param("factoryId") String factoryId,
            @Param("shiftDate") String shiftDate,
            @Param("shiftCode") String shiftCode);

    /**
     * 查询工厂级效率指标历史趋势（过去N天）
     * 
     * @param factoryId 工厂ID
     * @param startDate 开始日期（包含）
     * @param endDate 结束日期（包含）
     * @return 工厂级效率指标列表
     */
    List<FactoryMetricsSummaryDO> selectFactoryMetricsHistory(
            @Param("factoryId") String factoryId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);
}

