package com.weili.iot_portal.dal.repository.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weili.basic.framework.mybatis.query.LambdaQueryWrapperX;
import com.weili.iot_portal.dal.dataobject.permission.UserRoleDO;
import com.weili.iot_portal.dal.mapper.system.UserRoleMapper;
import com.weili.iot_portal.dal.repository.system.IUserRoleRepository;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * @author luying
 * @className UserRoleRepository
 * @description
 * @date 2025-07-16 10:44
 **/
@Repository
public class UserRoleRepository extends ServiceImpl<UserRoleMapper, UserRoleDO> implements IUserRoleRepository {
    @Override
    public void batchCreate(List<UserRoleDO> list) {
        super.saveBatch(list);
    }

    @Override
    public List<UserRoleDO> selectListByUserId(Long userId) {
        return super.list(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
    }

    @Override
    public List<UserRoleDO> selectListByUserIds(List<Long> userIds) {
        return super.list(new LambdaQueryWrapperX<UserRoleDO>().in(UserRoleDO::getUserId, userIds));
    }

    @Override
    public void deleteListByUserIdAndRoleIdIds(Long userId, Collection<Long> roleIds) {
        super.remove(new LambdaQueryWrapperX<UserRoleDO>()
                .eq(UserRoleDO::getUserId, userId)
                .in(!CollectionUtils.isEmpty(roleIds), UserRoleDO::getRoleId, roleIds));
    }

    @Override
    public void deleteListByUserIdAndRoleId(Long userId, Long roleId) {
        super.remove(new LambdaQueryWrapperX<UserRoleDO>()
                .eq(UserRoleDO::getUserId, userId)
                .eq(UserRoleDO::getRoleId, roleId));
    }

    @Override
    public void deleteListByUserIds(List<Long> userIds) {
        super.remove(new LambdaQueryWrapperX<UserRoleDO>()
                .in(!CollectionUtils.isEmpty(userIds), UserRoleDO::getUserId, userIds));
    }

    @Override
    public void deleteListByUserId(Long userId) {
        super.remove(new LambdaQueryWrapperX<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
    }

    @Override
    public void deleteListByRoleId(Long roleId) {
        super.remove(new LambdaQueryWrapperX<UserRoleDO>().eq(UserRoleDO::getRoleId, roleId));
    }


    @Override
    public void deleteListByRoleIds(List<Long> roleIds) {
        super.remove(new LambdaQueryWrapperX<UserRoleDO>()
                .in(!CollectionUtils.isEmpty(roleIds), UserRoleDO::getRoleId, roleIds));
    }

    @Override
    public List<UserRoleDO> selectListByRoleIds(List<Long> roleIds) {
        LambdaQueryWrapper<UserRoleDO> wrapper = new LambdaQueryWrapperX<UserRoleDO>()
                .in(!CollectionUtils.isEmpty(roleIds), UserRoleDO::getRoleId, roleIds)
                .eq(UserRoleDO::getDeleted, 0);
        return super.list(wrapper);
    }
}
