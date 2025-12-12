package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 设备信息选项数据 Response VO
 * 用于设备新增/编辑页面，
 */
@Schema(description = "设备信息选项数据 Response VO")
@Data
public class DeviceInfoOptionsRespVO {

    @Schema(description = "设备类型列表（仅启用状态）")
    private List<DeviceTypeRelationRespVO> deviceTypes;

    @Schema(description = "厂区列表（仅启用状态）")
    private List<DeviceOrgRelationRespVO> factories;

    @Schema(description = "车间列表（仅启用状态）")
    private List<DeviceOrgRelationRespVO> workshops;

    @Schema(description = "产线列表（仅启用状态）")
    private List<DeviceOrgRelationRespVO> productionLines;

    @Schema(description = "设备型号列表（仅启用状态）")
    private List<DeviceModelRespVO> deviceModels;
}

