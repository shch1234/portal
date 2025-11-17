package com.weili.iot_portal.domain.permission;

import com.weili.basic.common.model.BaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@Schema(description = "菜单列表 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class MenuListReqVO  extends BaseVO {

    @Schema(description = "菜单名称，模糊匹配")
    private String name;

    @Schema(description = "展示状态，参见 StatusEnum 枚举类", example = "1")
    private Integer status;

    private String client;//客户端标识

    private String image;

    private Set<Long> menuIds;

}