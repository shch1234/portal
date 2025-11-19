package com.weili.iot_portal.business.device_mgmt.domain.model;

import lombok.Data;

@Data
public class ProgramInfoVO {

    private String deviceId;

    private String programName;

    private String programPath;

    private Integer currentLine;

    private String currentCode;

    private Long ts;
}

