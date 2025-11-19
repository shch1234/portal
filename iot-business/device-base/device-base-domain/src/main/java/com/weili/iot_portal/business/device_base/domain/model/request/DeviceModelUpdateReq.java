package com.weili.iot_portal.business.device_base.domain.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 更新设备型号请求
 */
@Data
public class DeviceModelUpdateReq {

    @NotBlank(message = "型号ID不能为空")
    private String id;

    private String modelName;

    private String manufacturer;

    private Map<String, Object> specifications;

    private Map<String, Object> typeSpecificAttrs;

    private Boolean isActive;
}

