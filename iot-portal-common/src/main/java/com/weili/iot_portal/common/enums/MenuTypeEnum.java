package com.weili.iot_portal.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 菜单类型枚举类
 */
@Getter
@AllArgsConstructor
public enum MenuTypeEnum {

    DIR(1), // 目录
    MENU(2), // 菜单
    BUTTON(3) // 按钮
    ;
    private final Integer type;
}
