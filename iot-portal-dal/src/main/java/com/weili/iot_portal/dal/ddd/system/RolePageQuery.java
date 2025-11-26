package com.weili.iot_portal.dal.ddd.system;

import com.weili.basic.common.model.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * @author luying
 * @description: TODO
 * @date 2025/6/4 16:36
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RolePageQuery extends PageParam {
    @Serial
    private static final long serialVersionUID = 4800796045608097060L;
    /**
     * 角色名称，模糊匹配
     */
    private String name;

    /**
     * 角色标识，模糊匹配
     */
    private String code;

    /**
     * 展示状态
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime[] createTime;
}
