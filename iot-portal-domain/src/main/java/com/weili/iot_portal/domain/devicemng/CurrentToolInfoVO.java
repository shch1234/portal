package com.weili.iot_portal.domain.devicemng;

import lombok.Data;

/**
 * 当前刀具信息
 */
@Data
public class CurrentToolInfoVO {

    private String toolNumber;

    private String toolHolderNumber;

    private Double lengthComp;

    private Double radiusComp;

    private Double lengthWear;

    private Double radiusWear;

    private Long ts;
}

