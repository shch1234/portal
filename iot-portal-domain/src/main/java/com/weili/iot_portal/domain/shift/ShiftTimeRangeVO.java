package com.weili.iot_portal.domain.shift;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 班次时间范围VO（公共领域模型）
 * 
 * <p>用于各业务模块之间传递班次时间范围信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftTimeRangeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 班次编码
     */
    private String shiftCode;

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

