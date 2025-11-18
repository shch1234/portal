package com.weili.iot_portal.dal.repository.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weili.basic.framework.mybatis.query.LambdaQueryWrapperX;
import com.weili.iot_portal.dal.dataobject.permission.RoleMenuDO;
import com.weili.iot_portal.dal.mapper.system.RoleMenuMapper;
import com.weili.iot_portal.dal.repository.system.IRoleMenuRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author luying
 * @className RoleMenuRepository
 * @description
 * @date 2025-07-16 10:34
 **/
@Repository
public class RoleMenuRepository extends ServiceImpl<RoleMenuMapper, RoleMenuDO> implements IRoleMenuRepository {

    @Override
    public void batchCreate(List<RoleMenuDO> list) {
        super.saveBatch(list);
    }

    @Override
    public List<RoleMenuDO> selectListByRoleId(Long roleId) {
        return super.list(new LambdaQueryWrapperX<RoleMenuDO>()
                .eq(RoleMenuDO::getRoleId, roleId)
                .eq(RoleMenuDO::getDeleted, false));
    }

    @Override
    public List<RoleMenuDO> selectListByRoleId(List<Long> roleIds) {
        return super.list(new LambdaQueryWrapperX<RoleMenuDO>().inIfPresent(RoleMenuDO::getRoleId, roleIds));
    }

    @Override
    public List<RoleMenuDO> selectListByMenuId(Long menuId) {
        return super.list(new LambdaQueryWrapperX<RoleMenuDO>().eqIfPresent(RoleMenuDO::getMenuId, menuId));
    }

    @Override
    public void deleteListByRoleIdAndMenuIds(Long roleId, List<Long> menuIds) {
        LambdaQueryWrapper<RoleMenuDO> queryWrapper = new LambdaQueryWrapper<RoleMenuDO>()
                .eq(RoleMenuDO::getRoleId, roleId)
                .in(RoleMenuDO::getMenuId, menuIds);
        super.remove(queryWrapper);
    }

    @Override
    public void deleteListByMenuId(Long menuId) {
        super.remove(new LambdaQueryWrapper<RoleMenuDO>().eq(RoleMenuDO::getMenuId, menuId));
    }

    @Override
    public void deleteListByRoleId(Long roleId) {
        super.remove(new LambdaQueryWrapper<RoleMenuDO>().eq(RoleMenuDO::getRoleId, roleId));
    }
}
