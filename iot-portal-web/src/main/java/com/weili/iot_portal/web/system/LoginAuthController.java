package com.weili.iot_portal.web.system;

import com.weili.basic.authorization.security.SecurityContextUtils;
import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.basic.oauth2.oidc.Oauth2UserDetail;
import com.weili.iot_portal.dal.dataobject.system.MenuDO;
import com.weili.iot_portal.dal.dataobject.system.RoleDO;
import com.weili.iot_portal.domain.permission.*;
import com.weili.iot_portal.service.system.ILoginUserBizService;
import com.weili.iot_portal.service.system.IMenuBizService;
import com.weili.iot_portal.service.system.IUserRoleBizService;
import com.weili.iot_portal.web.annotation.PermRequired;
import com.weili.iot_portal.web.converter.AuthLoginConvert;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.weili.basic.common.model.CommonResult.success;


/**
 * @author luying
 * @description: TODO
 * @date 2025/6/16 22:51
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/system/user")
@Slf4j
public class LoginAuthController {

    @Resource
    private ILoginUserBizService loginUserBizService;
    @Resource
    private IMenuBizService menuBizService;
    @Resource
    private IUserRoleBizService userRoleBizService;


    @GetMapping("/get-info")
    @Operation(summary = "获取登录用户的信息")
    @ApiInterceptor
    public CommonResult<Map<String, AuthUserInfoRespVO>> getUserInfo() {
        Oauth2UserDetail loginUser = SecurityContextUtils.getLoginUser();
        Map<String, AuthUserInfoRespVO> result = new HashMap<>();
        result.put("user", BeanUtils.toBean(loginUser, AuthUserInfoRespVO.class));
        loginUserBizService.createUser();
        return CommonResult.success(result);
    }


    @GetMapping("/get-permission")
    @Operation(summary = "获取用户角色权限信息")
    @ApiInterceptor
    public CommonResult<AuthPermissionRespVO> getPermission() {
        Oauth2UserDetail loginUser = SecurityContextUtils.getLoginUser();
        Long userId = loginUser.getUserId();
        List<RoleDO> roleList = userRoleBizService.getRoleByUserId(userId);
        if (CollectionUtils.isEmpty(roleList)) {
            log.warn("未查询到用户角色信息");
            return CommonResult.success(AuthLoginConvert.INSTANCE.convertNoUser(Collections.emptyList(), Collections.emptyList()));
        }
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

    @GetMapping("/page")
    @Operation(summary = "获得用户分页列表")
    @ApiInterceptor
    public CommonResult<PageResult<LoginUserRespVO>> getUserPage(@Valid LoginUserPageReqVO pageReqVO) {
        PageResult<LoginUserRespVO> pageResult = loginUserBizService.selectPage(pageReqVO);
        return CommonResult.success(pageResult);
    }

    @GetMapping("/get")
    @Operation(summary = "获得用户详情")
    @Parameter(name = "id", description = "用户id", required = true)
    @ApiInterceptor
    public CommonResult<LoginUserRespVO> getUser(@RequestParam("id") Long id) {
        return CommonResult.success(loginUserBizService.get(id));
    }

    @PutMapping("/update")
    @Operation(summary = "更新用户关联角色")
    @ApiInterceptor
    @PermRequired(permission = "system:user:update")
    public CommonResult<Boolean> update(@Valid @RequestBody LoginUserSaveReqVO reqVO) {
        loginUserBizService.update(reqVO);
        return CommonResult.success(true);
    }
}
