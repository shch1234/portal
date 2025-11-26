package com.weili.iot_portal.service.system.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import com.google.common.collect.Lists;
import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.common.enums.MenuTypeEnum;
import com.weili.iot_portal.common.enums.RoleCodeEnum;
import com.weili.iot_portal.common.enums.StatusEnum;
import com.weili.iot_portal.dal.dataobject.system.MenuDO;
import com.weili.iot_portal.dal.dataobject.system.RoleDO;
import com.weili.iot_portal.dal.dataobject.system.RoleMenuDO;
import com.weili.iot_portal.dal.ddd.system.MenuListQuery;
import com.weili.iot_portal.dal.repository.system.IMenuRepository;
import com.weili.iot_portal.dal.repository.system.IRoleMenuRepository;
import com.weili.iot_portal.dal.repository.system.IRoleRepository;
import com.weili.iot_portal.domain.permission.MenuListReqVO;
import com.weili.iot_portal.domain.permission.MenuSaveVO;
import com.weili.iot_portal.service.system.IMenuBizService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.weili.iot_portal.dal.dataobject.system.MenuDO.ID_ROOT;


/**
 * @ClassName: MenuBizService
 * @Description:
 * @Author: luying
 * @Date: 2025-07-04 22:19
 **/
@Service
@Slf4j
public class MenuBizService implements IMenuBizService {

    @Resource
    private IMenuRepository menuRepository;
    @Resource
    private IRoleRepository roleRepository;
    @Resource
    private IRoleMenuRepository roleMenuRepository;


    @Override
    public List<MenuDO> getMenuListByRoleId(List<Long> roleIds) {
        List<RoleMenuDO> roleMenuList = roleMenuRepository.selectListByRoleId(roleIds);
        if (CollectionUtils.isEmpty(roleMenuList)) {
            return Collections.emptyList();
        }
        // 如果是管理员的情况下，获取全部菜单编号
        if (hasAnySuperAdmin(roleIds)) {
            return menuRepository.listAll();
        }
        // 如果是非管理员的情况下，获得拥有的菜单编号
        List<Long> menuIds = roleMenuList.stream().map(RoleMenuDO::getMenuId).toList();
        return menuRepository.listByQuery(MenuListQuery.builder().menuIds(menuIds).build());
    }


    @Override
    public List<MenuDO> filterDisableMenus(List<MenuDO> menuList) {
        if (CollUtil.isEmpty(menuList)) {
            return Collections.emptyList();
        }
        Map<Long, MenuDO> menuMap = menuList.stream().collect(Collectors.toMap(MenuDO::getId, Function.identity()));
        // 遍历 menu 菜单，查找不是禁用的菜单，添加到 enabledMenus 结果
        List<MenuDO> enabledMenus = new ArrayList<>();
        Set<Long> disabledMenuCache = new HashSet<>(); // 存下递归搜索过被禁用的菜单，防止重复的搜索
        for (MenuDO menu : menuList) {
            if (isMenuDisabled(menu, menuMap, disabledMenuCache)) {
                continue;
            }
            enabledMenus.add(menu);
        }
        return enabledMenus;
    }

    @Override
    public List<MenuDO> getMenuList(MenuListReqVO reqVO) {
        return menuRepository.listByQuery(BeanUtils.toBean(reqVO, MenuListQuery.class));
    }

    @Override
    public List<MenuDO> getSimpleMenuList(MenuListReqVO reqVO) {
        return getMenuList(reqVO);
    }

    @Override
    public MenuDO getMenu(Long id) {
        return menuRepository.getById(id);
    }

    @Override
    public void deleteListByRoleId(Long roleId) {
        roleMenuRepository.deleteListByRoleId(roleId);
    }

    @Override
    @Transactional
    public Long create(MenuSaveVO createReqVO) {
        // 校验父菜单存在
        validateParentMenu(createReqVO.getParentId(), null);
        // 校验菜单（自己）
        validateMenu(createReqVO.getParentId(), createReqVO.getName(), null);

        // 插入数据库
        MenuDO menu = BeanUtils.toBean(createReqVO, MenuDO.class);
        initMenuProperty(menu);
        menuRepository.create(menu);
        // 返回
        return menu.getId();
    }

    @Override
    @Transactional
    public void update(MenuSaveVO updateReqVO) {
        // 校验更新的菜单是否存在
        if (menuRepository.getById(updateReqVO.getId()) == null) {
            throw new ServiceException(ErrorCodeConstants.MENU_NOT_EXISTS);
        }
        // 校验父菜单存在
        validateParentMenu(updateReqVO.getParentId(), updateReqVO.getId());
        // 校验菜单（自己）
        validateMenu(updateReqVO.getParentId(), updateReqVO.getName(), updateReqVO.getId());

        // 更新到数据库
        MenuDO updateObj = BeanUtils.toBean(updateReqVO, MenuDO.class);
        initMenuProperty(updateObj);
        menuRepository.update(updateObj);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // 校验是否还有子菜单
        if (menuRepository.getCountByParentId(id) > 0) {
            throw new ServiceException(ErrorCodeConstants.MENU_EXISTS_CHILDREN);
        }
        // 校验删除的菜单是否存在
        if (menuRepository.getById(id) == null) {
            throw new ServiceException(ErrorCodeConstants.MENU_NOT_EXISTS);
        }
        // 标记删除
        menuRepository.delete(id);
        // 删除授予给角色的权限
        roleMenuRepository.deleteListByMenuId(id);
    }

    @Override
    public List<Long> getMenuIdListByPermission(String permission) {
        List<MenuDO> menus = menuRepository.listByPermission(permission);
        return com.weili.basic.common.util.CollectionUtils.convertList(menus, MenuDO::getId);
    }

    @Override
    @Transactional
    public void assignRoleMenu(Long roleId, List<Long> menuIds) {
        List<RoleMenuDO> list = roleMenuRepository.selectListByRoleId(roleId);
        List<Long> dbMenuIds = Optional.ofNullable(list).orElse(Lists.newArrayList())
                .stream().map(RoleMenuDO::getMenuId).distinct().toList();
        List<Long> menuIdList = Optional.ofNullable(menuIds).orElse(new ArrayList<>());
        List<Long> deleteMenuIds = dbMenuIds.stream().filter(menuId -> !menuIdList.contains(menuId)).toList();
        List<Long> createMenuIds = menuIdList.stream().filter(menuId -> !dbMenuIds.contains(menuId)).toList();

        if (!deleteMenuIds.isEmpty()) {
            roleMenuRepository.deleteListByRoleIdAndMenuIds(roleId, deleteMenuIds);
        }
        if (!createMenuIds.isEmpty()) {
            List<RoleMenuDO> addList = new ArrayList<>();
            createMenuIds.forEach(menuId -> {
                RoleMenuDO entity = new RoleMenuDO();
                entity.setRoleId(roleId);
                entity.setMenuId(menuId);
                addList.add(entity);
            });
            roleMenuRepository.batchCreate(addList);
        }
    }

    @Override
    public List<Long> selectListByMenuId(Long menuId) {
        return com.weili.basic.common.util.CollectionUtils.convertList(roleMenuRepository.selectListByMenuId(menuId), RoleMenuDO::getRoleId);
    }


    private boolean isMenuDisabled(MenuDO node, Map<Long, MenuDO> menuMap, Set<Long> disabledMenuCache) {
        // 如果已经判定是禁用的节点，直接结束
        if (disabledMenuCache.contains(node.getId())) {
            return true;
        }

        // 1. 先判断自身是否禁用
        if (StatusEnum.isDisable(node.getStatus())) {
            disabledMenuCache.add(node.getId());
            return true;
        }

        // 2. 遍历到 parentId 为根节点，则无需判断
        Long parentId = node.getParentId();
        if (ObjUtil.equal(parentId, ID_ROOT)) {
            return false;
        }

        // 3. 继续遍历 parent 节点
        MenuDO parent = menuMap.get(parentId);
        if (parent == null || isMenuDisabled(parent, menuMap, disabledMenuCache)) {
            disabledMenuCache.add(node.getId());
            return true;
        }
        return false;
    }


    private void validateParentMenu(Long parentId, Long childId) {
        if (parentId == null || ID_ROOT.equals(parentId)) {
            return;
        }
        // 不能设置自己为父菜单
        if (parentId.equals(childId)) {
            throw new ServiceException(ErrorCodeConstants.MENU_PARENT_ERROR);
        }
        MenuDO menu = menuRepository.getById(parentId);
        // 父菜单不存在
        if (menu == null) {
            throw new ServiceException(ErrorCodeConstants.MENU_PARENT_NOT_EXISTS);
        }
        // 父菜单必须是目录或者菜单类型
        if (!MenuTypeEnum.DIR.getType().equals(menu.getType())
                && !MenuTypeEnum.MENU.getType().equals(menu.getType())) {
            throw new ServiceException(ErrorCodeConstants.MENU_PARENT_NOT_DIR_OR_MENU);
        }
    }

    /**
     * 校验菜单是否合法
     * <p>
     * 1. 校验相同父菜单编号下，是否存在相同的菜单名
     *
     * @param name     菜单名字
     * @param parentId 父菜单编号
     * @param id       菜单编号
     */
    private void validateMenu(Long parentId, String name, Long id) {
        MenuDO menu = menuRepository.getByParentIdAndName(parentId, name);
        if (menu == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的菜单
        if (id == null) {
            throw new ServiceException(ErrorCodeConstants.MENU_NAME_DUPLICATE);
        }
        if (!menu.getId().equals(id)) {
            throw new ServiceException(ErrorCodeConstants.MENU_NAME_DUPLICATE);
        }
    }


    /**
     * 初始化菜单的通用属性。
     * <p>
     * 例如说，只有目录或者菜单类型的菜单，才设置 icon
     *
     * @param menu 菜单
     */
    private void initMenuProperty(MenuDO menu) {
        // 菜单为按钮类型时，无需 component、icon、path 属性，进行置空
        if (MenuTypeEnum.BUTTON.getType().equals(menu.getType())) {
            menu.setComponent("");
            menu.setComponentName("");
            menu.setIcon("");
            menu.setPath("");
        }
    }

    private boolean hasAnySuperAdmin(List<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return false;
        }
        List<RoleDO> roleDOList = roleRepository.selectByIds(roleIds);
        return Optional.ofNullable(roleDOList).orElse(Lists.newArrayList())
                .stream().anyMatch(role -> RoleCodeEnum.isSuperAdmin(role.getCode()));
    }
}
