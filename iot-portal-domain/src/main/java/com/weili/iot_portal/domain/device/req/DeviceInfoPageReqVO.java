package com.weili.iot_portal.domain.device.req;

import com.weili.basic.common.model.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author luying
 * @className DeviceInfoPageReqVO
 * @description 设备列表查询请求参数
 * @date 2025-12-11 10:41
 **/
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceInfoPageReqVO extends PageParam {

    /**
     * 设备编号（威力编号）
     */
    @Schema(description = "设备编号（威力编号）", example = "WL-S21-JQ001")
    private String deviceNo;

    /**
     * 设备类型（机床、机器人、plc）
     */
    @Schema(description = "设备类型（机床、机器人、plc）", example = "PLC", allowableValues = {"MACHINE_TOOL", "ROBOT", "PLC"})
    private String deviceType;

    /**
     * 设备子类型（如五轴铣车中心、立式加工中心等）
     */
    @Schema(description = "设备子类型（如五轴铣车中心、立式加工中心等）", example = "CNC_5AXIS")
    private String deviceSubType;

    /**
     * 车间
     */
    @Schema(description = "车间名称", example = "2199")
    private String workshop;

    /**
     * 状态（加工中、待机、关机、故障）
     */
    @Schema(description = "设备状态（加工中、待机、关机、故障）", example = "加工中", allowableValues = {"加工中", "待机", "关机", "故障"})
    private String status;

    /**
     * 是否有报警（true/false）
     */
    @Schema(description = "是否有报警，true表示有报警，false表示无报警", example = "true")
    private Boolean hasAlarm;

    /**
     * 设备型号
     */
    @Schema(description = "设备型号")
    private String model;

    /**
     * 规格型号
     */
    @Schema(description = "规格型号")
    private String specification;
}
