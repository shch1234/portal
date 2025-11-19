package com.weili.iot_portal.business.alarm_mgmt.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 报警排行项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmRankingItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceId;

    private String deviceCode;

    private String deviceTypeName;

    private String deviceSubTypeName;

    private String alarmText;

    private Long durationMs;
}


