package com.weili.iot_portal.common.enums;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Getter;

/**
 * @ClassName: RoleSystemEnum
 * @Description:
 * @Author: luying
 * @Date: 2025-07-04 13:10
 **/
@Getter
public enum RoleCodeEnum {

    @JsonPropertyDescription("超级管理员")
    superAdmin,
    @JsonPropertyDescription("系统管理员")
    systemAdmin,
    @JsonPropertyDescription("普通员工")
    common
  ;

    public static boolean isSuperAdmin(String code) {
        return superAdmin.name().equals(code);
    }
}
