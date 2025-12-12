package com.weili.iot_portal.domain.device.req;

import com.weili.basic.common.model.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备类型分页查询 Request VO
 */
@Schema(description = "设备类型分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceTypeRelationPageReqVO extends PageParam {

    @Schema(description = "设备类型编码（模糊匹配）", example = "CNC")
    @Size(max = 100, message = "设备类型编码长度不能超过100个字符")
    private String typeCode;

    @Schema(description = "父类型ID", example = "123456789")
    private String parentTypeId;

    @Schema(description = "层级：1主类型、2子类型", example = "1")
    private Integer levelNo;

    @Schema(description = "业务分类", example = "机床")
    @Size(max = 100, message = "业务分类长度不能超过100个字符")
    private String category;

    @Schema(description = "是否启用：true-启用 false-停用", example = "true")
    private Boolean isActive;
}

