package com.weili.iot_portal.service.system;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.permission.RoleDO;
import com.weili.iot_portal.domain.permission.RolePageReqVO;
import com.weili.iot_portal.domain.permission.RoleSaveReqVO;

import java.util.List;
import java.util.Map;

/**
 * @InterfaceName: IUserRoleBizService
 * @Description: 用户角色服务
 * @Author: luying
 **/
public interface IUserRoleBizService {

    boolean hasAnySuperAdmin(List<Long> roleIds);

    boolean isSuperAdminRole(Long userId);

    boolean userHasBindRole(Long roleId);

    List<RoleDO> getRoleByUserId(Long userId);

    Map<Long, List<RoleDO>> getRoleMapByUserId(List<Long> userIds);

    List<RoleDO> getRoleByRoleIds(List<Long> roleIds);

    /**
     * 批量增加用户角色关联数据
     *
     * @param userId
     * @param roleIds
     */
    void batchAddUserRole(Long userId, List<Long> roleIds);

    /**
     * 根据userId删除数据
     *
     * @param userId
     */
    void deleteByUserId(Long userId);

    /**
     * 给用户角色
     */
    void assignUserRole(Long userId, List<Long> roleIds);

    /**
     * 新增用户角色
     * @param userIds
     * @param roleIds
     */
    void insertUserRole(List<Long> userIds, List<Long> roleIds);

    /**
     * 更新用户角色
     * @param userIds
     * @param roleIds
     */
    void updateUserRole(List<Long> userIds, List<Long> roleIds);

    /**
     * 删除用户角色
     * @param userIds
     * @param roleIds
     */
    void deleteUserRole(List<Long> userIds, List<Long> roleIds);


    // -------------------------角色表操作----------------------
    /**
     * 获取角色数据
     *
     * @param id
     */
    RoleDO getRole(Long id);

    /**
     * 获取分页
     *
     * @param reqVO
     * @return
     */
    PageResult<RoleDO> getRolePage(RolePageReqVO reqVO);

    /**
     * 获取开启的角色列表
     *
     * @return
     */
    List<RoleDO> selectEnableList();

    /**
     * 删除角色
     * @param id
     */
    void deleteRole(Long id);

    /**
     * 新增角色数据
     *
     * @param createReqVO
     * @return
     */
    Long createRole(RoleSaveReqVO createReqVO);

    /**
     * 更新角色数据
     *
     * @param updateReqVO
     */
    void updateRole(RoleSaveReqVO updateReqVO);

    RoleDO getRoleByCode(String code);

}
