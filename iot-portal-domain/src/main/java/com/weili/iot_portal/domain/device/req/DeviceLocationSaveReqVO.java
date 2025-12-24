package com.weili.iot_portal.domain.device.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 设备位置创建/修改 Request VO
 */
@Schema(description = "设备位置创建/修改 Request VO")
@Data
public class DeviceLocationSaveReqVO {

    @Schema(description = "设备位置ID", example = "123456789")
    private Long id;

    @Schema(description = "设备信息ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456789")
    @NotNull
    private Long deviceInfoId;

    @Schema(description = "所属厂区ID", example = "123456789")
    private Long orgFactoryId;

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

    @Schema(description = "生效开始时间戳（秒，Unix时间戳）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1704067200")
    @NotNull(message = "生效开始时间戳不能为空")
    private Long effectiveStartTs;

    @Schema(description = "生效结束时间戳（秒，Unix时间戳，NULL表示当前生效）", example = "1735689600")
    private Long effectiveEndTs;

    @Schema(description = "是否当前生效：true-当前生效 false-历史版本", example = "true")
    private Boolean active;

    @Schema(description = "位置说明", example = "设备搬迁、位置调整等")
    private String description;
}

