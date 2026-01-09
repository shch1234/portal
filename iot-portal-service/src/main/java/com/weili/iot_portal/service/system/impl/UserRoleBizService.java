package com.weili.iot_portal.service.system.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjUtil;
import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.BaseException;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.common.enums.RoleCodeEnum;
import com.weili.iot_portal.common.enums.StatusEnum;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.dal.dataobject.system.LoginUserDO;
import com.weili.iot_portal.dal.dataobject.system.RoleDO;
import com.weili.iot_portal.dal.dataobject.system.UserRoleDO;
import com.weili.iot_portal.dal.ddd.system.RolePageQuery;
import com.weili.iot_portal.dal.repository.system.IRoleRepository;
import com.weili.iot_portal.dal.repository.system.IUserRoleRepository;
import com.weili.iot_portal.dal.repository.system.impl.LoginUserRepository;
import com.weili.iot_portal.domain.permission.LoginUserRespVO;
import com.weili.iot_portal.domain.permission.RolePageReqVO;
import com.weili.iot_portal.domain.permission.RoleSaveReqVO;
import com.weili.iot_portal.service.system.IMenuBizService;
import com.weili.iot_portal.service.system.IUserRoleBizService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @ClassName: UserRoleBizService
 * @Description:
 * @Author: luying
 * @Date: 2025-07-04 22:12
 **/
@Service
@Slf4j
public class UserRoleBizService implements IUserRoleBizService {

    @Resource
    private IRoleRepository roleRepository;
    @Resource
    private IMenuBizService menuBizService;
    @Resource
    private IUserRoleRepository userRoleRepository;
    @Resource
    private LoginUserRepository loginUserRepository;

    @Override
    public boolean userHasBindRole(Long roleId) {
        return CollectionUtils.isNotEmpty(userRoleRepository.selectListByRoleIds(Collections.singletonList(roleId)));
    }


    @Override
    public List<RoleDO> getRoleByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<UserRoleDO> roleDOList = userRoleRepository.selectListByUserId(userId);
        if (CollectionUtils.isEmpty(roleDOList)) {
            return Collections.emptyList();
        }
        List<Long> roleIds = roleDOList.stream().map(UserRoleDO::getRoleId).collect(Collectors.toList());
        return roleRepository.selectByIds(roleIds);
    }

    @Override
    public PageResult<RoleDO> getRolePage(RolePageReqVO reqVO) {
        return roleRepository.selectPage(BeanUtils.toBean(reqVO, RolePageQuery.class));
    }

    @Override
    public List<RoleDO> selectEnableList() {
        return roleRepository.selectEnableList();
    }


    @Override
    public RoleDO getRole(Long id) {
        return roleRepository.selectById(id);
    }

    @Override
    public RoleDO getRole(String roleKey) {
        return roleRepository.selectByCode(roleKey);
    }

    @Override
    public void assignUserRole(Long userId, List<Long> roleIds) {
        // 获得角色拥有角色编号
        List<Long> dbRoleIds = com.weili.basic.common.util.CollectionUtils.convertList(userRoleRepository.selectListByUserId(userId),
                UserRoleDO::getRoleId);
        // 计算新增和删除的角色编号
        List<Long> roleIdList = CollUtil.emptyIfNull(roleIds);
        Collection<Long> createRoleIds = CollUtil.subtract(roleIdList, dbRoleIds);
        Collection<Long> deleteMenuIds = CollUtil.subtract(dbRoleIds, roleIdList);
        // 执行新增和删除。对于已经授权的角色，不用做任何处理
        if (!CollectionUtil.isEmpty(createRoleIds)) {
            userRoleRepository.batchCreate(com.weili.basic.common.util.CollectionUtils.convertList(createRoleIds, roleId -> {
                UserRoleDO entity = new UserRoleDO();
                entity.setUserId(userId);
                entity.setRoleId(roleId);
                return entity;
            }));
        }
        if (!CollectionUtil.isEmpty(deleteMenuIds)) {
            userRoleRepository.deleteListByUserIdAndRoleIdIds(userId, deleteMenuIds);
        }
    }

    @Override
    @Transactional
    public void insertUserRole(List<Long> userIds, List<Long> roleIds) {
        userRoleRepository.deleteListByUserIds(userIds);//先删除后新增
        List<UserRoleDO> userRoleList = getUserRoleList(userIds, roleIds);
        userRoleRepository.batchCreate(userRoleList);
    }

    @Override
    @Transactional
    public void updateUserRole(List<Long> userIds, List<Long> roleIds) {
        userRoleRepository.deleteListByUserIds(userIds);//先删除后新增
        List<UserRoleDO> userRoleList = getUserRoleList(userIds, roleIds);
        userRoleRepository.batchCreate(userRoleList);
    }

    @Override
    @Transactional
    public void deleteUserRole(List<Long> userIds, List<Long> roleIds) {
        if (CollectionUtil.isNotEmpty(userIds) && CollectionUtil.isEmpty(roleIds)) {
            userRoleRepository.deleteListByUserIds(userIds);//先删除
        }
        if (CollectionUtil.isNotEmpty(roleIds) && CollectionUtil.isEmpty(userIds)) {
            userRoleRepository.deleteListByRoleIds(roleIds);
        }
        if (CollectionUtil.isNotEmpty(roleIds) && CollectionUtil.isNotEmpty(userIds)) {
            List<UserRoleDO> userRoleList = getUserRoleList(userIds, roleIds);
            userRoleList.forEach(entity -> userRoleRepository.deleteListByUserIdAndRoleId(entity.getUserId(), entity.getRoleId()));
        }
    }


    @Override
    public Long createRole(RoleSaveReqVO createReqVO) {
        validateRoleDuplicate(createReqVO.getName(), createReqVO.getCode(), createReqVO.getId());
        // 2. 插入到数据库
        RoleDO role = BeanUtils.toBean(createReqVO, RoleDO.class);
        role.setStatus(ObjUtil.defaultIfNull(createReqVO.getStatus(), StatusEnum.ENABLE.getStatus()));
        roleRepository.create(role);
        return role.getId();
    }

    @Override
    public void updateRole(RoleSaveReqVO updateReqVO) {
        RoleDO role = roleRepository.selectById(updateReqVO.getId());
        if (role == null) {
            throw new ServiceException(ErrorCodeConstants.ROLE_NOT_EXISTS);
        }
        validateRoleDuplicate(updateReqVO.getName(), updateReqVO.getCode(), updateReqVO.getId());
        RoleDO update = BeanUtils.toBean(updateReqVO, RoleDO.class);
        roleRepository.update(update);
    }

    @Override
    public void batchAddUserRole(Long userId, List<Long> roleIds) {
        if (userId == null || CollectionUtils.isEmpty(roleIds)) {
            return;
        }
        //角色
        List<UserRoleDO> list = roleIds.stream().map(r -> {
            UserRoleDO roleDO = new UserRoleDO();
            roleDO.setUserId(userId);
            roleDO.setRoleId(r);
            return roleDO;
        }).toList();
        userRoleRepository.batchCreate(list);
    }

    @Override
    public void deleteByUserId(Long userId) {
        userRoleRepository.deleteListByUserId(userId);
    }

    @Override
    public List<LoginUserRespVO> selectUserList(String code) {
        RoleDO roleDO = roleRepository.selectByCode(code);
        if (roleDO == null) {
            throw new ServiceException(ErrorCodeConstants.ROLE_NOT_EXISTS);
        }
        List<UserRoleDO> userRoleList = userRoleRepository.selectListByRoleIds(List.of(roleDO.getId()));
        if (CollectionUtils.isEmpty(userRoleList)) {
            return Collections.emptyList();
        }
        List<Long> userIds = userRoleList.stream().map(UserRoleDO::getUserId).distinct().toList();
        List<LoginUserDO> userList = loginUserRepository.listByUserIds(userIds);
        return userList.stream().map(user -> {
            LoginUserRespVO userRespVO = BeanUtils.toBean(user, LoginUserRespVO.class);
            userRespVO.setId(user.getId());
            userRespVO.setUsername(user.getUsername());
            return userRespVO;
        }).collect(Collectors.toList());
    }


    @Override
    @Transactional
    public void deleteRole(Long id) {
        RoleDO role = roleRepository.selectById(id);
        if (role == null) {
            throw new ServiceException(ErrorCodeConstants.ROLE_NOT_EXISTS);
        }
        if (userHasBindRole(id)) {
            throw new BaseException(IotPortalErrorCode.DATA_OPERATE_ERROR, "角色已绑定用户，不允许删除");
        }
        roleRepository.delete(id);
        userRoleRepository.deleteListByRoleId(id);
        menuBizService.deleteListByRoleId(id);
    }


    /**
     * 校验角色的唯一字段是否重复
     * 1. 是否存在相同名字的角色
     * 2. 是否存在相同编码的角色
     *
     * @param name 角色名字
     * @param code 角色额编码
     * @param id   角色编号
     */
    private void validateRoleDuplicate(String name, String code, Long id) {
        // 0. 超级管理员，不允许创建
        if (RoleCodeEnum.isSuperAdmin(code)) {
            throw new ServiceException(ErrorCodeConstants.ROLE_ADMIN_CODE_ERROR.getCode(), code);
        }
        // 1. 该 name 名字被其它角色所使用
        RoleDO role = roleRepository.selectByName(name);
        if (role != null && !role.getId().equals(id)) {
            throw new ServiceException(ErrorCodeConstants.ROLE_NAME_DUPLICATE.getCode(), name);
        }
        // 2. 是否存在相同编码的角色
        if (!StringUtils.hasText(code)) {
            return;
        }
        // 该 code 编码被其它角色所使用
        role = roleRepository.selectByCode(code);
        if (role != null && !role.getId().equals(id)) {
            throw new ServiceException(ErrorCodeConstants.ROLE_CODE_DUPLICATE.getCode(), code);
        }
    }


    private List<UserRoleDO> getUserRoleList(List<Long> userIds, List<Long> roleIds) {
        List<UserRoleDO> userRoleList = new ArrayList<>();
        if (CollectionUtil.isEmpty(userIds) || CollectionUtil.isEmpty(roleIds)) {
            return userRoleList;
        }
        userIds.forEach(userId -> roleIds.forEach(roleId -> {
            UserRoleDO entity = new UserRoleDO();
            entity.setUserId(userId);
            entity.setRoleId(roleId);
            userRoleList.add(entity);
        }));
        return userRoleList;
    }

}
