package com.weili.iot_portal.web.controller.system;

import cn.hutool.core.util.StrUtil;
import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.CommonResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.dal.dataobject.permission.MenuDO;
import com.weili.iot_portal.dal.dataobject.permission.RoleDO;
import com.weili.iot_portal.domain.model.LoginUserModel;
import com.weili.iot_portal.domain.permission.*;
import com.weili.iot_portal.service.system.IAuthLoginBizService;
import com.weili.iot_portal.service.system.IMenuBizService;
import com.weili.iot_portal.service.system.IUserRoleBizService;
import com.weili.iot_portal.web.converter.AuthLoginConvert;
import com.weili.iot_portal.web.security.context.SecurityFrameworkContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.weili.basic.common.model.CommonResult.success;

@Tag(name = "认证")
@RestController
@RequestMapping("/system/auth")
@Validated
@Slf4j
public class AuthController {

    @Resource
    private IMenuBizService menuBizService;
    @Resource
    private IUserRoleBizService userRoleBizService;
    @Resource
    private IAuthLoginBizService authLoginBizService;

    @PostMapping("/login")
    @PermitAll
    @Operation(summary = "登录")
    @ApiInterceptor
    public CommonResult<AuthLoginRespVO> login(@RequestBody @Valid AuthLoginReqVO reqVO) {
        AuthLoginRespVO login = authLoginBizService.login(reqVO);
        return CommonResult.success(login);
    }


    @PostMapping("/logout")
    @PermitAll
    @Operation(summary = "退出")
    @ApiInterceptor
    public CommonResult<Boolean> logout(HttpServletRequest request) {
        String token = SecurityFrameworkContext.obtainAuthorization(request);
        if (StrUtil.isNotBlank(token)) {
            authLoginBizService.logout(token);
        }
        return success(true);
    }


    @GetMapping("/get-info")
    @Operation(summary = "获取登录用户的信息")
    @ApiInterceptor
    public CommonResult<Map<String, AuthUserInfoRespVO>> getUserInfo(HttpServletRequest request) {
        String accessToken = SecurityFrameworkContext.obtainAuthorization(request);
        AuthUserInfoRespVO infoVO = authLoginBizService.getUserByAccessToken(accessToken);
        Map<String, AuthUserInfoRespVO> result = new HashMap<>();
        result.put("user", infoVO);
        return CommonResult.success(result);
    }


    @GetMapping("/get-permission")
    @Operation(summary = "获取用户角色权限信息")
    @ApiInterceptor
    public CommonResult<AuthPermissionRespVO> getPermission(AuthPermissionReqVO reqVO) {
        if (reqVO == null || StringUtils.isBlank(reqVO.getUserId())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "用户id为空");
        }
        LoginUserModel userModel = authLoginBizService.getUser(reqVO.getUserId());
        if (userModel == null) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "用户数据不存在，请联系管理员");
        }
        // 1.2 获得角色列表
        List<RoleDO> roleList = userRoleBizService.getRoleByUserId(userModel.getUserId());
        if (CollectionUtils.isEmpty(roleList)) {
            log.warn("未查询到用户角色信息");
            return CommonResult.success(AuthLoginConvert.INSTANCE.convertNoUser(Collections.emptyList(), Collections.emptyList()));
        }
        // 1.3 获取菜单列表
        List<Long> roleIds = roleList.stream().map(RoleDO::getId).collect(Collectors.toList());
        List<MenuDO> menuList = menuBizService.getMenuListByRoleId(roleIds);
        if (CollectionUtils.isEmpty(menuList)) {
            log.warn("未查询到用户角色菜单信息");
        }
        List<MenuDO> filterList = menuBizService.filterDisableMenus(menuList);
        if (CollectionUtils.isEmpty(filterList)) {
            log.warn("未查询到用户角色有效菜单信息");
        }
        return success(AuthLoginConvert.INSTANCE.convertNoUser(roleList, filterList));
    }
}
