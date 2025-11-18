package com.weili.iot_portal.service.system;

/**
 * @InterfaceName: IPermissionBizService
 * @Description:
 * @Author: luying
 **/
public interface IPermissionBizService {

    /**
     * 判断是否有权限，任一一个即可
     *
     * @param userId      用户编号
     * @param permissions 权限
     * @return 是否
     */
    boolean hasAnyPermissions(Long userId, String... permissions);

}
