package com.weili.iot_portal.domain.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备状态统计响应VO
 *
 * @author luying
 */
@Data
@Schema(description = "设备状态统计响应")
public class DeviceStateStatisticsRespVO {

    @Schema(description = "设备总数", example = "231")
    private Integer totalDevices;

    @Schema(description = "在线设备数", example = "123")
    private Integer onlineDevices;

    @Schema(description = "离线设备数", example = "123")
    private Integer offlineDevices;

    @Schema(description = "故障设备数", example = "123")
    private Integer faultDevices;
}
