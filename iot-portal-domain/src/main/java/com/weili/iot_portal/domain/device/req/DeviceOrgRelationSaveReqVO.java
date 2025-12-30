package com.weili.iot_portal.domain.device.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设备组织单元创建/修改 Request VO
 */
@Schema(description = "设备组织单元创建/修改 Request VO")
@Data
public class DeviceOrgRelationSaveReqVO {

    @Schema(description = "组织单元ID", example = "123456789")
    private String id;

    @Schema(description = "组织单元编码（租户内唯一）", requiredMode = Schema.RequiredMode.REQUIRED, example = "FACTORY_A")
    @NotBlank(message = "组织单元编码不能为空")
    @Size(max = 100, message = "组织单元编码长度不能超过100个字符")
    private String unitCode;

    @Schema(description = "组织单元名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "第一厂区")
    @NotBlank(message = "组织单元名称不能为空")
    @Size(max = 255, message = "组织单元名称长度不能超过255个字符")
    private String unitName;

    @Schema(description = "组织单元类型值（FACTORY/WORKSHOP/PRODUCTION_LINE）", requiredMode = Schema.RequiredMode.REQUIRED, example = "FACTORY")
    @NotBlank(message = "组织单元类型值不能为空")
    @Size(max = 100, message = "组织单元类型值长度不能超过100个字符")
    private String unitTypeValue;

    @Schema(description = "父级组织ID集合")
    private String orgParentId;

    @Schema(description = "描述信息")
    private String description;

    @Schema(description = "是否启用：true-启用 false-停用", example = "true")
    private Boolean isActive;
}

