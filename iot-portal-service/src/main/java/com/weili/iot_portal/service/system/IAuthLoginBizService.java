package com.weili.iot_portal.service.system;

import com.weili.iot_portal.domain.model.AccessTokenModel;
import com.weili.iot_portal.domain.model.LoginUserModel;
import com.weili.iot_portal.domain.permission.AuthLoginReqVO;
import com.weili.iot_portal.domain.permission.AuthLoginRespVO;
import com.weili.iot_portal.domain.permission.AuthUserInfoRespVO;

/**
 * @author luying
 * @className IAuthLoginBizService
 * @description
 * @date 2025-11-14 13:29
 **/
public interface IAuthLoginBizService {

    AuthLoginRespVO login(AuthLoginReqVO reqVO);

    AccessTokenModel checkAccessToken(String accessToken);

    LoginUserModel getUser(String userId);

    AuthUserInfoRespVO getUserByAccessToken(String accessToken);
}
