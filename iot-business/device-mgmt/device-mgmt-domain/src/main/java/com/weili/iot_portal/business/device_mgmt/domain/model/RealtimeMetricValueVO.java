package com.weili.iot_portal.business.device_mgmt.domain.model;

import lombok.Data;

@Data
public class RealtimeMetricValueVO {

    private String metric;

    private Double value;

    private Long ts;
}

