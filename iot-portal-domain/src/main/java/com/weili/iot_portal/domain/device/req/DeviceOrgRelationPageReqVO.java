package com.weili.iot_portal.domain.device.req;

import com.weili.basic.common.model.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备组织单元分页查询 Request VO
 */
@Schema(description = "设备组织单元分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceOrgRelationPageReqVO extends PageParam {

    @Schema(description = "组织单元编码（模糊匹配）", example = "FACTORY")
    @Size(max = 100, message = "组织单元编码长度不能超过100个字符")
    private String unitCode;

    @Schema(description = "组织单元名称（模糊匹配）", example = "厂区")
    @Size(max = 255, message = "组织单元名称长度不能超过255个字符")
    private String unitName;

    @Schema(description = "组织单元类型值（FACTORY/WORKSHOP/PRODUCTION_LINE）", example = "FACTORY")
    @Size(max = 100, message = "组织单元类型值长度不能超过100个字符")
    private String unitTypeValue;

    @Schema(description = "父级组织ID", example = "123456789")
    private String orgParentId;

    @Schema(description = "层级：1厂区、2车间、3产线", example = "1")
    private Integer levelNo;

    @Schema(description = "是否启用：true-启用 false-停用", example = "true")
    private Boolean isActive;
}

