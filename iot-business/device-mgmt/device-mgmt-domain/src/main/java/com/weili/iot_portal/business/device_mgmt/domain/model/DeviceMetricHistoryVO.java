package com.weili.iot_portal.business.device_mgmt.domain.model;

import lombok.Data;

import java.util.List;

/**
 * 指标查询响应
 */
@Data
public class DeviceMetricHistoryVO {

    private List<DeviceMetricItemVO> metrics;

    private Long total;

    private Integer pageNo;

    private Integer pageSize;

    private Integer totalPages;
}


