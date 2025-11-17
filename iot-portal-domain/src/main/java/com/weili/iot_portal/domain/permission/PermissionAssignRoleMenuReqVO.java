package com.weili.iot_portal.domain.permission;

import com.weili.basic.common.model.BaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Collections;
import java.util.List;

@Schema(description = "赋予角色菜单 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class PermissionAssignRoleMenuReqVO  extends BaseVO {

    @Serial
    private static final long serialVersionUID = 6554445386565880763L;

    @Schema(description = "角色编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "角色编号不能为空")
    private Long roleId;

    @Schema(description = "菜单编号列表", example = "1,3,5")
    private List<Long> menuIds = Collections.emptyList(); // 兜底

    private String client;
}
