package com.weili.iot_portal.service.system;

/**
 * @InterfaceName: IPermissionBizService
 * @Description:
 * @Author: luying
 **/
public interface IPermissionBizService {
    boolean hasAnyPermissions(Long userId, String... permissions);

}
