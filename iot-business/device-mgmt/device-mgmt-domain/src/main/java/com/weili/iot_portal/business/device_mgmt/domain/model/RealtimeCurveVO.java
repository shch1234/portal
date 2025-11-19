package com.weili.iot_portal.business.device_mgmt.domain.model;

import lombok.Data;

import java.util.List;

/**
 * 曲线响应
 */
@Data
public class RealtimeCurveVO {

    private String metric;

    private Long startTs;

    private Long endTs;

    private List<RealtimeCurvePointVO> points;
}


