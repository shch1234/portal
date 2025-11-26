package com.weili.iot_portal.domain.devicebase.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 创建设备关系请求
 */
@Data
public class DeviceRelationCreateReq {

    @NotBlank(message = "源设备ID不能为空")
    private String fromDeviceId;

    @NotBlank(message = "目标设备ID不能为空")
    private String toDeviceId;

    @NotBlank(message = "关系类型不能为空")
    private String relationType;

    private String relationName;

    private String description;

    private Map<String, Object> properties;

    private Boolean isActive;
}

