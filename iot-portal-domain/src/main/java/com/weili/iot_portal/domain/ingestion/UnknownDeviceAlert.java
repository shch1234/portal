package com.weili.iot_portal.domain.ingestion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 未知设备告警信息
 * 用于记录无法匹配的设备信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnknownDeviceAlert {
    private String deviceCode;
    private String tbDeviceId;
    private String source;
    private long timestamp;
}

