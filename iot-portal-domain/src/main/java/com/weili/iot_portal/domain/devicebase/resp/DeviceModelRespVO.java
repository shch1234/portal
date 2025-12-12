package com.weili.iot_portal.domain.devicebase.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备型号 Response VO
 */
@Schema(description = "设备型号 Response VO")
@Data
public class DeviceModelRespVO {

    @Schema(description = "设备型号ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "型号编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String modelCode;

    @Schema(description = "型号名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String modelName;

    @Schema(description = "设备类型编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceTypeCode;

    @Schema(description = "制造商")
    private String manufacturer;

    @Schema(description = "通用规格参数（JSON）")
    private Map<String, Object> specifications;

    @Schema(description = "类型特定属性（JSON）")
    private Map<String, Object> typeSpecificAttrs;

    @Schema(description = "是否启用")
    private Boolean isActive;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;
}

