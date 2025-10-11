package com.weili.example.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.weili.basic.framework.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author luying
 * @className ExampleDO
 * @description
 * @date 2025-10-11 11:04
 **/
@Data
@EqualsAndHashCode(callSuper = true)
public class ExampleDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
}
