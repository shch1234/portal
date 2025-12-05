package com.weili.iot_portal.dal.dataobject.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * @author luying
 * @description: 登录用户
 * @date 2025/6/18 17:37
 */
@TableName(value = "system_login_user", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class LoginUserDO extends BaseSimpleDO {
    @Serial
    private static final long serialVersionUID = 6130390885793904754L;
    /**
     * 用户ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 员工号
     */
    private String jobNumber;

    /**
     * 用户账号
     */
    private String username;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 手机号码
     */
    private String mobile;

    /**
     * 帐号状态（0正常 1停用）
     */
    private Integer status = 0;

    /**
     * 最后登录时间
     */
    private LocalDateTime loginTime;

}
