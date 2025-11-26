package com.weili.iot_portal.dal.ddd.system;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author luying
 * @date 2025/6/4 16:32
 */
@Data
@Builder
public class MenuListQuery {
    private String name;
    private Integer status;
    private List<Long> menuIds;
}
