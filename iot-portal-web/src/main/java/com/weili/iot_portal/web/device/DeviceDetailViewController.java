package com.weili.iot_portal.web.device;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.device.req.DeviceAxisQueryReqVO;
import com.weili.iot_portal.domain.device.req.DeviceStateSummaryQueryReqVO;
import com.weili.iot_portal.domain.device.req.DeviceToolCompensationQueryReqVO;
import com.weili.iot_portal.domain.device.req.DeviceToolRecordQueryReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceAxisRespVO;
import com.weili.iot_portal.domain.device.resp.DeviceStateSummaryRespVO;
import com.weili.iot_portal.domain.device.resp.DeviceToolCompensationRespVO;
import com.weili.iot_portal.domain.device.resp.DeviceToolRecordRespVO;
import com.weili.iot_portal.service.device.IDeviceAxisBizService;
import com.weili.iot_portal.service.device.IDeviceStateSummaryBizService;
import com.weili.iot_portal.service.device.IDeviceToolBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author luying
 * @className DeviceDetailViewController
 * @description
 * @date 2025-12-23 13:39
 **/
@Tag(name = "设备详情视图")
@RestController
@RequestMapping("/device-mgmt/detail-view")
@Validated
public class DeviceDetailViewController {
    @Resource
    private IDeviceAxisBizService deviceAxisBizService;
    @Resource
    private IDeviceStateSummaryBizService deviceStateSummaryBizService;
    @Resource
    private IDeviceToolBizService deviceToolBizService;

    @GetMapping("/state-summary")
    @Operation(summary = "获取设备状态统计（饼图+时间轴）")
    public CommonResult<DeviceStateSummaryRespVO> getDeviceStateSummary(@Valid DeviceStateSummaryQueryReqVO queryReqVO) {
        DeviceStateSummaryRespVO result = deviceStateSummaryBizService.getDeviceStateSummary(queryReqVO);
        return CommonResult.success(result);
    }

    @GetMapping("/axis-info")
    @Operation(summary = "获取设备轴标签信息（主轴曲线+轴坐标）")
    public CommonResult<DeviceAxisRespVO> getDeviceAxisInfo(@Valid DeviceAxisQueryReqVO queryReqVO) {
        DeviceAxisRespVO result = deviceAxisBizService.getDeviceAxisInfo(queryReqVO);
        return CommonResult.success(result);
    }

    @GetMapping("/tool-compensation")
    @Operation(summary = "获取设备刀具补偿信息")
    public CommonResult<PageResult<DeviceToolCompensationRespVO>> getDeviceToolCompensation(@Valid DeviceToolCompensationQueryReqVO queryReqVO) {
        PageResult<DeviceToolCompensationRespVO> result = deviceToolBizService.getDeviceToolCompensation(queryReqVO);
        return CommonResult.success(result);
    }

    @GetMapping("/tool-records")
    @Operation(summary = "获取设备刀具使用记录列表")
    public CommonResult<PageResult<DeviceToolRecordRespVO>> getDeviceToolRecords(@Valid DeviceToolRecordQueryReqVO queryReqVO) {
        PageResult<DeviceToolRecordRespVO> result = deviceToolBizService.getDeviceToolRecords(queryReqVO);
        return CommonResult.success(result);
    }

    @GetMapping("/tool-current")
    @Operation(summary = "获取设备刀具实时使用记录", description = "当前刀具号、刀套号、刀补值")
    public CommonResult<DeviceToolRecordRespVO> getDeviceToolRecords(@RequestParam("deviceId") Long id) {
        DeviceToolRecordRespVO result = deviceToolBizService.getCurrentToolRecord(id);
        return CommonResult.success(result);
    }
}
