package com.weili.iot_portal.business.device_mgmt.service.support;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 班次信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftInfo {
    /**
     * 班次编码：SHIFT_1、SHIFT_2、SHIFT_3
     */
    private String code;

    /**
     * 班次名称：早班、中班、晚班
     */
    private String name;

    /**
     * 开始时间（HH:mm:ss格式）
     */
    private String startTime;

    /**
     * 结束时间（HH:mm:ss格式）
     */
    private String endTime;

    /**
     * 持续时长（小时）
     */
    private Integer durationHours;

    /**
     * 是否跨天
     */
    private Boolean crossDay;
}

