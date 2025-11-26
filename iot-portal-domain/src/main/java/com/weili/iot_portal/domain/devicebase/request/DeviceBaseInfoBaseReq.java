package com.weili.iot_portal.domain.devicebase.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 公共的设备基础信息请求字段
 */
@Data
public class DeviceBaseInfoBaseReq {

    @NotBlank(message = "设备编号不能为空")
    private String deviceCode;

    @NotBlank(message = "设备名称不能为空")
    private String deviceName;

    @NotBlank(message = "设备类型不能为空")
    private String deviceTypeId;

    @NotBlank(message = "设备型号不能为空")
    private String deviceModelId;

    /**
     * TB 设备 ID，可在关联时进行填充
     */
    private String tbDeviceId;

    private String deviceTypeName;

    private String deviceSubTypeName;

    private String modelName;

    private String manufacturer;

    private String factoryId;

    private String workshopId;

    private String productionLineId;

    private String factoryName;

    private String workshopName;

    private String productionLineName;

    private String deviceStatus;

    private Boolean isMonitored;

    private String remarks;

    private Map<String, Object> extraProperties;
}

