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

    @Schema(description = "ThingsBoard 设备ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tbDeviceId;

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
    private String deviceModelId;

    @Schema(description = "设备型号名称")
    private String deviceModelName;

    @Schema(description = "制造商")
    private String manufacturer;

    @Schema(description = "所属厂区ID")
    private String orgFactoryId;

    @Schema(description = "所属车间ID")
    private String orgWorkshopId;

    @Schema(description = "所属产线ID")
    private String orgProductionLineId;

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

    /**
     * 设备位置信息
     */
    @Data
    @Schema(description = "设备位置信息")
    public static class DeviceLocationInfo {
        @Schema(description = "位置编码")
        private String locationCode;

        @Schema(description = "位置描述")
        private String locationDescription;

        @Schema(description = "坐标信息（JSON）")
        private Map<String, Object> coordinates;

        @Schema(description = "楼层号")
        private Integer floorNo;

        @Schema(description = "区域编码")
        private String areaCode;

        @Schema(description = "经度")
        private Double longitude;

        @Schema(description = "纬度")
        private Double latitude;

        @Schema(description = "生效开始时间戳（秒）")
        private Long effectiveStartTs;

        @Schema(description = "生效结束时间戳（秒）")
        private Long effectiveEndTs;

        @Schema(description = "位置说明")
        private String description;
    }

    /**
     * 设备网络配置信息
     */
    @Data
    @Schema(description = "设备网络配置信息")
    public static class DeviceNetworkInfo {
        @Schema(description = "IP地址")
        private String ipAddress;

        @Schema(description = "端口号")
        private Integer port;

        @Schema(description = "MAC地址")
        private String macAddress;

        @Schema(description = "网关地址")
        private String gateway;

        @Schema(description = "子网掩码")
        private String subnetMask;

        @Schema(description = "通信协议")
        private String protocol;

        @Schema(description = "连接参数（JSON）")
        private Map<String, Object> connectionParams;

        @Schema(description = "生效开始时间戳（秒）")
        private Long effectiveStartTs;

        @Schema(description = "生效结束时间戳（秒）")
        private Long effectiveEndTs;

        @Schema(description = "配置说明")
        private String description;
    }
}
