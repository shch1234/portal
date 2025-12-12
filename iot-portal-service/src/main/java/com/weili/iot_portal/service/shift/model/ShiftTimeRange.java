package com.weili.iot_portal.service.shift.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 班次时间范围
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftTimeRange {
    /**
     * 班次编码（TINYINT UNSIGNED）
     * 编码映射：1-一班 2-二班 3-三班
     */
    private Integer shiftCode;

    /**
     * 班次名称
     */
    private String shiftName;

    /**
     * 班次开始时间戳（毫秒）
     */
    private Long startTs;

    /**
     * 班次结束时间戳（毫秒）
     */
    private Long endTs;

    /**
     * 班次持续时长（毫秒）
     */
    private Long durationMs;
}

