package com.weili.iot_portal.business.efficiency_mgmt.dal.mapper;

import com.weili.iot_portal.business.efficiency_mgmt.dal.dataobject.DeviceEfficiencyMetricDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 效率指标Mapper
 */
@Mapper
public interface EfficiencyMetricMapper {

    /**
     * 查询效率指标列表总数
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param workshopId 车间ID（可选）
     * @param metricCode 指标代码
     * @param shiftDate 班次日期（格式：yyyy-MM-dd）
     * @param shiftCode 班次编码
     * @return 总数
     */
    long countDeviceMetrics(
            @Param("tenantId") String tenantId,
            @Param("factoryId") String factoryId,
            @Param("workshopId") String workshopId,
            @Param("metricCode") String metricCode,
            @Param("shiftDate") String shiftDate,
            @Param("shiftCode") String shiftCode);

    /**
     * 查询效率指标列表（分页、排序）
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param workshopId 车间ID（可选）
     * @param metricCode 指标代码
     * @param shiftDate 班次日期（格式：yyyy-MM-dd）
     * @param shiftCode 班次编码
     * @param sortBy 排序字段（metricValue 或 deviceCode）
     * @param sortDirection 排序方向（ASC 或 DESC）
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 设备效率指标列表
     */
    List<DeviceEfficiencyMetricDO> selectDeviceMetrics(
            @Param("tenantId") String tenantId,
            @Param("factoryId") String factoryId,
            @Param("workshopId") String workshopId,
            @Param("metricCode") String metricCode,
            @Param("shiftDate") String shiftDate,
            @Param("shiftCode") String shiftCode,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection,
            @Param("offset") int offset,
            @Param("limit") int limit);
}

