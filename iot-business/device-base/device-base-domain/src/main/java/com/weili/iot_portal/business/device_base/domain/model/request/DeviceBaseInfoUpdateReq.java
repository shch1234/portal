package com.weili.iot_portal.business.device_base.domain.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新设备基础信息请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceBaseInfoUpdateReq extends DeviceBaseInfoBaseReq {

    @NotBlank(message = "设备 ID 不能为空")
    private String id;
}

