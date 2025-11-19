package com.weili.iot_portal.business.device_mgmt.domain.model;

import com.weili.iot_portal.business.device_mgmt.domain.enums.ProgramCodeType;
import lombok.Data;

@Data
public class ProgramCodeVO {

    private ProgramCodeType type;

    private String content;

    private Long ts;
}

