package com.weili.iot_portal.domain.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备列表响应VO
 *
 * @author luying
 */
@Data
@Schema(description = "设备列表响应")
public class DeviceListRespVO {

    @Schema(description = "设备ID", example = "123456789")
    private Long deviceId;

    @Schema(description = "设备编码", example = "DEV001")
    private String deviceCode;

    @Schema(description = "设备类型编码", example = "TYPE001")
    private String deviceTypeCode;

    @Schema(description = "设备状态：0-在线，1-离线，2-故障", example = "0")
    private String state;
}
