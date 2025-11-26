package com.weili.iot_portal.domain.devicemng;

import lombok.Data;

import java.util.List;

/**
 * 设备参数历史项
 */
@Data
public class DeviceParameterHistoryItemVO {

    private Long timestamp;

    private Double theoreticalCycleHours;

    private Double plannedDowntimeHours;

    private Integer shiftMode;

    private List<String> shiftStartTimes;

    private String updatedBy;

    private String remark;
}


