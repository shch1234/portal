package com.weili.iot_portal.business.device_mgmt.domain.model;

import lombok.Data;

@Data
public class DeviceMetricValueVO {

    private String code;

    private String name;

    private String unit;

    private Double value;
}

