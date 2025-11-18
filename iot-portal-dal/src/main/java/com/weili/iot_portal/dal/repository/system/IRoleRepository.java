package com.weili.iot_portal.dal.repository.system;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.permission.RoleDO;
import com.weili.iot_portal.dal.ddd.RolePageQuery;

import java.util.List;

/**
 * @InterfaceName: IRoleRepository
 * @Description:
 * @Author: luying
 **/
public interface IRoleRepository {

    void create(RoleDO roleDO);

    void update(RoleDO roleDO);

    void delete(Long id);

    PageResult<RoleDO> selectPage(RolePageQuery query);

    RoleDO selectById(Long id);

    RoleDO selectByName(String name);

    RoleDO selectByCode(String code);

    List<RoleDO> selectEnableList();

    List<RoleDO> selectByIds(List<Long> ids);
}
