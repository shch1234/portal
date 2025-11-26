package com.weili.iot_portal.dal.ddd.system;

import com.weili.basic.common.model.PageParam;
import lombok.Getter;
import lombok.Setter;

/**
 * @ClassName: DictDataPageQuery
 * @Description:
 * @Author: luying
 * @Date: 2025-07-01 22:21
 **/
@Getter
@Setter
public class DictDataPageQuery extends PageParam {
    private String label;
    private String dictType;
    private Integer status;

}
