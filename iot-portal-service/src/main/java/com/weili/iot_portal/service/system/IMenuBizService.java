package com.weili.iot_portal.service.system;

import com.weili.iot_portal.dal.dataobject.permission.MenuDO;
import com.weili.iot_portal.domain.permission.MenuListReqVO;
import com.weili.iot_portal.domain.permission.MenuSaveVO;

import java.util.List;

/**
 * @InterfaceName: IMenuBizService
 * @Description: 菜单服务
 * @Author: luying
 **/
public interface IMenuBizService {

    /**
     * 根据角色ID列表获取菜单列表
     *
     * @param roleIds 角色ID列表
     * @return 菜单列表
     */
    List<MenuDO> getMenuListByRoleId(List<Long> roleIds);

    /**
     * 过滤掉禁用的菜单
     *
     * @param list 原始菜单列表
     * @return 过滤后的可用菜单列表
     */
    List<MenuDO> filterDisableMenus(List<MenuDO> list);

    /**
     * 根据条件查询菜单列表
     *
     * @param reqVO 菜单查询条件
     * @return 菜单列表
     */
    List<MenuDO> getMenuList(MenuListReqVO reqVO);

    /**
     * 获取简单的菜单列表（可能不包含详细信息）
     *
     * @param reqVO 菜单查询条件
     * @return 简单菜单列表
     */
    List<MenuDO> getSimpleMenuList(MenuListReqVO reqVO);

    /**
     * 根据ID获取菜单详情
     *
     * @param id 菜单ID
     * @return 菜单信息
     */
    MenuDO getMenu(Long id);

    /**
     * 根据角色ID删除关联的菜单列表
     *
     * @param roleId 角色ID
     */
    void deleteListByRoleId(Long roleId);

    /**
     * 创建新菜单
     *
     * @param createReqVO 菜单创建参数
     * @return 新创建菜单的ID
     */
    Long create(MenuSaveVO createReqVO);

    /**
     * 更新菜单信息
     *
     * @param updateReqVO 菜单更新参数
     */
    void update(MenuSaveVO updateReqVO);

    /**
     * 删除指定菜单
     *
     * @param id 菜单ID
     */
    void delete(Long id);

    /**
     * 根据权限标识获取菜单ID列表
     *
     * @param permission 权限标识
     * @return 菜单ID列表
     */
    List<Long> getMenuIdListByPermission(String permission);

    /**
     * 为角色分配菜单权限
     *
     * @param roleId  角色ID
     * @param menuIds 菜单ID列表
     */
    void assignRoleMenu(Long roleId, List<Long> menuIds);

    /**
     * 根据菜单ID查询关联的菜单列表
     *
     * @param menuId 菜单ID
     * @return 关联的菜单ID列表
     */
    List<Long> selectListByMenuId(Long menuId);
}
