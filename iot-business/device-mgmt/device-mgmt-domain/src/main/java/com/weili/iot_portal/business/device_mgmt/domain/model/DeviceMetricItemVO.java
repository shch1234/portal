package com.weili.iot_portal.business.device_mgmt.domain.model;

import lombok.Data;

import java.util.List;

/**
 * 设备指标项
 */
@Data
public class DeviceMetricItemVO {

    private String shiftId;

    private Long shiftStartTs;

    private Long shiftEndTs;

    private List<DeviceMetricValueVO> values;

    private Long calculatedTime;
}


