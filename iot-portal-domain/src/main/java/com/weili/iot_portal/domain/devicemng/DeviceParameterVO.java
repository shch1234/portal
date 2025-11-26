package com.weili.iot_portal.domain.devicemng;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备参数视图
 */
@Data
public class DeviceParameterVO {

    private String deviceId;

    private Double theoreticalCycleHours;

    private Double plannedDowntimeHours;

    private Integer shiftMode;

    private List<String> shiftStartTimes;

    private LocalDateTime updateTime;

    private String remark;
}


