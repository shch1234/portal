package com.weili.iot_portal.service.system;

import com.weili.iot_portal.domain.model.AccessTokenModel;
import com.weili.iot_portal.domain.model.LoginUserModel;
import com.weili.iot_portal.domain.permission.AuthLoginReqVO;
import com.weili.iot_portal.domain.permission.AuthLoginRespVO;
import com.weili.iot_portal.domain.permission.AuthUserInfoRespVO;

/**
 * 认证登录业务服务接口
 * 该接口定义了与用户认证和授权相关的核心业务逻辑方法，
 * 包括用户登录、访问令牌验证、获取用户信息以及登出等功能。
 *
 * @author luying
 * @date 2025-11-14 13:29
 */
public interface IAuthLoginBizService {

    /**
     * 用户登录方法
     * 根据传入的登录请求参数进行身份验证，并返回登录响应结果，
     * 包含访问令牌等相关信息。
     *
     * @param reqVO 登录请求参数对象，包含用户名、密码等信息
     * @return AuthLoginRespVO 登录响应结果对象，包含访问令牌等信息
     */
    AuthLoginRespVO login(AuthLoginReqVO reqVO);

    /**
     * 检查访问令牌的有效性
     * 验证传入的访问令牌是否有效，若有效则返回对应的访问令牌模型对象。
     *
     * @param accessToken 待检查的访问令牌字符串
     * @return AccessTokenModel 访问令牌模型对象，包含令牌相关信息
     */
    AccessTokenModel checkAccessToken(String accessToken);

    /**
     * 根据用户ID获取用户信息
     * 通过用户唯一标识符（userId）查询并返回用户的详细信息。
     *
     * @param userId 用户唯一标识符
     * @return LoginUserModel 用户信息模型对象
     */
    LoginUserModel getUser(String userId);

    /**
     * 根据访问令牌获取用户信息
     * 通过有效的访问令牌查询并返回对应用户的基本信息。
     *
     * @param accessToken 有效的访问令牌字符串
     * @return AuthUserInfoRespVO 用户基本信息响应对象
     */
    AuthUserInfoRespVO getUserByAccessToken(String accessToken);

    /**
     * 用户登出操作
     * 执行用户登出逻辑，使当前会话或指定令牌失效。
     *
     * @param token 当前用户的访问令牌
     */
    void logout(String token);
}
