package com.weili.iot_portal.service.system;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.permission.LoginUserPageReqVO;
import com.weili.iot_portal.domain.permission.LoginUserRespVO;
import com.weili.iot_portal.domain.permission.LoginUserSaveReqVO;

/**
 * @author luying
 * @description: TODO
 * @date 2025/6/20 23:25
 */
public interface ILoginUserBizService {

    /**
     * 获取用户信息：包含部门、组织、员工、角色数据
     */
    PageResult<LoginUserRespVO> selectPage(LoginUserPageReqVO pageReqVO);

    /**
     * 创建用户
     */
    void createUser();

    /**
     * 修改用户
     */
    void update(LoginUserSaveReqVO reqVO);

    LoginUserRespVO get(Long userId);
}
