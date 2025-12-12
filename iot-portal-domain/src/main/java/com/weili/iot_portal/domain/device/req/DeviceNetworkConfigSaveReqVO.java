package com.weili.iot_portal.domain.device.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 设备网络配置创建/修改 Request VO
 */
@Schema(description = "设备网络配置创建/修改 Request VO")
@Data
public class DeviceNetworkConfigSaveReqVO {

    @Schema(description = "设备网络配置ID", example = "123456789")
    private String id;

    @Schema(description = "设备信息ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456789")
    @NotBlank(message = "设备信息ID不能为空")
    private String deviceInfoId;

    @Schema(description = "所属厂区ID", example = "123456789")
    private String orgFactoryId;

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

    @Schema(description = "连接参数（JSON）：超时、重试、轮询间隔等")
    private Map<String, Object> connectionParams;

    @Schema(description = "生效开始时间戳（秒，Unix时间戳）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1704067200")
    @NotNull(message = "生效开始时间戳不能为空")
    private Long effectiveStartTs;

    @Schema(description = "生效结束时间戳（秒，Unix时间戳，NULL表示当前生效）", example = "1735689600")
    private Long effectiveEndTs;

    @Schema(description = "是否当前生效：true-当前生效 false-历史版本", example = "true")
    private Boolean isActive;

    @Schema(description = "配置说明", example = "IP地址变更、网络调整等")
    private String description;
}

