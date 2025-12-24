package com.weili.iot_portal.web.device;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.domain.device.req.DeviceAxisQueryReqVO;
import com.weili.iot_portal.domain.device.req.DeviceStateSummaryQueryReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceAxisRespVO;
import com.weili.iot_portal.domain.device.resp.DeviceStateSummaryRespVO;
import com.weili.iot_portal.service.device.IDeviceAxisBizService;
import com.weili.iot_portal.service.device.IDeviceStateSummaryBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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


}
