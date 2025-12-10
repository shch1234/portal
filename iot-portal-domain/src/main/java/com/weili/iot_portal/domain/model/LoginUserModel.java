package com.weili.iot_portal.domain.model;

import lombok.Data;

/**
 * @author luying
 * @className LoginUserModel
 * @description
 * @date 2025-11-14 11:48
 **/
@Data
public class LoginUserModel {
    private Long userId;
    private String userName;
    private Long departId;
    private String sex;
}
