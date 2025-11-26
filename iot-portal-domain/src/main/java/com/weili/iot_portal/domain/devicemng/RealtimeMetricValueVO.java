package com.weili.iot_portal.domain.devicemng;

import lombok.Data;

@Data
public class RealtimeMetricValueVO {

    private String metric;

    private Double value;

    private Long ts;
}

