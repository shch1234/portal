package com.weili.iot_portal.dal.dataobject.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 用户和角色关联
 */
@TableName(value = "system_user_role", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class UserRoleDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = -8242691917754154085L;
    /**
     * 自增主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 用户 ID
     */
    private Long userId;
    /**
     * 角色 ID
     */
    private Long roleId;
}
