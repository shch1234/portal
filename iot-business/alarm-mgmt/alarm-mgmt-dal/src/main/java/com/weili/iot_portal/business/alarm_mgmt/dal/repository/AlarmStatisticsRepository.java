package com.weili.iot_portal.business.alarm_mgmt.dal.repository;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.alarm_mgmt.dal.dataobject.AlarmDeviceStatisticsDO;

import java.util.List;

/**
 * 报警统计仓储
 */
public interface AlarmStatisticsRepository {

    /**
     * 统计当前报警设备数量（按车间）
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param workshopId 车间ID（可选，null表示查询该工厂下所有车间）
     * @return 设备统计列表
     */
    List<AlarmDeviceStatisticsDO> countCurrentAlarmDevices(String tenantId, String factoryId, String workshopId);

    /**
     * 查询当前报警设备列表（分页）
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param workshopId 车间ID（可选）
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<AlarmDeviceStatisticsDO> selectCurrentAlarmDevices(
            String tenantId, String factoryId, String workshopId, int pageNo, int pageSize);
}

