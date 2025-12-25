package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 设备网络配置信息
 */
@Data
@Schema(description = "设备网络配置信息")
public class DeviceNetworkInfo {
    @Schema(description = "IP地址")
    private String ipAddress;

    @Schema(description = "端口号")
    private Integer port;

    @Schema(description = "MAC地址")
    private String macAddress;

    @Schema(description = "网关地址")
    private String gateway;

    @Schema(description = "子网掩码")
    private String subnetMask;

    @Schema(description = "通信协议")
    private String protocol;

    @Schema(description = "连接参数（JSON）")
    private Map<String, Object> connectionParams;

    @Schema(description = "生效开始时间戳（秒）")
    private Long effectiveStartTs;

    @Schema(description = "生效结束时间戳（秒）")
    private Long effectiveEndTs;

    @Schema(description = "配置说明")
    private String description;
}

