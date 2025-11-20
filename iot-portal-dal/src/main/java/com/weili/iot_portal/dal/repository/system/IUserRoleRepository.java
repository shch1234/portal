
package com.weili.iot_portal.dal.repository.system;

import com.weili.iot_portal.dal.dataobject.system.UserRoleDO;

import java.util.Collection;
import java.util.List;

/**
 * @InterfaceName: IRoleRepository
 * @Description:
 * @Author: luying
 **/
public interface IUserRoleRepository {

    void batchCreate(List<UserRoleDO> list);

    List<UserRoleDO> selectListByUserId(Long userId);

    void deleteListByUserIdAndRoleIdIds(Long userId, Collection<Long> roleIds);

    void deleteListByUserIdAndRoleId(Long userId, Long roleId);

    void deleteListByUserIds(List<Long> userIds);

    void deleteListByUserId(Long userId);

    void deleteListByRoleId(Long roleId);


    void deleteListByRoleIds(List<Long> roleIds);

    List<UserRoleDO> selectListByRoleIds(List<Long> roleIds);
}
