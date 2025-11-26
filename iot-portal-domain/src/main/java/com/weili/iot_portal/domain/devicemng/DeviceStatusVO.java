package com.weili.iot_portal.domain.devicemng;

import lombok.Data;

/**
 * 设备状态VO（简化版，仅包含状态信息）
 */
@Data
public class DeviceStatusVO {

    private String deviceId;
    private String deviceCode;
    private String deviceName;
    private String currentStatus; // 加工中/待机/故障/关机
    private Long statusChangeTs; // 状态变更时间戳
    private Boolean hasAlarm; // 是否有报警
}
