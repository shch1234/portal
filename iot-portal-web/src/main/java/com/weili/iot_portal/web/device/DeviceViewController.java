package com.weili.iot_portal.web.device;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.device.req.*;
import com.weili.iot_portal.domain.device.resp.*;
import com.weili.iot_portal.service.device.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author luying
 * @className DeviceViewController
 * @description
 * @date 2025-12-23 13:39
 **/
@Tag(name = "设备详情视图")
@RestController
@RequestMapping("/device-mgmt/detail-view")
@Validated
public class DeviceViewController {
    @Resource
    private IDeviceAxisBizService deviceAxisBizService;
    @Resource
    private IDeviceStateSummaryBizService deviceStateSummaryBizService;
    @Resource
    private IDeviceToolBizService deviceToolBizService;
    @Resource
    private IDeviceProgramBizService deviceProgramBizService;
    @Resource
    private IDeviceProductionSummaryService deviceProductionSummaryService;

    @PostMapping("/state-summary")
    @Operation(summary = "获取设备状态统计（饼图+时间轴）")
    public CommonResult<DeviceStateSummaryRespVO> getDeviceStateSummary(@Valid @RequestBody DeviceStateSummaryQueryReqVO queryReqVO) {
        DeviceStateSummaryRespVO result = deviceStateSummaryBizService.getDeviceStateSummary(queryReqVO);
        return CommonResult.success(result);
    }

    @PostMapping("/axis-info")
    @Operation(summary = "获取设备轴标签信息（主轴曲线+轴坐标）")
    public CommonResult<DeviceAxisRespVO> getDeviceAxisInfo(@Valid @RequestBody DeviceAxisQueryReqVO queryReqVO) {
        DeviceAxisRespVO result = deviceAxisBizService.getDeviceAxisInfo(queryReqVO);
        return CommonResult.success(result);
    }

    @PostMapping("/tool-compensation")
    @Operation(summary = "获取设备刀具补偿信息")
    public CommonResult<PageResult<DeviceToolCompensationRespVO>> getDeviceToolCompensation(@Valid @RequestBody DeviceToolCompensationQueryReqVO queryReqVO) {
        PageResult<DeviceToolCompensationRespVO> result = deviceToolBizService.getDeviceToolCompensation(queryReqVO);
        return CommonResult.success(result);
    }

    @PostMapping("/tool-records")
    @Operation(summary = "获取设备刀具使用记录列表，包括当前的道具记录")
    public CommonResult<DeviceToolRecordRespVO> getDeviceToolRecords(@Valid @RequestBody DeviceToolRecordQueryReqVO queryReqVO) {
        DeviceToolRecordRespVO result = deviceToolBizService.getDeviceToolRecords(queryReqVO);
        return CommonResult.success(result);
    }


    @GetMapping("/program")
    @Operation(summary = "获取设备程序信息")
    public CommonResult<DeviceProgramRespVO> getDeviceProgram(@RequestParam("deviceId") Long deviceId) {
        DeviceProgramRespVO result = deviceProgramBizService.getDeviceProgram(deviceId);
        return CommonResult.success(result);
    }


    @PostMapping("/production-statistics")
    @Operation(summary = "查询设备产量统计",
            description = "查询设备当日加工数量和近一周/近一个月的产量趋势图数据")
    public CommonResult<DeviceProductionStatisticsRespVO> getProductionStatistics(@Valid @RequestBody DeviceProductionStatisticsReqVO reqVO) {
        DeviceProductionStatisticsRespVO statistics = deviceProductionSummaryService.getDeviceProductionStatistics(reqVO);
        return CommonResult.success(statistics);
    }
}
