package com.weili.iot_portal.service.system;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.permission.RoleDO;
import com.weili.iot_portal.domain.permission.RolePageReqVO;
import com.weili.iot_portal.domain.permission.RoleSaveReqVO;

import java.util.List;

/**
 * @InterfaceName: IUserRoleBizService
 * @Description: 用户角色服务
 * @Author: luying
 **/
public interface IUserRoleBizService {
    /**
     * 判断指定角色是否已绑定用户
     *
     * @param roleId 角色ID
     * @return 若存在用户绑定了该角色则返回true，否则返回false
     */
    boolean userHasBindRole(Long roleId);

    /**
     * 根据用户ID获取其拥有的所有角色
     *
     * @param userId 用户ID
     * @return 用户所拥有的角色列表
     */
    List<RoleDO> getRoleByUserId(Long userId);

    /**
     * 给用户分配角色（先删除原有角色再添加新角色）
     *
     * @param userId  用户ID
     * @param roleIds 要分配的角色ID列表
     */
    void assignUserRole(Long userId, List<Long> roleIds);

    /**
     * 新增用户与角色的关系
     *
     * @param userIds 用户ID列表
     * @param roleIds 角色ID列表
     */
    void insertUserRole(List<Long> userIds, List<Long> roleIds);

    /**
     * 更新用户与角色的关系（通常为替换关系）
     *
     * @param userIds 用户ID列表
     * @param roleIds 角色ID列表
     */
    void updateUserRole(List<Long> userIds, List<Long> roleIds);

    /**
     * 删除用户与角色的关系
     *
     * @param userIds 用户ID列表
     * @param roleIds 角色ID列表
     */
    void deleteUserRole(List<Long> userIds, List<Long> roleIds);


    // -------------------------角色表操作----------------------

    /**
     * 根据ID获取角色详情
     *
     * @param id 角色ID
     * @return 角色信息对象
     */
    RoleDO getRole(Long id);

    /**
     * 分页查询角色列表
     *
     * @param reqVO 查询条件封装对象
     * @return 分页结果集
     */
    PageResult<RoleDO> getRolePage(RolePageReqVO reqVO);

    /**
     * 获取启用状态下的所有角色列表
     *
     * @return 启用的角色列表
     */
    List<RoleDO> selectEnableList();

    /**
     * 删除指定角色
     *
     * @param id 角色ID
     */
    void deleteRole(Long id);

    /**
     * 创建新的角色
     *
     * @param createReqVO 角色创建请求参数
     * @return 新创建角色的ID
     */
    Long createRole(RoleSaveReqVO createReqVO);

    /**
     * 更新已有角色的信息
     *
     * @param updateReqVO 角色更新请求参数
     */
    void updateRole(RoleSaveReqVO updateReqVO);

}
