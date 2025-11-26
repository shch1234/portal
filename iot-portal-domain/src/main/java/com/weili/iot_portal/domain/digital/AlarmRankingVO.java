package com.weili.iot_portal.domain.digital;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 数字大屏报警排行展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmRankingVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceCode;

    private String deviceTypeName;

    private String deviceSubTypeName;

    private String alarmText;

    /**
     * 持续时长（毫秒）
     */
    private Long durationMs;
}


