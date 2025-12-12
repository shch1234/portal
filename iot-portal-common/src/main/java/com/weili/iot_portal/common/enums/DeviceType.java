package com.weili.iot_portal.common.enums;

import lombok.Getter;

/**
 * @author luying
 * @className DeviceType
 * @description
 * @date 2025-12-11 10:50
 **/
@Getter
public enum DeviceType {

    MACHINE_TOOL("MACHINE_TOOL", "机床"),

    ROBOT("ROBOT", "机器人"),

    PLC("PLC", "PLC");

    private final String code;

    private final String description;

    DeviceType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
