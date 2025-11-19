package com.weili.iot_portal.business.device_mgmt.domain.model;

import lombok.Data;

@Data
public class ToolCompensationSlotVO {

    private Integer slot;

    private Double shapeValue;

    private Double wearValue;

    private Long ts;
}

