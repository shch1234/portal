package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 报警管理响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "报警管理响应")
public class AlarmManageRespVO {

    @Schema(description = "序号", example = "1")
    private Integer rowNum;

    @Schema(description = "设备编号", example = "sb-001")
    private String deviceCode;

    @Schema(description = "设备类型", example = "机床-扁丝机")
    private String deviceType;

    @Schema(description = "报警号", example = "***")
    private String alarmCode;

    @Schema(description = "报警内容", example = "减速路机加车间")
    private String alarmText;

    @Schema(description = "开始时间", example = "2025-11-14 10:32:45")
    private String startTime;

    @Schema(description = "结束时间", example = "2025-11-14 12:32:45")
    private String endTime;

    @Schema(description = "持续时间(S)", example = "3600")
    private Integer durationS;

    @Schema(description = "是否报警中：1-报警中 0-已解除", example = "1")
    private Integer isActive;
}
