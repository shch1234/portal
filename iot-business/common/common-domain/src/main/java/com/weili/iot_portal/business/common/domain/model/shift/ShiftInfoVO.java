package com.weili.iot_portal.business.common.domain.model.shift;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 班次信息VO（公共领域模型）
 * 
 * <p>用于各业务模块之间传递班次信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 班次编码：SHIFT_1、SHIFT_2、SHIFT_3
     */
    private String shiftCode;

    /**
     * 班次名称：早班、中班、晚班
     */
    private String shiftName;

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

    /**
     * 班次日期（yyyy-MM-dd格式）
     * <p>根据当前时间和班次配置计算得出
     */
    private String shiftDate;
}

