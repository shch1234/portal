package com.weili.iot_portal.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * @author luying
 * @className AccessTokenModel
 * @description
 * @date 2025-11-14 11:48
 **/
@Data
@Accessors(chain = true)
public class AccessTokenModel {
    /**
     * 用户Id
     */
    private String userId;
    /**
     * 工号
     */
    private Integer jobNumber;
    /**
     * 访问令牌
     */
    private String accessToken;
    /**
     * 刷新令牌
     */
    private String refreshToken;
    /**
     * 过期时间
     */
    private LocalDateTime expiresTime;

    /**
     * 中台 SPM 令牌
     */
    private String spmToken;
}
