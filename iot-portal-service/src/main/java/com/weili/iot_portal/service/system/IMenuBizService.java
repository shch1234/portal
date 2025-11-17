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

    List<MenuDO> getMenuListByRoleId(List<Long> roleIds);

    /**
     * 过滤掉关闭的菜单及其子菜单
     *
     * @param list 菜单列表
     * @return 过滤后的菜单列表
     */
    List<MenuDO> filterDisableMenus(List<MenuDO> list);

    List<MenuDO> getMenuList(MenuListReqVO reqVO);

    List<MenuDO> getSimpleMenuList(MenuListReqVO reqVO);

    MenuDO getMenu(Long id);

    void deleteListByRoleId(Long roleId);

    Long create(MenuSaveVO createReqVO);

    void update(MenuSaveVO updateReqVO);

    void delete(Long id);

    List<Long> getMenuIdListByPermission(String permission);

    void assignRoleMenu(Long roleId, List<Long> menuIds);

    List<Long> selectListByMenuId(Long menuId);
}
