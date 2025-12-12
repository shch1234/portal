package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备组织单元 Response VO
 */
@Schema(description = "设备组织单元 Response VO")
@Data
public class DeviceOrgRelationRespVO {

    @Schema(description = "组织单元ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "组织单元编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    @Schema(description = "组织单元名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitName;

    @Schema(description = "组织单元类型值", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitTypeValue;

    @Schema(description = "父级组织ID")
    private String orgParentId;

    @Schema(description = "层级：1厂区、2车间、3产线", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer levelNo;

    @Schema(description = "层级路径")
    private String path;

    @Schema(description = "描述信息")
    private String description;

    @Schema(description = "是否启用")
    private Boolean isActive;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;
}

