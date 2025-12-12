package com.weili.iot_portal.domain.devicebase.req;

import com.weili.basic.common.model.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备信息分页查询 Request VO
 */
@Schema(description = "设备信息分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceInfoBasePageReqVO extends PageParam {

    @Schema(description = "设备编号（模糊匹配）", example = "WL-S21")
    @Size(max = 100, message = "设备编号长度不能超过100个字符")
    private String deviceCode;

    @Schema(description = "设备名称（模糊匹配）", example = "加工中心")
    @Size(max = 255, message = "设备名称长度不能超过255个字符")
    private String deviceName;

    @Schema(description = "设备类型编码", example = "CNC_5AXIS")
    @Size(max = 100, message = "设备类型编码长度不能超过100个字符")
    private String deviceTypeCode;

    @Schema(description = "设备型号ID", example = "123456789")
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
}

