package com.weili.iot_portal.dal.repository.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weili.basic.framework.mybatis.query.LambdaQueryWrapperX;
import com.weili.iot_portal.dal.dataobject.system.MenuDO;
import com.weili.iot_portal.dal.ddd.system.MenuListQuery;
import com.weili.iot_portal.dal.mapper.system.MenuMapper;
import com.weili.iot_portal.dal.repository.system.IMenuRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author luying
 * @className MenuRepository
 * @description
 * @date 2025-07-15 17:49
 **/
@Repository
public class MenuRepository extends ServiceImpl<MenuMapper, MenuDO> implements IMenuRepository {

    @Override
    public void create(MenuDO menuDO) {
        super.save(menuDO);
    }

    @Override
    public void update(MenuDO menuDO) {
        super.updateById(menuDO);
    }

    @Override
    public void delete(Long id) {
        super.removeById(id);
    }

    @Override
    public MenuDO getByParentIdAndName(Long parentId, String name) {
        return super.getOne(new LambdaQueryWrapper<MenuDO>()
                .eq(MenuDO::getParentId, parentId)
                .eq(MenuDO::getName, name));
    }

    @Override
    public Long getCountByParentId(Long parentId) {
        return super.count(new LambdaQueryWrapper<MenuDO>().eq(MenuDO::getParentId, parentId));
    }

    @Override
    public List<MenuDO> listByQuery(MenuListQuery request) {
        LambdaQueryWrapperX<MenuDO> wrapperX = new LambdaQueryWrapperX<MenuDO>()
                .inIfPresent(MenuDO::getId, request.getMenuIds())
                .likeIfPresent(MenuDO::getName, request.getName())
                .eqIfPresent(MenuDO::getStatus, request.getStatus());
        return list(wrapperX);
    }

    @Override
    public List<MenuDO> listByPermission(String permission) {
        return super.list(new LambdaQueryWrapper<MenuDO>().eq(MenuDO::getPermission, permission));
    }

    @Override
    public MenuDO getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<MenuDO> listAll() {
        return super.list();
    }
}

