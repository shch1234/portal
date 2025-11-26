package com.weili.iot_portal.domain.devicemng;

import lombok.Data;

import java.util.List;

/**
 * 报警历史响应
 */
@Data
public class AlarmHistoryVO {

    private String deviceId;

    private List<AlarmHistoryItemVO> alarms;

    private Long total;

    private Integer pageNo;

    private Integer pageSize;

    private Integer totalPages;
}


