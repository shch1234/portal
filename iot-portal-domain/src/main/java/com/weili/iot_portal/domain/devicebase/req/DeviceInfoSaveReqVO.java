package com.weili.iot_portal.domain.devicebase.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 设备信息创建/修改 Request VO
 * 包含设备基本信息、位置信息和网络配置信息
 */
@Schema(description = "设备信息创建/修改 Request VO")
@Data
public class DeviceInfoSaveReqVO {

    @Schema(description = "设备信息ID", example = "123456789")
    private String id;

    @Schema(description = "ThingsBoard 设备ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "550e8400-e29b-41d4-a716-446655440000")
    @NotBlank(message = "ThingsBoard设备ID不能为空")
    @Size(max = 36, message = "ThingsBoard设备ID长度不能超过36个字符")
    private String tbDeviceId;

    @Schema(description = "设备编号（威力编号，租户内唯一）", requiredMode = Schema.RequiredMode.REQUIRED, example = "WL-S21-JQ001")
    @NotBlank(message = "设备编号不能为空")
    @Size(max = 100, message = "设备编号长度不能超过100个字符")
    private String deviceCode;

    @Schema(description = "设备名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "五轴加工中心-001")
    @NotBlank(message = "设备名称不能为空")
    @Size(max = 255, message = "设备名称长度不能超过255个字符")
    private String deviceName;

    @Schema(description = "设备类型编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "CNC_5AXIS")
    @NotBlank(message = "设备类型编码不能为空")
    @Size(max = 100, message = "设备类型编码长度不能超过100个字符")
    private String deviceTypeCode;

    @Schema(description = "设备型号ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456789")
    @NotBlank(message = "设备型号ID不能为空")
    private String deviceModelId;

    @Schema(description = "所属厂区ID", example = "123456789")
    private String orgFactoryId;

    @Schema(description = "所属车间ID", example = "123456789")
    private String orgWorkshopId;

    @Schema(description = "所属产线ID", example = "123456789")
    private String orgProductionLineId;

    @Schema(description = "设备状态：ACTIVE-在用 INACTIVE-停用 MAINTENANCE-维护中 RETIRED-报废", example = "ACTIVE")
    private String deviceStatus;

    @Schema(description = "是否监控：true-监控 false-不监控", example = "true")
    private Boolean isMonitored;

    @Schema(description = "扩展属性（JSON）：采购信息、资产编号、序列号等")
    private Map<String, Object> extraProperties;

    @Schema(description = "备注信息")
    private String remarks;

    @Schema(description = "设备位置信息（可选，创建设备时可同时创建位置信息）")
    @Valid
    private DeviceLocationInfo location;

    @Schema(description = "设备网络配置信息（可选，创建设备时可同时创建网络配置）")
    @Valid
    private DeviceNetworkInfo network;

    /**
     * 设备位置信息（嵌套对象）
     */
    @Data
    @Schema(description = "设备位置信息")
    public static class DeviceLocationInfo {
        @Schema(description = "位置编码（物理位置编码）", example = "A区-1层-01号位")
        @Size(max = 100, message = "位置编码长度不能超过100个字符")
        private String locationCode;

        @Schema(description = "位置描述", example = "第一车间A区1层01号位置")
        @Size(max = 500, message = "位置描述长度不能超过500个字符")
        private String locationDescription;

        @Schema(description = "坐标信息（JSON）：经纬度、楼层、区域、位置编号等")
        private Map<String, Object> coordinates;

        @Schema(description = "楼层号", example = "1")
        private Integer floorNo;

        @Schema(description = "区域编码", example = "AREA_A")
        @Size(max = 50, message = "区域编码长度不能超过50个字符")
        private String areaCode;

        @Schema(description = "经度", example = "120.1234567")
        private Double longitude;

        @Schema(description = "纬度", example = "30.1234567")
        private Double latitude;

        @Schema(description = "生效开始时间戳（秒，Unix时间戳）", example = "1704067200")
        private Long effectiveStartTs;

        @Schema(description = "生效结束时间戳（秒，Unix时间戳，NULL表示当前生效）", example = "1735689600")
        private Long effectiveEndTs;

        @Schema(description = "位置说明", example = "设备搬迁、位置调整等")
        private String description;
    }

    /**
     * 设备网络配置信息（嵌套对象）
     */
    @Data
    @Schema(description = "设备网络配置信息")
    public static class DeviceNetworkInfo {
        @Schema(description = "IP地址", example = "192.168.1.100")
        @Size(max = 50, message = "IP地址长度不能超过50个字符")
        private String ipAddress;

        @Schema(description = "端口号", example = "8080")
        private Integer port;

        @Schema(description = "MAC地址", example = "00:11:22:33:44:55")
        @Size(max = 50, message = "MAC地址长度不能超过50个字符")
        private String macAddress;

        @Schema(description = "网关地址", example = "192.168.1.1")
        @Size(max = 50, message = "网关地址长度不能超过50个字符")
        private String gateway;

        @Schema(description = "子网掩码", example = "255.255.255.0")
        @Size(max = 50, message = "子网掩码长度不能超过50个字符")
        private String subnetMask;

        @Schema(description = "通信协议：MQTT、MODBUS、OPC_UA、FOCAS等", example = "MQTT")
        @Size(max = 50, message = "通信协议长度不能超过50个字符")
        private String protocol;

        @Schema(description = "连接参数（JSON）：超时、重试、轮询间隔等")
        private Map<String, Object> connectionParams;

        @Schema(description = "生效开始时间戳（秒，Unix时间戳）", example = "1704067200")
        private Long effectiveStartTs;

        @Schema(description = "生效结束时间戳（秒，Unix时间戳，NULL表示当前生效）", example = "1735689600")
        private Long effectiveEndTs;

        @Schema(description = "配置说明", example = "IP地址变更、网络调整等")
        private String description;
    }
}
