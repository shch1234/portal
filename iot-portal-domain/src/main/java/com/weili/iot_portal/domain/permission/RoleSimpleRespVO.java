package com.weili.iot_portal.domain.permission;

import com.weili.basic.common.model.BaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "角色精简信息 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleSimpleRespVO  extends BaseVO {

    @Schema(description = "角色编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

}
