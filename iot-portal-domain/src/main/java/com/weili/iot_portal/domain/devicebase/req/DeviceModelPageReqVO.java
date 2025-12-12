package com.weili.iot_portal.domain.devicebase.req;

import com.weili.basic.common.model.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备型号分页查询 Request VO
 */
@Schema(description = "设备型号分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceModelPageReqVO extends PageParam {

    @Schema(description = "型号编码（模糊匹配）", example = "MODEL")
    @Size(max = 100, message = "型号编码长度不能超过100个字符")
    private String modelCode;

    @Schema(description = "型号名称（模糊匹配）", example = "加工中心")
    @Size(max = 255, message = "型号名称长度不能超过255个字符")
    private String modelName;

    @Schema(description = "设备类型编码", example = "CNC_5AXIS")
    @Size(max = 100, message = "设备类型编码长度不能超过100个字符")
    private String deviceTypeCode;

    @Schema(description = "制造商（模糊匹配）", example = "威力")
    @Size(max = 255, message = "制造商长度不能超过255个字符")
    private String manufacturer;

    @Schema(description = "是否启用：true-启用 false-停用", example = "true")
    private Boolean isActive;
}

