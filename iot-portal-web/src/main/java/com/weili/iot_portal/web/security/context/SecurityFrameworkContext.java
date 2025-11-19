package com.weili.iot_portal.web.security.context;

import cn.hutool.core.map.MapUtil;
import com.weili.basic.framework.context.RequestContext;
import com.weili.basic.framework.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;

/**
 * 安全服务工具类
 */
@Slf4j
@NoArgsConstructor
public class SecurityFrameworkContext {

    /**
     * HTTP 请求时，访问令牌的请求 Header
     */
    public final static String TOKEN_HEADER = "Authorization";

    /**
     * HEADER 认证头 value 的前缀
     */
    public static final String AUTHORIZATION_BEARER = "Bearer";

    /**
     * 从请求中，获得认证 Token
     *
     * @param request       请求
     * @return 认证 Token
     */
    public static String obtainAuthorization(HttpServletRequest request) {
        // 1. 获得 Token。优先级：Header > Parameter
        String token = request.getHeader(TOKEN_HEADER);
        if (!StringUtils.hasText(token)) {
            return null;
        }
        // 2. 去除 Token 中带的 Bearer
        int index = token.indexOf(AUTHORIZATION_BEARER + " ");
        return index >= 0 ? token.substring(index + 7).trim() : token;
    }

    /**
     * 获得当前认证信息
     *
     * @return 认证信息
     */
    public static Authentication getAuthentication() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context == null) {
            return null;
        }
        return context.getAuthentication();
    }

    /**
     * 获取当前用户
     *
     * @return 当前用户
     */
    @Nullable
    public static LonginUserContext getLoginUser() {
        Authentication authentication = getAuthentication();
        if (authentication == null) {
            return null;
        }
        return authentication.getPrincipal() instanceof LonginUserContext ? (LonginUserContext) authentication.getPrincipal() : null;
    }

    /**
     * 获得当前用户的编号，从上下文中
     *
     * @return 用户编号
     */
    @Nullable
    public static Long getLoginUserId() {
        LonginUserContext loginUser = getLoginUser();
        return loginUser != null ? loginUser.getId() : null;
    }

    @Nullable
    public static String getLoginUserName() {
        LonginUserContext loginUser = getLoginUser();
        return loginUser != null ? MapUtil.getStr(loginUser.getInfo(), LonginUserContext.INFO_KEY_USERNAME) : null;
    }

    /**
     * 获得当前用户的部门编号，从上下文中
     *
     * @return 部门编号
     */
    @Nullable
    public static Long getLoginDeptId() {
        LonginUserContext loginUser = getLoginUser();
        return loginUser != null ? MapUtil.getLong(loginUser.getInfo(), LonginUserContext.INFO_KEY_DEPT_ID) : null;
    }

    /**
     * 设置当前用户
     *
     * @param loginUser 登录用户
     * @param request   请求
     */
    public static void setLoginUser(LonginUserContext loginUser, HttpServletRequest request) {
        // 创建 Authentication，并设置到上下文
        Authentication authentication = buildAuthentication(loginUser, request);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static Authentication buildAuthentication(LonginUserContext loginUser, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                loginUser, null, Collections.emptyList());
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return authenticationToken;
    }


    @Component
    @Primary
    @Qualifier("securityContext")
    public static class SecurityContextImpl implements RequestContext {

        @Override
        public UserContext getUserInfo() {
            return new UserContext()
                    .setUserId(getLoginUserId())
                    .setUserName(getLoginUserName())
                    .setDepartId(getLoginDeptId());
        }
    }
}
