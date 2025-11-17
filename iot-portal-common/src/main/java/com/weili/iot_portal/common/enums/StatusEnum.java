package com.weili.iot_portal.common.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum StatusEnum {

    ENABLE(0, "开启"),
    DISABLE(1, "关闭");

    /**
     * 状态值
     */
    private final Integer status;
    /**
     * 状态名
     */
    private final String name;

    StatusEnum(Integer status, String name) {
        this.status = status;
        this.name = name;
    }

    public static boolean isEnable(Integer status) {
        return ObjUtil.equal(ENABLE.status, status);
    }

    public static boolean isDisable(Integer status) {
        return ObjUtil.equal(DISABLE.status, status);
    }
}
