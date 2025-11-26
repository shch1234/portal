package com.weili.iot_portal.domain.devicemng;

import lombok.Data;

/**
 * 班次产量项
 */
@Data
public class ProductionHistoryItemVO {

    private String shiftDate;

    private String shiftCode;

    private Long shiftStartTs;

    private Long shiftEndTs;

    private Integer partCount;

    private Integer qualifiedCount;

    private Integer defectCount;

    private String countMethod;

    private Boolean finalized;

    private Long calculatedTime;
}


