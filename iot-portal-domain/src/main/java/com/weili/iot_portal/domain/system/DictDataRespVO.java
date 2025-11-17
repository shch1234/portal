package com.weili.iot_portal.domain.system;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "字典数据信息")
@Data
public class DictDataRespVO {

    @Schema(description = "字典数据编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer sort;

    @Schema(description = "字典标签", requiredMode = Schema.RequiredMode.REQUIRED)
    private String label;

    @Schema(description = "字典值", requiredMode = Schema.RequiredMode.REQUIRED)
    private String value;

    @Schema(description = "字典类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dictType;

    @Schema(description = "状态,见 StatusEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;

    @Schema(description = "颜色类型,default、primary、success、info、warning、danger")
    private String colorType;

    @Schema(description = "css 样式", example = "btn-visible")
    private String cssClass;

    @Schema(description = "备注", example = "我是一个角色")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "时间戳格式")
    private LocalDateTime createTime;

}
