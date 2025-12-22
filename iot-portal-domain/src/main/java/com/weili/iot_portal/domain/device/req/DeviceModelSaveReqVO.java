package com.weili.iot_portal.domain.device.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设备型号创建/修改 Request VO
 */
@Schema(description = "设备型号创建/修改 Request VO")
@Data
public class DeviceModelSaveReqVO {

    @Schema(description = "设备型号ID", example = "123456789")
    private String id;

    @Schema(description = "型号编码（租户内唯一）", requiredMode = Schema.RequiredMode.REQUIRED, example = "MODEL-001")
    @NotBlank(message = "型号编码不能为空")
    @Size(max = 100, message = "型号编码长度不能超过100个字符")
    private String modelCode;

    @Schema(description = "型号名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "五轴加工中心")
    @NotBlank(message = "型号名称不能为空")
    @Size(max = 255, message = "型号名称长度不能超过255个字符")
    private String modelName;

    @Schema(description = "设备类型编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "CNC_5AXIS")
    @NotBlank(message = "设备类型编码不能为空")
    @Size(max = 100, message = "设备类型编码长度不能超过100个字符")
    private String deviceTypeCode;

    @Schema(description = "制造商", example = "威力机床")
    @Size(max = 255, message = "制造商长度不能超过255个字符")
    private String manufacturer;


    @Schema(description = "是否启用：true-启用 false-停用", example = "true")
    private Boolean isActive;
}

