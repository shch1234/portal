package com.weili.iot_portal.service.system;

import com.weili.iot_portal.domain.model.AccessTokenModel;
import com.weili.iot_portal.domain.model.LoginUserModel;
import com.weili.iot_portal.domain.permission.AuthLoginReqVO;
import com.weili.iot_portal.domain.permission.AuthLoginRespVO;
import com.weili.iot_portal.domain.permission.AuthUserInfoRespVO;

/**
 * @author luying
 * @className IAuthLoginService
 * @description
 * @date 2025-11-14 13:29
 **/
public interface IAuthLoginBizService {
    /**
     * 账号登录
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AuthLoginRespVO login(AuthLoginReqVO reqVO);

    AccessTokenModel checkAccessToken(String accessToken);

    LoginUserModel getUser(String userId);

    void logout(String token);

    AuthUserInfoRespVO getUserByAccessToken(String accessToken);
}
