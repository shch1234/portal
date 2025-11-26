package com.weili.iot_portal.dal.ddd.system;

import com.weili.basic.common.model.PageParam;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


/**
 * @ClassName: DictTypePageQuery
 * @Description:
 * @Author: luying
 * @Date: 2025-07-01 22:26
 **/
@Getter
@Setter
public class DictTypePageQuery extends PageParam {
    private String name;
    private String type;
    private Integer status;
    private LocalDateTime[] createTime;
}
