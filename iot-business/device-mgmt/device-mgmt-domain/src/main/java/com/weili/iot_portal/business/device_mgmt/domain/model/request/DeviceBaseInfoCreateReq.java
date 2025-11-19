package com.weili.iot_portal.business.device_mgmt.domain.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 创建设备基础信息请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceBaseInfoCreateReq extends DeviceBaseInfoBaseReq {

    /**
     * 组织单元（厂区/部门）ID，来源于 Portal 组织模型
     */
    @NotBlank(message = "组织单元不能为空")
    private String organizationUnitId;
}


