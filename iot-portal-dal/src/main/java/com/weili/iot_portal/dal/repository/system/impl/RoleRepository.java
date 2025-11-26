package com.weili.iot_portal.dal.repository.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.framework.mybatis.query.LambdaQueryWrapperX;
import com.weili.iot_portal.dal.dataobject.system.RoleDO;
import com.weili.iot_portal.dal.ddd.system.RolePageQuery;
import com.weili.iot_portal.dal.repository.system.IRoleRepository;
import com.weili.iot_portal.dal.mapper.system.RoleMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author luying
 * @className RoleRepository
 * @description
 * @date 2025-07-16 09:46
 **/
@Repository
public class RoleRepository extends ServiceImpl<RoleMapper, RoleDO> implements IRoleRepository {

    @Override
    public void create(RoleDO roleDO) {
        super.save(roleDO);
    }

    @Override
    public void update(RoleDO roleDO) {
        super.updateById(roleDO);
    }

    @Override
    public void delete(Long id) {
        super.removeById(id);
    }

    @Override
    public PageResult<RoleDO> selectPage(RolePageQuery query) {
        LambdaQueryWrapper<RoleDO> queryWrapper = new LambdaQueryWrapperX<RoleDO>()
                .likeIfPresent(RoleDO::getName, query.getName())
                .likeIfPresent(RoleDO::getCode, query.getCode())
                .eqIfPresent(RoleDO::getStatus, query.getStatus())
                .betweenIfPresent(RoleDO::getCreateTime, query.getCreateTime())
                .orderByAsc(RoleDO::getSort);
        Page<RoleDO> page = super.page(new Page<>(query.getPageNo(), query.getPageSize()), queryWrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    public RoleDO selectById(Long id) {
        return super.getById(id);
    }

    @Override
    public RoleDO selectByName(String name) {
        return super.getOne(new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getName, name));
    }

    @Override
    public RoleDO selectByCode(String code) {
        return super.getOne(new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getCode, code));
    }

    @Override
    public List<RoleDO> selectEnableList() {
        return super.list(new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getStatus, 0));
    }

    @Override
    public List<RoleDO> selectByIds(List<Long> ids) {
        return super.listByIds(ids);
    }
}
