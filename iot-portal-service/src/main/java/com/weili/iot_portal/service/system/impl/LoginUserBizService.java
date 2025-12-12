package com.weili.iot_portal.service.system.impl;

import com.weili.basic.authorization.security.SecurityContextUtils;
import com.weili.basic.common.exception.BaseException;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.basic.oauth2.oidc.Oauth2UserDetail;
import com.weili.iot_portal.common.enums.BizErrorCodeEnum;
import com.weili.iot_portal.common.enums.RoleCodeEnum;
import com.weili.iot_portal.common.enums.StatusEnum;
import com.weili.iot_portal.dal.dataobject.system.LoginUserDO;
import com.weili.iot_portal.dal.dataobject.system.RoleDO;
import com.weili.iot_portal.dal.ddd.system.LoginUserPageQuery;
import com.weili.iot_portal.dal.repository.system.ILoginUserRepository;
import com.weili.iot_portal.domain.permission.LoginUserPageReqVO;
import com.weili.iot_portal.domain.permission.LoginUserRespVO;
import com.weili.iot_portal.domain.permission.LoginUserSaveReqVO;
import com.weili.iot_portal.service.system.ILoginUserBizService;
import com.weili.iot_portal.service.system.IUserRoleBizService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author luying
 * @description: TODO
 * @date 2025/6/20 23:36
 */
@Slf4j
@Service
public class LoginUserBizService implements ILoginUserBizService {

    @Resource
    private IUserRoleBizService userRoleBizService;
    @Resource
    private ILoginUserRepository loginUserRepository;

    @Override
    @Transactional
    public Long createUser() {
        Oauth2UserDetail loginUser = SecurityContextUtils.getLoginUser();
        LoginUserDO loginUserDO = loginUserRepository.getByEmpId(Long.valueOf(loginUser.getEmpId()));
        if (loginUserDO != null) {
            return loginUserDO.getId();
        }
        loginUserDO = new LoginUserDO();
        loginUserDO.setUserId(loginUser.getUserId());
        loginUserDO.setJobNumber(loginUser.getEmpId());
        loginUserDO.setUsername(loginUser.getUserName());
        loginUserDO.setMobile(loginUser.getPhone());
        loginUserDO.setStatus(StatusEnum.ENABLE.getStatus());
        loginUserDO.setCreateTime(LocalDateTime.now());
        loginUserDO.setUpdateTime(LocalDateTime.now());
        loginUserDO.setLoginTime(LocalDateTime.now());
        if (SecurityContextUtils.getDept() != null) {
            String deptId = SecurityContextUtils.getDept().getDeptId();
            loginUserDO.setDeptId(Long.valueOf(deptId));
        }
        loginUserRepository.create(loginUserDO);
        //默认系统管理员
        RoleDO roleDO = userRoleBizService.getRole(RoleCodeEnum.systemAdmin.name());
        userRoleBizService.batchAddUserRole(loginUser.getUserId(), Collections.singletonList(roleDO.getId()));
        return loginUser.getUserId();
    }


    @Override
    @Transactional
    public void update(LoginUserSaveReqVO reqVO) {
        Long userid = SecurityContextUtils.getUserid();
        userRoleBizService.deleteByUserId(userid);
        userRoleBizService.batchAddUserRole(userid, reqVO.getRoleList());
    }

    @Override
    public LoginUserRespVO getById(Long id) {
        LoginUserDO oldRow = loginUserRepository.getById(id);
        return BeanUtils.toBean(oldRow, LoginUserRespVO.class);
    }

    @Override
    public LoginUserRespVO getByEmpId(String empId) {
        LoginUserDO oldRow = loginUserRepository.getByEmpId(Long.parseLong(empId));
        return BeanUtils.toBean(oldRow, LoginUserRespVO.class);
    }


    @Override
    public PageResult<LoginUserRespVO> selectPage(LoginUserPageReqVO pageReqVO) {
        LoginUserPageQuery query = BeanUtils.toBean(pageReqVO, LoginUserPageQuery.class);
        PageResult<LoginUserDO> pageResult = loginUserRepository.selectPage(query);
        if (CollectionUtils.isEmpty(pageResult.getList())) {
            return PageResult.empty();
        }
        List<LoginUserDO> resultList = pageResult.getList();
        return PageResult.buildSuccess(pageResult.getTotal(), BeanUtils.toBean(resultList, LoginUserRespVO.class));
    }
}
