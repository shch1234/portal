package com.weili.iot_portal.dal.repository.system;

import com.weili.iot_portal.dal.dataobject.system.RoleMenuDO;

import java.util.List;

/**
 * @InterfaceName: IRoleMenuRepository
 * @Description:
 * @Author: luying
 **/
public interface IRoleMenuRepository {

    void batchCreate(List<RoleMenuDO> list);

    List<RoleMenuDO> selectListByRoleId(Long roleId);

    List<RoleMenuDO> selectListByRoleId(List<Long> roleIds);

    List<RoleMenuDO> selectListByMenuId(Long menuId);

    void deleteListByRoleIdAndMenuIds(Long roleId, List<Long> menuIds);

    void deleteListByMenuId(Long menuId);

    void deleteListByRoleId(Long roleId);
}
