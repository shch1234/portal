package com.weili.iot_portal.domain.devicebase.req;

import com.weili.basic.common.model.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备网络配置分页查询 Request VO
 */
@Schema(description = "设备网络配置分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceNetworkConfigPageReqVO extends PageParam {

    @Schema(description = "设备信息ID", example = "123456789")
    private String deviceInfoId;

    @Schema(description = "所属厂区ID", example = "123456789")
    private String orgFactoryId;

    @Schema(description = "IP地址（模糊匹配）", example = "192.168")
    @Size(max = 50, message = "IP地址长度不能超过50个字符")
    private String ipAddress;

    @Schema(description = "通信协议", example = "MQTT")
    @Size(max = 50, message = "通信协议长度不能超过50个字符")
    private String protocol;

    @Schema(description = "是否当前生效：true-当前生效 false-历史版本", example = "true")
    private Boolean isActive;
}

