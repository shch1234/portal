package com.weili.iot_portal.web.security;

import com.weili.basic.authorization.security.SecurityContextUtils;
import com.weili.basic.common.exception.BaseException;
import com.weili.iot_portal.common.enums.BizErrorCodeEnum;
import com.weili.iot_portal.service.system.ILoginUserBizService;
import com.weili.iot_portal.service.system.IPermissionBizService;
import com.weili.iot_portal.web.annotation.PermRequired;
import com.weili.iot_portal.web.aspect.IPermRequiredVerifier;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Service;

/**
 * @author luying
 * @description: TODO
 * @date 2025/6/19 14:58
 */
@Service
@Slf4j
public class MenuRoleRequiredVerifier implements IPermRequiredVerifier {

    @Resource
    private ILoginUserBizService loginUserBizService;
    @Resource
    private IPermissionBizService permissionBizService;

    @Override
    public void verifyPerm(ProceedingJoinPoint pjp, Object[] args, PermRequired permRequired) {
        if (StringUtils.isEmpty(permRequired.permission()) && permRequired.permissions().length == 0) {
            throw new BaseException(BizErrorCodeEnum.PERMISSION_ERROR.getCode(), "用户没有当前菜单操作权限");
        }
        Long userId = SecurityContextUtils.getUserid();
        if (userId == null) {
            throw new BaseException(BizErrorCodeEnum.PERMISSION_ERROR.getCode(), "用户UserId是空");
        }
        String permission = permRequired.permission();
        boolean hasPermission;
        if (StringUtils.isNotBlank(permission)) {
            hasPermission = permissionBizService.hasAnyPermissions(userId, permission);
        } else {
            String[] permissions = permRequired.permissions();
            hasPermission = permissionBizService.hasAnyPermissions(userId, permissions);
        }
        if (!hasPermission) {
            log.warn("用户无权限。userId={} permission={}", userId, permission);
            throw new BaseException(BizErrorCodeEnum.PERMISSION_ERROR);
        }
    }
}
