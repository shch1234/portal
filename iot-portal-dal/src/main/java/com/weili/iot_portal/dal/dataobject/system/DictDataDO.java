package com.weili.iot_portal.dal.dataobject.system;

import com.baomidou.mybatisplus.annotation.*;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据表
 */
@TableName("system_dict_data")
@Data
@EqualsAndHashCode(callSuper = true)
public class DictDataDO extends BaseSimpleDO {

    /**
     * 字典数据编号
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 字典排序
     */
    private Integer sort;
    /**
     * 字典标签
     */
    private String label;
    /**
     * 字典值
     */
    private String value;
    /**
     * 字典类型
     *
     * 冗余 {@link DictDataDO#getDictType()}
     */
    private String dictType;
    /**
     * 状态
     */
    private Integer status;
    /**
     * 颜色类型
     *
     * 对应到 element-ui 为 default、primary、success、info、warning、danger
     */
    private String colorType;
    /**
     * css 样式
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cssClass;
    /**
     * 备注
     */
    private String remark;

}
