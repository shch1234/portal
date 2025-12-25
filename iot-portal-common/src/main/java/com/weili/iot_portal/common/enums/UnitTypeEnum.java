package com.weili.iot_portal.common.enums;

import lombok.Getter;

/**
 * @EnumName: UnitTypeEnum
 * @Description:
 * @Author: luying
 **/
@Getter
public enum UnitTypeEnum {

    FACTORY("FACTORY", 1, "工厂"),
    WORKSHOP("WORKSHOP", 2, "车间"),
    PRODUCTION_LINE("PRODUCTION_LINE", 3, "产线"),
    ;
    private final String code;
    private final int levelNo;
    private final String name;

    UnitTypeEnum(String code, int levelNo, String name) {
        this.code = code;
        this.levelNo = levelNo;
        this.name = name;
    }

    public static int ofLevelNo(String code) {
        for (UnitTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value.levelNo;
            }
        }
        return -1;
    }
}
