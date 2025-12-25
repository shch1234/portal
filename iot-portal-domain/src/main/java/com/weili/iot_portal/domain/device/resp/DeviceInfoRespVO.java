package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备信息 Response VO
 * 包含设备基本信息、位置信息和网络配置信息
 */
@Schema(description = "设备信息 Response VO")
@Data
public class DeviceInfoRespVO {

    @Schema(description = "设备信息ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "设备编号（威力编号）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceCode;

    @Schema(description = "设备名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceName;

    @Schema(description = "设备类型编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceTypeCode;

    @Schema(description = "设备类型名称")
    private String deviceTypeName;

    @Schema(description = "设备子类型code")
    private String deviceSubTypeCode;

    @Schema(description = "设备子类型名称")
    private String deviceSubTypeName;

    @Schema(description = "设备型号ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long deviceModelId;

    @Schema(description = "设备型号名称")
    private String deviceModelName;

    @Schema(description = "制造商")
    private String manufacturer;

    @Schema(description = "所属厂区ID")
    private Long orgFactoryId;

    @Schema(description = "所属车间ID")
    private Long orgWorkshopId;

    @Schema(description = "所属产线ID")
    private Long orgProductionLineId;

    @Schema(description = "厂区名称")
    private String factoryName;

    @Schema(description = "车间名称")
    private String workshopName;

    @Schema(description = "产线名称")
    private String productionLineName;

    @Schema(description = "设备状态：ACTIVE-在用 INACTIVE-停用 MAINTENANCE-维护中 RETIRED-报废")
    private String deviceStatus;

    @Schema(description = "是否监控：true-监控 false-不监控")
    private Boolean isMonitored;

    @Schema(description = "扩展属性（JSON）")
    private Map<String, Object> extraProperties;

    @Schema(description = "备注信息")
    private String remarks;

    @Schema(description = "设备位置信息")
    private DeviceLocationInfo location;

    @Schema(description = "设备网络配置信息")
    private DeviceNetworkInfo network;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;
}
