package com.weili.iot_portal.common.enums;

import com.weili.basic.common.enums.IEnumBase;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author luying
 * @description: TODO
 * @date 2025/11/14 8:54
 */
@AllArgsConstructor
@Getter
public enum BizErrorCodeEnum implements IEnumBase {

    ILLEGAL("ILLEGAL", "参数非法"),
    OVER_LENGTH("OVER_LENGTH", "超出长度"),
    PERMISSION_ERROR("PERMISSION_ERROR", "权限校验失败"),
    DATA_OPERATE_ERROR("DATA_OPERATE_ERROR", "数据操作失败"),
    DB_DATABASE_ERROR("DB_DATABASE_ERROR", "数据库操作上失败"),

    //登录
    AUTH_LOGIN_FAIL("AUTH_LOGIN_FAIL", "登录失败，请联系管理员"),
    AUTH_LOGIN_EXPIRE("AUTH_LOGIN_EXPIRE", "登录过期"),
    LOGIN_USER_NOT_EXIST("LOGIN_USER_NOT_EXIST", "获取用户信息为空"),

;


    private final String code;
    private final String message;


    @Override
    public String getCode() {
        return this.code;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
