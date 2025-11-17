package com.weili.iot_portal.domain.permission;

import com.weili.basic.common.model.BaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

@Schema(description = "角色信息 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleRespVO  extends BaseVO {
    @Serial
    private static final long serialVersionUID = -1568029973060131258L;

    @Schema(description = "角色编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "管理员")
    private String name;

    @Schema(description = "角色key")
    private String code;

    @Schema(description = "用户绑定状态 0-否 1-是")
    private String bingUserState;

    @Schema(description = "角色排序")
    private Integer sort;

    @Schema(description = "角色状态 1-开启 0-关闭")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "时间戳格式")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "时间戳格式")
    private LocalDateTime updateTime;
}
