package com.weili.iot_portal.domain.devicemng;

import com.weili.iot_portal.common.enums.ProgramCodeType;
import lombok.Data;

@Data
public class ProgramCodeVO {

    private ProgramCodeType type;

    private String content;

    private Long ts;
}

