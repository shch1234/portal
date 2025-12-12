package com.weili.iot_portal.domain.devicebase.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备类型 Response VO
 */
@Schema(description = "设备类型 Response VO")
@Data
public class DeviceTypeRelationRespVO {

    @Schema(description = "设备类型ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "设备类型编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String typeCode;

    @Schema(description = "父类型ID")
    private String parentTypeId;

    @Schema(description = "父类型编码")
    private String parentTypeCode;

    @Schema(description = "层级：1主类型、2子类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer levelNo;

    @Schema(description = "类型路径")
    private String path;

    @Schema(description = "业务分类")
    private String category;

    @Schema(description = "类型描述")
    private String description;

    @Schema(description = "自定义字段定义（JSON）")
    private Map<String, Object> customFields;

    @Schema(description = "是否启用")
    private Boolean isActive;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;
}

