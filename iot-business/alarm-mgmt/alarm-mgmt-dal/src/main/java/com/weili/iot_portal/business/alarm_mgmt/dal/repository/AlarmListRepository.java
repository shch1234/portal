package com.weili.iot_portal.business.alarm_mgmt.dal.repository;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.alarm_mgmt.dal.dataobject.AlarmItemDO;

import java.util.List;

/**
 * 报警列表仓储接口
 */
public interface AlarmListRepository {

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
            String tenantId,
            String factoryId,
            String workshopId,
            List<String> deviceCodes,
            Long startTime,
            Long endTime,
            Boolean isActive,
            List<String> alarmLevels);

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
     * @param pageNo 页码
     * @param pageSize 页大小
     * @return 分页结果
     */
    PageResult<AlarmItemDO> selectAlarmList(
            String tenantId,
            String factoryId,
            String workshopId,
            List<String> deviceCodes,
            Long startTime,
            Long endTime,
            Boolean isActive,
            List<String> alarmLevels,
            int pageNo,
            int pageSize);

    /**
     * 查询正在报警且时长最长的记录
     *
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param limit 数量
     * @return 报警记录
     */
    List<AlarmItemDO> selectTopActiveAlarms(String tenantId, String factoryId, int limit);
}

