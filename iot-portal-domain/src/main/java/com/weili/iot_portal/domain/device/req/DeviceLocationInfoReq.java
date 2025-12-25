package com.weili.iot_portal.domain.device.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备位置信息（嵌套对象）
 */
@Data
@Schema(description = "设备位置信息")
public class DeviceLocationInfoReq {
    @Schema(description = "位置编码（物理位置编码）", example = "A区-1层-01号位")
    @Size(max = 100, message = "位置编码长度不能超过100个字符")
    private String locationCode;

    @Schema(description = "位置描述", example = "第一车间A区1层01号位置")
    @Size(max = 500, message = "位置描述长度不能超过500个字符")
    private String locationDescription;

    @Schema(description = "楼层号", example = "1")
    private Integer floorNo;

    @Schema(description = "区域编码", example = "AREA_A")
    @Size(max = 50, message = "区域编码长度不能超过50个字符")
    private String areaCode;

    @Schema(description = "经度", example = "120.1234567")
    private Double longitude;

    @Schema(description = "纬度", example = "30.1234567")
    private Double latitude;

    @Schema(description = "生效开始时间yyyy-mm-dd")
    private LocalDateTime effectiveStart;

    @Schema(description = "生效结束时间yyyy-mm-dd")
    private LocalDateTime effectiveEnd;

    @Schema(description = "位置说明", example = "设备搬迁、位置调整等")
    private String description;
}

