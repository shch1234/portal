package com.weili.iot_portal.business.device_base.domain.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 更新设备关系请求
 */
@Data
public class DeviceRelationUpdateReq {

    @NotBlank(message = "关系ID不能为空")
    private String id;

    private String relationName;

    private String description;

    private Map<String, Object> properties;

    private Boolean isActive;
}

