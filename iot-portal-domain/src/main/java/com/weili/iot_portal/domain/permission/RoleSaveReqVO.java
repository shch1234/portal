package com.weili.iot_portal.domain.permission;

import com.weili.basic.common.model.BaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

@Schema(description = "角色创建/更新")
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleSaveReqVO  extends BaseVO {

    @Serial
    private static final long serialVersionUID = 3652575454377842589L;

    @Schema(description = "角色编号", example = "1")
    private Long id;

    @Schema(description = "角色名称-长度不能超过 30 个字符", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "角色标志", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer sort;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
