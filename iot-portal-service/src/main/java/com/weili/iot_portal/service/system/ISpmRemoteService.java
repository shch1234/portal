package com.weili.iot_portal.service.system;

/**
 * @InterfaceName: ISpmRemoteService
 * @Description: 从中台登录
 * @Author: luying
 **/
public interface ISpmRemoteService {

    /**
     * 获取登录用户token
     */
    String loginUser(Integer jobNumber, String password);
}
