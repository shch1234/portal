package com.weili.iot_portal.dal.ddd.system;

import com.weili.basic.common.model.PageParam;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * @author luying
 * @description: TODO
 * @date 2025/6/20 23:21
 */
@Getter
@Setter
public class LoginUserPageQuery extends PageParam {
    private String searchKey;
    private LocalDateTime[] createTime;
}
