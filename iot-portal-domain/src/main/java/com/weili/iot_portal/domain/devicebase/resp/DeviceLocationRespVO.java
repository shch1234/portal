package com.weili.iot_portal.domain.devicebase.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备位置 Response VO
 */
@Schema(description = "设备位置 Response VO")
@Data
public class DeviceLocationRespVO {

    @Schema(description = "设备位置ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "设备信息ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceInfoId;

    @Schema(description = "所属厂区ID")
    private String orgFactoryId;

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

    @Schema(description = "生效开始时间戳（秒）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long effectiveStartTs;

    @Schema(description = "生效结束时间戳（秒）")
    private Long effectiveEndTs;

    @Schema(description = "是否当前生效")
    private Boolean active;

    @Schema(description = "位置说明")
    private String description;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;
}

