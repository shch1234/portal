package com.weili.iot_portal.web.security.handle;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.web.security.context.SecurityFrameworkContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;


/**
 * 访问一个需要认证的 URL 资源，已经认证（登录）但是没有权限的情况下，返回  错误码。
 */
@Slf4j
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) {
        log.warn("[commence][访问 URL({}) 时，用户({}) 权限不够]", request.getRequestURI(),
                SecurityFrameworkContext.getLoginUserId(), e);
        // 返回 403
        ServletHelper.writeJSON(response,
                CommonResult.error(ErrorCodeConstants.FORBIDDEN.getCode(),
                        ErrorCodeConstants.FORBIDDEN.getMsg()));
    }

}
