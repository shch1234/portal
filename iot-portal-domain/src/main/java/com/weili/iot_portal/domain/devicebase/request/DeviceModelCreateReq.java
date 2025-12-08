package com.weili.iot_portal.domain.devicebase.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 创建设备型号请求
 */
@Data
public class DeviceModelCreateReq {

    @NotBlank(message = "型号编码不能为空")
    private String modelCode;

    @NotBlank(message = "型号名称不能为空")
    private String modelName;

    @NotBlank(message = "设备类型编码不能为空")
    private String deviceTypeCode;

    private String manufacturer;

    private Map<String, Object> specifications;

    private Map<String, Object> typeSpecificAttrs;

    private Boolean isActive;
}

