package com.weili.iot_portal.domain.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 报警时长TOP数据响应对象
 *
 * @author luying
 * @date 2026-01-12
 */
@Data
@Schema(description = "报警时长TOP数据响应对象")
public class AlarmDurationTopRespVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备编号")
    private String deviceCode;

    @Schema(description = "设备类型")
    private String deviceTypeCode;

    @Schema(description = "报警内容")
    private String alarmText;

    @Schema(description = "时长(秒)")
    private Integer durationS;
}
