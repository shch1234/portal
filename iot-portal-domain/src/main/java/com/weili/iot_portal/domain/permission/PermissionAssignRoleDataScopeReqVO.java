package com.weili.iot_portal.domain.permission;

import com.weili.basic.common.model.BaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.Set;

@Schema(description = "赋予角色数据权限 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class PermissionAssignRoleDataScopeReqVO  extends BaseVO {

    @Schema(description = "角色编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "角色编号不能为空")
    private Long roleId;

    @Schema(description = "数据范围，参见 DataScopeEnum 枚举类", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "数据范围不能为空")
    private Integer dataScope;

    @Schema(description = "部门编号列表，只有范围类型为 DEPT_CUSTOM 时，该字段才需要", example = "1,3,5")
    private Set<Long> dataScopeDeptIds = Collections.emptySet(); // 兜底

}
