package com.weili.iot_portal.dal.dataobject.permission;

import com.baomidou.mybatisplus.annotation.TableName;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 菜单
 */
@TableName(value = "system_menu", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class MenuDO extends BaseSimpleDO {
    @Serial
    private static final long serialVersionUID = 7654873401901166122L;

    /**
     * 菜单编号 - 根节点
     */
    public static final Long ID_ROOT = 0L;
    /**
     * 菜单编号
     */
    private Long id;
    /**
     * 菜单名称
     */
    private String name;
    /**
     * 权限标识
     * <p>
     * 一般格式为：${系统}:${模块}:${操作}
     * 例如说：system:admin:add，即 system 服务的添加管理员。
     * <p>
     * 当我们把该 MenuDO 赋予给角色后，意味着该角色有该资源：
     */
    private String permission;
    /**
     * 菜单类型
     * <p>
     * 枚举 MenuTypeEnum
     */
    private Integer type;
    /**
     * 显示顺序
     */
    private Integer sort;
    /**
     * 父菜单ID
     */
    private Long parentId;
    /**
     * 路由地址
     * <p>
     * 如果 path 为 http(s) 时，则它是外链
     */
    private String path;
    /**
     * 菜单图标
     */
    private String icon;
    /**
     * 组件路径
     */
    private String component;
    /**
     * 组件名
     */
    private String componentName;
    /**
     * pch5 app 客户端标识
     */
    private String client;

    private String image;
    /**
     * 状态
     * <p>
     * 枚举 StatusEnum
     */
    private Integer status;
    /**
     * 是否可见
     * <p>
     * 只有菜单、目录使用
     * 当设置为 true 时，该菜单不会展示在侧边栏，但是路由还是存在。例如说，一些独立的编辑页面 /edit/1024 等等
     */
    private Boolean visible;

    /**
     * 是否缓存
     * <p>
     * 只有菜单、目录使用，否使用 Vue 路由的 keep-alive 特性
     * 注意：如果开启缓存，则必须填写 {@link #componentName} 属性，否则无法缓存
     */
    private Boolean keepAlive;
    /**
     * 是否总是显示
     * <p>
     * 如果为 false 时，当该菜单只有一个子菜单时，不展示自己，直接展示子菜单
     */
    private Boolean alwaysShow;
}
