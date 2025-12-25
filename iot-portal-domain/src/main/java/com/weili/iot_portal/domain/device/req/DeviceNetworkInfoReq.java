package com.weili.iot_portal.domain.device.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备网络配置信息（嵌套对象）
 */
@Data
@Schema(description = "设备网络配置信息")
public class DeviceNetworkInfoReq {
    @Schema(description = "IP地址", example = "192.168.1.100")
    @Size(max = 50, message = "IP地址长度不能超过50个字符")
    private String ipAddress;

    @Schema(description = "端口号", example = "8080")
    private Integer port;

    @Schema(description = "MAC地址", example = "00:11:22:33:44:55")
    @Size(max = 50, message = "MAC地址长度不能超过50个字符")
    private String macAddress;

    @Schema(description = "网关地址", example = "192.168.1.1")
    @Size(max = 50, message = "网关地址长度不能超过50个字符")
    private String gateway;

    @Schema(description = "子网掩码", example = "255.255.255.0")
    @Size(max = 50, message = "子网掩码长度不能超过50个字符")
    private String subnetMask;

    @Schema(description = "通信协议：MQTT、MODBUS、OPC_UA、FOCAS等", example = "MQTT")
    @Size(max = 50, message = "通信协议长度不能超过50个字符")
    private String protocol;

    @Schema(description = "生效开始时间yyyy-mm-dd")
    private LocalDateTime effectiveStart;

    @Schema(description = "生效结束时间yyyy-mm-dd")
    private LocalDateTime effectiveEnd;

    @Schema(description = "配置说明", example = "IP地址变更、网络调整等")
    private String description;
}

