package com.weili.iot_portal.common.enums;

/**
 * 设备参数类型
 */
public enum DeviceParameterTypeEnum {

    THEORETICAL_CYCLE,
    PLANNED_DOWNTIME,
    SHIFT_MODE,
    SHIFT_START_TIMES,
    REMARK;

    public String getType() {
        return name();
    }
}


