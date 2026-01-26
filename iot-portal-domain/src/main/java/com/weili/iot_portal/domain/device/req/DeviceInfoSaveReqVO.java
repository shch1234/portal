package com.weili.iot_portal.domain.device.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;


/**
 * 设备信息创建/修改 Request VO
 * 包含设备基本信息、位置信息和网络配置信息
 */
@Schema(description = "设备信息创建/修改 Request VO")
@Data
public class DeviceInfoSaveReqVO {

    @Schema(description = "设备信息ID", example = "123456789")
    private Long id;

    @Schema(description = "设备编号（威力编号，租户内唯一）", requiredMode = Schema.RequiredMode.REQUIRED, example = "WL-S21-JQ001")
    @NotBlank(message = "设备编号不能为空")
    @Size(max = 100, message = "设备编号长度不能超过100个字符")
    private String deviceCode;

    @Schema(description = "设备名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "五轴加工中心-001")
    @NotBlank(message = "设备名称不能为空")
    @Size(max = 255, message = "设备名称长度不能超过255个字符")
    private String deviceName;

    @Schema(description = "设备类型编码（父类型编码）", example = "MACHINE_TOOL")
    @Size(max = 100, message = "设备类型编码长度不能超过100个字符")
    private String deviceTypeCode;

    @Schema(description = "设备子类型编码（子类型编码，如果提供则优先使用，将写入device_type_code字段）", example = "CNC_MACHINING_CENTER")
    @Size(max = 100, message = "设备子类型编码长度不能超过100个字符")
    private String deviceSubTypeCode;

    @Schema(description = "设备型号ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456789")
    @NotNull
    private Long deviceModelId;

    @Schema(description = "所属厂区ID", example = "123456789")
    private Long orgFactoryId;

    @Schema(description = "所属车间ID", example = "123456789")
    private Long orgWorkshopId;

    @Schema(description = "所属产线ID", example = "123456789")
    private Long orgProductionLineId;

    @Schema(description = "设备状态：ACTIVE-在用 INACTIVE-停用 MAINTENANCE-维护中 RETIRED-报废", example = "ACTIVE")
    private String deviceStatus;

    @Schema(description = "是否监控：true-监控 false-不监控", example = "true")
    private Boolean isMonitored;

    @Schema(description = "备注信息")
    private String remarks;

    @Schema(description = "设备位置信息（可选，创建设备时可同时创建位置信息）")
    @Valid
    private DeviceLocationInfoReq location;

    @Schema(description = "设备网络配置信息（可选，创建设备时可同时创建网络配置）")
    @Valid
    private DeviceNetworkInfoReq network;

    @Schema(description = "设备参数配置信息（可选，创建设备时可同时创建参数配置）")
    @Valid
    private List<DeviceParamConfigReq> paramConfig;
}
