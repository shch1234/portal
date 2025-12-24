package com.weili.iot_portal.domain.device.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设备类型创建/修改 Request VO
 */
@Schema(description = "设备类型创建/修改 Request VO")
@Data
public class DeviceTypeRelationSaveReqVO {

    @Schema(description = "设备类型ID", example = "123456789")
    private Long id;

    @Schema(description = "设备类型编码（字典 value）", requiredMode = Schema.RequiredMode.REQUIRED, example = "CNC_5AXIS")
    @NotBlank(message = "设备类型编码不能为空")
    @Size(max = 100, message = "设备类型编码长度不能超过100个字符")
    private String typeCode;

    @Schema(description = "父类型ID", example = "123456789")
    private Long parentTypeId;

    @Schema(description = "父类型编码", example = "MACHINE_TOOL")
    @Size(max = 100, message = "父类型编码长度不能超过100个字符")
    private String parentTypeCode;

    @Schema(description = "层级：1主类型、2子类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "层级不能为空")
    private Integer levelNo;

    @Schema(description = "业务分类：机床/机器人/PLC等", example = "机床")
    @Size(max = 100, message = "业务分类长度不能超过100个字符")
    private String category;

    @Schema(description = "类型描述")
    private String description;

    @Schema(description = "是否启用：true-启用 false-停用", example = "true")
    private Boolean isActive;

    @Schema(description = "排序号", example = "0")
    private Integer sortOrder;
}

