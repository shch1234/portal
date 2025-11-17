package com.weili.iot_portal.domain.permission;

import com.weili.basic.common.model.BaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.util.Collections;
import java.util.List;

@Schema(description = "赋予用户角色 Request VO")
@Getter
@Setter
public class PermissionAssignUserRoleReqVO extends BaseVO {

    @Serial
    private static final long serialVersionUID = 6326686727894092622L;
    @Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "用户编号不能为空")
    private Long userId;

    @Schema(description = "角色编号列表", example = "1,3,5")
    private List<Long> roleIds = Collections.emptyList(); // 兜底

}
