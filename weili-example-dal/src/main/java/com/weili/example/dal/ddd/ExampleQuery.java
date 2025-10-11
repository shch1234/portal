package com.weili.example.dal.ddd;

import com.weili.basic.common.model.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author luying
 * @className ExampleQuery
 * @description 不要直接使用VO对象进行dal查询
 * @date 2025-10-11 11:17
 **/
@Data
@EqualsAndHashCode(callSuper = true)
public class ExampleQuery extends PageParam {

    private String name;
}
