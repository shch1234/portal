package com.weili.iot_portal.web.security.handle;

import cn.hutool.core.util.StrUtil;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.domain.model.AccessTokenModel;
import com.weili.iot_portal.domain.model.LoginUserModel;
import com.weili.iot_portal.service.system.IAuthLoginBizService;
import com.weili.iot_portal.web.security.context.LonginUserContext;
import com.weili.iot_portal.web.security.context.SecurityFrameworkContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Token 过滤器，验证 token 的有效性
 */
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final GlobalExceptionHandler globalExceptionHandler;

    private final IAuthLoginBizService authLoginBizService;


    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain chain)
            throws ServletException, IOException {
        String token = SecurityFrameworkContext.obtainAuthorization(request);
        if (StrUtil.isNotEmpty(token)) {
            try {
                LonginUserContext loginUser = buildLoginUserByToken(token);
                if (loginUser != null) {
                    SecurityFrameworkContext.setLoginUser(loginUser, request);
                }
            } catch (Throwable ex) {
                CommonResult<?> result = globalExceptionHandler.allExceptionHandler(request, ex);
                ServletHelper.writeJSON(response, result);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private LonginUserContext buildLoginUserByToken(String token) {
        try {
            AccessTokenModel accessToken = authLoginBizService.checkAccessToken(token);
            if (accessToken == null) {
                return null;
            }
            LoginUserModel userModel = authLoginBizService.getUser(accessToken.getUserId());
            if (userModel == null) {
                return null;
            }
            LonginUserContext loginContext = new LonginUserContext();
            loginContext.setId(userModel.getUserId());
            loginContext.setExpiresTime(accessToken.getExpiresTime());
            loginContext.setAccessToken(accessToken.getAccessToken());
            Map<String, String> userInfo = new HashMap<>();
            userInfo.put(LonginUserContext.INFO_KEY_DEPT_ID, StrUtil.toStringOrNull(userModel.getDepartId()));
            userInfo.put(LonginUserContext.INFO_KEY_USERNAME, userModel.getUserName());
            loginContext.setInfo(userInfo);
            return loginContext;
        } catch (ServiceException serviceException) {
            return null;
        }
    }

}
