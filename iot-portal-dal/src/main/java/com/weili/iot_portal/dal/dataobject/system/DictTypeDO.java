package com.weili.iot_portal.dal.dataobject.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.*;

/**
 * 字典类型表
 */
@TableName("system_dict_type")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictTypeDO extends BaseSimpleDO {

    /**
     * 字典主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 字典名称
     */
    private String name;
    /**
     * 字典类型
     */
    private String type;
    /**
     * 状态
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;
}
