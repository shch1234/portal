package com.weili.iot_portal.business.alarm_mgmt.dal.mapper;

import com.weili.iot_portal.business.alarm_mgmt.dal.dataobject.AlarmDeviceStatisticsDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 报警统计 Mapper
 * 
 * <p>查询设备管理模块的报警历史数据，按设备统计当前报警情况
 */
@Mapper
public interface AlarmStatisticsMapper {

    /**
     * 统计当前报警设备数量（按车间）
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param workshopId 车间ID（可选，null表示查询该工厂下所有车间）
     * @return 设备统计列表（每个设备一条记录）
     */
    List<AlarmDeviceStatisticsDO> countCurrentAlarmDevices(
            @Param("tenantId") String tenantId,
            @Param("factoryId") String factoryId,
            @Param("workshopId") String workshopId
    );

    /**
     * 查询当前报警设备列表（分页，按车间筛选）
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param workshopId 车间ID（可选，null表示查询该工厂下所有车间）
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 设备统计列表
     */
    List<AlarmDeviceStatisticsDO> selectCurrentAlarmDevices(
            @Param("tenantId") String tenantId,
            @Param("factoryId") String factoryId,
            @Param("workshopId") String workshopId,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    /**
     * 统计当前报警设备总数量（用于分页）
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param workshopId 车间ID（可选，null表示查询该工厂下所有车间）
     * @return 设备数量
     */
    long countCurrentAlarmDeviceTotal(
            @Param("tenantId") String tenantId,
            @Param("factoryId") String factoryId,
            @Param("workshopId") String workshopId
    );
}

