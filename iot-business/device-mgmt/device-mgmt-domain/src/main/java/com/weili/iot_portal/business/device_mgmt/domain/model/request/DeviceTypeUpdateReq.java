package com.weili.iot_portal.business.device_mgmt.domain.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 更新设备类型请求
 */
@Data
public class DeviceTypeUpdateReq {

    @NotBlank(message = "设备类型ID不能为空")
    private String id;

    private String typeName;

    private String description;

    private String icon;

    private Map<String, Object> customFields;

    private Boolean isActive;

    private Integer sortOrder;
}


