package com.weili.iot_portal.domain.devicebase.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 创建设备类型请求
 */
@Data
public class DeviceTypeCreateReq {

    @NotBlank(message = "类型编码不能为空")
    private String typeCode;

    @NotBlank(message = "类型字典值不能为空")
    private String typeDictValue;

    /**
     * 父级类型 ID，可为空
     */
    private String parentTypeId;

    @NotNull(message = "层级不能为空")
    private Integer levelNo;

    private String category;

    private String description;

    private String icon;

    private Map<String, Object> customFields;

    private Boolean isActive;

    private Integer sortOrder;
}

