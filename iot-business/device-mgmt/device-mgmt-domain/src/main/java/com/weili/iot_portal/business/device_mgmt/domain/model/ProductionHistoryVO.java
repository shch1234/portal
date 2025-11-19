package com.weili.iot_portal.business.device_mgmt.domain.model;

import lombok.Data;

import java.util.List;

/**
 * 产量历史返回
 */
@Data
public class ProductionHistoryVO {

    private String deviceId;

    private List<ProductionHistoryItemVO> items;

    private Long total;

    private Integer pageNo;

    private Integer pageSize;

    private Integer totalPages;
}


