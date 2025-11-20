package com.weili.iot_portal.service.system.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import com.google.common.collect.Lists;
import com.weili.basic.common.util.CollectionUtils;
import com.weili.iot_portal.common.enums.RoleCodeEnum;
import com.weili.iot_portal.common.enums.StatusEnum;
import com.weili.iot_portal.dal.dataobject.system.RoleDO;
import com.weili.iot_portal.dal.repository.system.IRoleRepository;
import com.weili.iot_portal.service.system.IMenuBizService;
import com.weili.iot_portal.service.system.IPermissionBizService;
import com.weili.iot_portal.service.system.IUserRoleBizService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author luying
 * @className PermissionService
 * @description
 * @date 2025-11-17 09:38
 **/
@Service
@Slf4j
public class PermissionBizService implements IPermissionBizService {
    @Resource
    private IRoleRepository roleRepository;
    @Resource
    private IMenuBizService menuBizService;
    @Resource
    private IUserRoleBizService userRoleBizService;


    @Override
    public boolean hasAnyPermissions(Long userId, String... permissions) {
        // 如果为空，说明已经有权限
        if (ArrayUtil.isEmpty(permissions)) {
            return true;
        }

        // 获得当前登录的角色。如果为空，说明没有权限
        List<RoleDO> roles = getEnableUserRoleListByUserId(userId);
        if (CollUtil.isEmpty(roles)) {
            return false;
        }

        // 情况一：遍历判断每个权限，如果有一满足，说明有权限
        for (String permission : permissions) {
            if (hasAnyPermission(roles, permission)) {
                return true;
            }
        }

        // 情况二：如果是超管，也说明有权限
        return hasAnySuperAdmin(CollectionUtils.convertList(roles, RoleDO::getId));
    }


    private List<RoleDO> getEnableUserRoleListByUserId(Long userId) {
        // 获得用户拥有的角色编号
        List<RoleDO> roles = userRoleBizService.getRoleByUserId(userId);
        roles.removeIf(role -> !StatusEnum.ENABLE.getStatus().equals(role.getStatus()));
        return roles;
    }

    /**
     * 判断指定角色，是否拥有该 permission 权限
     *
     * @param roles      指定角色数组
     * @param permission 权限标识
     * @return 是否拥有
     */
    private boolean hasAnyPermission(List<RoleDO> roles, String permission) {
        List<Long> menuIds = menuBizService.getMenuIdListByPermission(permission);
        // 采用严格模式，如果权限找不到对应的 Menu 的话，也认为没有权限
        if (CollUtil.isEmpty(menuIds)) {
            return false;
        }

        // 判断是否有权限
        Set<Long> roleIds = CollectionUtils.convertSet(roles, RoleDO::getId);
        for (Long menuId : menuIds) {
            // 获得拥有该菜单的角色编号集合
            List<Long> menuRoleIds = menuBizService.selectListByMenuId(menuId);
            // 如果有交集，说明有权限
            if (CollUtil.containsAny(menuRoleIds, roleIds)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnySuperAdmin(List<Long> roleIds) {
        if (org.apache.commons.collections4.CollectionUtils.isEmpty(roleIds)) {
            return false;
        }
        List<RoleDO> roleDOList = roleRepository.selectByIds(roleIds);
        return Optional.ofNullable(roleDOList).orElse(Lists.newArrayList())
                .stream().anyMatch(role -> RoleCodeEnum.isSuperAdmin(role.getCode()));
    }
}

