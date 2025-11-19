package com.weili.iot_portal.business.alarm_mgmt.domain.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 报警列表查询请求
 * 
 * <p>对应需求：4.3.2 报警列表
 * - 筛选：设备编号、时间范围、是否报警中
 */
@Data
public class AlarmListQueryReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工厂ID（必填，用于数据隔离）
     */
    private String factoryId;

    /**
     * 车间ID（可选，如果不填则查询该工厂下所有车间的报警）
     */
    private String workshopId;

    /**
     * 设备编号（可选，支持多个，用逗号分隔）
     * <p>示例：CNC001,CNC002
     */
    private String deviceCodes;

    /**
     * 时间范围-开始时间（毫秒时间戳，可选）
     */
    private Long startTime;

    /**
     * 时间范围-结束时间（毫秒时间戳，可选）
     */
    private Long endTime;

    /**
     * 是否报警中（可选）
     * <p>true：仅显示未结束记录（is_active = true）
     * <p>false：仅显示已结束记录（is_active = false）
     * <p>null：显示所有记录
     */
    private Boolean isActive;

    /**
     * 报警级别（可选，支持多个，用逗号分隔）
     * <p>示例：ERROR,CRITICAL
     */
    private String alarmLevels;

    /**
     * 分页页码（默认1）
     */
    private Integer pageNo = 1;

    /**
     * 分页大小（默认10）
     */
    private Integer pageSize = 10;
}

