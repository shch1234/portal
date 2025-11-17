package com.weili.iot_portal.domain.system;

import com.weili.basic.common.model.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Schema(description = "字典类型分页列表 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class DictDataPageReqVO extends PageParam {

    @Schema(description = "字典标签")
    @Size(max = 100, message = "字典标签长度不能超过100个字符")
    private String label;

    @Schema(description = "字典类型，模糊匹配", example = "sys_common_sex")
    @Size(max = 100, message = "字典类型类型长度不能超过100个字符")
    private String dictType;

    @Schema(description = "展示状态，参见 StatusEnum 枚举类", example = "1")
    private Integer status;

}
