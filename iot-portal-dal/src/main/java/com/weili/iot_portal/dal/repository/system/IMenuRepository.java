package com.weili.iot_portal.dal.repository.system;

import com.weili.iot_portal.dal.dataobject.system.MenuDO;
import com.weili.iot_portal.dal.ddd.system.MenuListQuery;

import java.util.List;

/**
 * @InterfaceName: IMenuRepository
 * @Description:
 * @Author: luying
 **/
public interface IMenuRepository {

    void create(MenuDO menuDO);

    void update(MenuDO menuDO);

    void delete(Long id);

    MenuDO getByParentIdAndName(Long parentId, String name);

    Long getCountByParentId(Long parentId);

    List<MenuDO> listByQuery(MenuListQuery request);

    List<MenuDO> listByPermission(String permission);

    MenuDO getById(Long id);

    List<MenuDO> listAll();
}
