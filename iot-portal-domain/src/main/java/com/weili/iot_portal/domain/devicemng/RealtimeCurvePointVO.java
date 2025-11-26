package com.weili.iot_portal.domain.devicemng;

import lombok.Data;

/**
 * 曲线数据点
 */
@Data
public class RealtimeCurvePointVO {

    private Long ts;

    private Double value;
}


