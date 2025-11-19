package com.weili.iot_portal.business.device_mgmt.domain.model;

import lombok.Data;

/**
 * 报警历史条目
 */
@Data
public class AlarmHistoryItemVO {

    private String alarmId;

    private String deviceId;

    private String alarmCode;

    private String alarmText;

    private String alarmLevel;

    private Long startTs;

    private Long endTs;

    private Long durationMs;

    private Boolean inProgress;
}


