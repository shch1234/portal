package com.weili.iot_portal.business.alarm_mgmt.service;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.alarm_mgmt.domain.model.AlarmDeviceItemVO;
import com.weili.iot_portal.business.alarm_mgmt.domain.model.CurrentAlarmDeviceCountVO;
import com.weili.iot_portal.business.alarm_mgmt.domain.model.request.CurrentAlarmDeviceQueryReq;

/**
 * 报警统计服务
 */
public interface AlarmStatisticsService {

    /**
     * 获取当前报警设备数量（按车间）
     * 
     * <p>统计指定工厂（或车间）内所有正在报警的设备个数。
     * 注意：一台设备可能有多个报警，但只统计设备个数（去重）。
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID（必填）
     * @param workshopId 车间ID（可选，null表示查询该工厂下所有车间）
     * @return 当前报警设备数量
     */
    CurrentAlarmDeviceCountVO getCurrentAlarmDeviceCount(String tenantId, String factoryId, String workshopId);

    /**
     * 查询当前报警设备列表（支持车间筛选，分页）
     * 
     * <p>返回正在报警的设备列表，每个设备显示其报警数量。
     * 用于用户点击"当前报警设备数量"后查看详细列表。
     * 
     * @param tenantId 租户ID
     * @param request 查询请求
     * @return 分页结果
     */
    PageResult<AlarmDeviceItemVO> getCurrentAlarmDeviceList(String tenantId, CurrentAlarmDeviceQueryReq request);
}

