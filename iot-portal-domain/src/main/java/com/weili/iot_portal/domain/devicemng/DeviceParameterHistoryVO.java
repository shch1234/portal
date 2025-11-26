package com.weili.iot_portal.domain.devicemng;

import lombok.Data;

import java.util.List;

/**
 * 设备参数历史响应
 */
@Data
public class DeviceParameterHistoryVO {

    private String deviceId;

    private List<DeviceParameterHistoryItemVO> history;

    private Long total;

    private Integer pageNo;

    private Integer pageSize;

    private Integer totalPages;
}


