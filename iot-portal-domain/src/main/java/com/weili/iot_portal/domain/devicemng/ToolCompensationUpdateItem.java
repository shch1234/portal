package com.weili.iot_portal.domain.devicemng;

import com.weili.iot_portal.common.enums.ToolCompensationDimension;
import lombok.Data;

@Data
public class ToolCompensationUpdateItem {

    private ToolCompensationDimension dimension;

    private Integer slot;

    private Double shapeValue;

    private Double wearValue;

    private Long ts;
}

