package com.weili.iot_portal.domain.device.req;

import com.weili.basic.common.model.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报警管理查询请求VO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "报警管理查询请求")
public class AlarmManageQueryReqVO extends PageParam {

    @Schema(description = "设备编号（支持模糊查询）", example = "sb-001")
    private String deviceCode;

    @Schema(description = "设备类型", example = "机床-扁丝机")
    private String deviceType;

    @Schema(description = "报警状态：1-报警中 0-已解除", example = "1")
    private Integer isActive;

    @Schema(description = "报警时间-开始", example = "2025-11-14 10:32:45")
    private String reportTimeStart;

    @Schema(description = "报警时间-结束", example = "2025-11-14 12:32:45")
    private String reportTimeEnd;
}
