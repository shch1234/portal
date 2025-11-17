package com.weili.iot_portal.dal.dataobject.permission;

import com.baomidou.mybatisplus.annotation.TableName;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 角色和菜单关联
 */
@TableName(value = "system_role_menu", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleMenuDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = -2204983992598157334L;
    /**
     * 自增主键
     */
    private Long id;
    /**
     * 角色ID
     */
    private Long roleId;
    /**
     * 菜单ID
     */
    private Long menuId;

}
