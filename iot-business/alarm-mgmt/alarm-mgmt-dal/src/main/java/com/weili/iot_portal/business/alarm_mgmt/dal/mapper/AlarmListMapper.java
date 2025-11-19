package com.weili.iot_portal.business.alarm_mgmt.dal.mapper;

import com.weili.iot_portal.business.alarm_mgmt.dal.dataobject.AlarmItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 报警列表Mapper
 */
@Mapper
public interface AlarmListMapper {

    /**
     * 查询报警列表总数
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param workshopId 车间ID（可选）
     * @param deviceCodes 设备编号列表（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @param isActive 是否报警中（可选）
     * @param alarmLevels 报警级别列表（可选）
     * @return 总数
     */
    long countAlarmList(
            @Param("tenantId") String tenantId,
            @Param("factoryId") String factoryId,
            @Param("workshopId") String workshopId,
            @Param("deviceCodes") List<String> deviceCodes,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime,
            @Param("isActive") Boolean isActive,
            @Param("alarmLevels") List<String> alarmLevels);

    /**
     * 查询报警列表（分页）
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param workshopId 车间ID（可选）
     * @param deviceCodes 设备编号列表（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @param isActive 是否报警中（可选）
     * @param alarmLevels 报警级别列表（可选）
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 报警列表
     */
    List<AlarmItemDO> selectAlarmList(
            @Param("tenantId") String tenantId,
            @Param("factoryId") String factoryId,
            @Param("workshopId") String workshopId,
            @Param("deviceCodes") List<String> deviceCodes,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime,
            @Param("isActive") Boolean isActive,
            @Param("alarmLevels") List<String> alarmLevels,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 查询正在报警且时长最长的记录
     *
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param limit 数量
     * @return 报警记录
     */
    List<AlarmItemDO> selectTopActiveAlarms(
            @Param("tenantId") String tenantId,
            @Param("factoryId") String factoryId,
            @Param("limit") int limit);
}

