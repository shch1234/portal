package com.weili.iot_portal.web.controller.system;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.util.CollectionUtils;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.basic.framework.annotation.LimitingInterceptor;
import com.weili.iot_portal.dal.dataobject.system.MenuDO;
import com.weili.iot_portal.dal.dataobject.system.RoleDO;
import com.weili.iot_portal.domain.permission.PermissionAssignRoleMenuReqVO;
import com.weili.iot_portal.domain.permission.PermissionAssignUserRoleReqVO;
import com.weili.iot_portal.domain.permission.PermissionUserRoleReqVO;
import com.weili.iot_portal.service.system.IMenuBizService;
import com.weili.iot_portal.service.system.IUserRoleBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "权限管理")
@RestController
@RequestMapping("/system/permission")
public class PermissionController {

    @Resource
    private IMenuBizService menuBizService;
    @Resource
    private IUserRoleBizService userRoleBizService;
    

    @GetMapping("/list-role-menus")
    @Operation(summary = "获得角色拥有的菜单编号")
    public CommonResult<List<Long>> getRoleMenuList(PermissionAssignRoleMenuReqVO reqVO) {
        List<Long> roleIds = new ArrayList<>();
        if (reqVO.getRoleId() != null) {
            roleIds.add(reqVO.getRoleId());
        }
        List<MenuDO> menuList = menuBizService.getMenuListByRoleId(roleIds);
        return CommonResult.success(CollectionUtils.convertList(menuList, MenuDO::getId));
    }


    @PostMapping("/assign-role-menu")
    @Operation(summary = "赋予角色菜单")
    public CommonResult<Boolean> assignRoleMenu(@Valid @RequestBody PermissionAssignRoleMenuReqVO reqVO) {
        menuBizService.assignRoleMenu(reqVO.getRoleId(), reqVO.getMenuIds());
        return CommonResult.success(true);
    }


    @GetMapping("/list-user-roles")
    @Operation(summary = "获得管理员拥有的角色编号列表")
    @ApiInterceptor
    public CommonResult<List<Long>> listAdminRoles(@Valid @RequestParam("userId") Long userId) {
        List<RoleDO> roleList = userRoleBizService.getRoleByUserId(userId);
        return CommonResult.success(CollectionUtils.convertList(roleList, RoleDO::getId));
    }

    @PostMapping("/assign-user-role")
    @Operation(summary = "赋予用户角色")
    @ApiInterceptor
    public CommonResult<Boolean> assignUserRole(@Valid @RequestBody PermissionAssignUserRoleReqVO reqVO) {
        userRoleBizService.assignUserRole(reqVO.getUserId(), reqVO.getRoleIds());
        return CommonResult.success(true);
    }

    @PostMapping("/insert-user-role")
    @Operation(summary = "批量新增用户角色")
    @ApiInterceptor
    public CommonResult<Boolean> insertUserRole(@Valid @RequestBody PermissionUserRoleReqVO reqVO) {
        userRoleBizService.insertUserRole(reqVO.getUserIds(), reqVO.getRoleIds());
        return CommonResult.success(true);
    }

    @PostMapping("/update-user-role")
    @Operation(summary = "批量更新用户角色")
    @ApiInterceptor
    public CommonResult<Boolean> updateUserRole(@Valid @RequestBody PermissionUserRoleReqVO reqVO) {
        userRoleBizService.updateUserRole(reqVO.getUserIds(), reqVO.getRoleIds());
        return CommonResult.success(true);
    }

    @PostMapping("/delete-user-role")
    @Operation(summary = "批量删除用户角色")
    @ApiInterceptor
    @LimitingInterceptor(bizKey = "delete-user-role")
    public CommonResult<Boolean> deleteUserRole(@Valid @RequestBody PermissionUserRoleReqVO reqVO) {
        userRoleBizService.deleteUserRole(reqVO.getUserIds(), reqVO.getRoleIds());
        return CommonResult.success(true);
    }
}
