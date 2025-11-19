package com.weili.iot_portal.business.device_mgmt.domain.model;

import com.weili.iot_portal.business.device_mgmt.domain.enums.ToolCompensationDimension;
import lombok.Data;

@Data
public class ToolCompensationUpdateItem {

    private ToolCompensationDimension dimension;

    private Integer slot;

    private Double shapeValue;

    private Double wearValue;

    private Long ts;
}

