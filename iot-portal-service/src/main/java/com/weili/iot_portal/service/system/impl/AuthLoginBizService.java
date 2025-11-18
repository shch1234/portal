package com.weili.iot_portal.service.system.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.util.BeanUtils;
import com.weili.basic.common.util.DateUtils;
import com.weili.iot_portal.common.constant.ApolloConstant;
import com.weili.iot_portal.common.utils.TokeUtil;
import com.weili.iot_portal.domain.model.AccessTokenModel;
import com.weili.iot_portal.domain.model.LoginUserModel;
import com.weili.iot_portal.domain.permission.AuthLoginReqVO;
import com.weili.iot_portal.domain.permission.AuthLoginRespVO;
import com.weili.iot_portal.domain.permission.AuthUserInfoRespVO;
import com.weili.iot_portal.service.cache.AccessTokenCache;
import com.weili.iot_portal.service.system.IAuthLoginBizService;
import com.weili.iot_portal.service.system.ISpmRemoteService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * @author luying
 * @className AuthLoginBizService
 * @description
 * @date 2025-11-14 13:30
 **/
@Service
@Slf4j
public class AuthLoginBizService implements IAuthLoginBizService {

    @Resource
    private ISpmRemoteService spmRemoteService;
    @Resource
    private AccessTokenCache accessTokenCache;


    @Override
    public AuthLoginRespVO login(AuthLoginReqVO reqVO) {
        //请求4A获取登录
        String spmToken = spmRemoteService.loginUser(reqVO.getJobNumber(), reqVO.getPassword());
        if (StringUtils.isEmpty(spmToken)) {
            throw new ServiceException(ErrorCodeConstants.AUTH_LOGIN_BAD_CREDENTIALS);
        }
        AccessTokenModel tokenModel = new AccessTokenModel()
                .setUserId(reqVO.getJobNumber().toString())
                .setJobNumber(reqVO.getJobNumber())
                .setAccessToken(TokeUtil.generateAccessToken())
                .setRefreshToken(TokeUtil.generateRefreshToken())
                .setSpmToken(spmToken)
                .setExpiresTime(LocalDateTime.now().plusSeconds(ApolloConstant.accessTokenSeconds()));
        accessTokenCache.set(tokenModel);
        return BeanUtils.toBean(tokenModel, AuthLoginRespVO.class);
    }

    @Override
    public AccessTokenModel checkAccessToken(String accessToken) {
        AccessTokenModel accessTokenDO = accessTokenCache.get(accessToken);
        if (accessTokenDO == null) {
            throw new ServiceException(ErrorCodeConstants.UNAUTHORIZED.getCode(), "访问令牌不存在");
        }
        if (DateUtils.isExpired(accessTokenDO.getExpiresTime())) {
            throw new ServiceException(ErrorCodeConstants.UNAUTHORIZED.getCode(), "访问令牌已过期");
        }
        return accessTokenDO;
    }

    @Override
    public LoginUserModel getUser(String userId) {
        //请求4A获取员工信息
        return null;
    }

    @Override
    public AuthUserInfoRespVO getUserByAccessToken(String accessToken) {
        return null;
    }

    @Override
    public void logout(String token) {
        accessTokenCache.delete(token);
    }
}
