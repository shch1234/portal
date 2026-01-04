package com.weili.iot_portal.web.alarm;

import com.weili.basic.common.enums.ErrorCodeEnum;
import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.domain.device.req.DeviceAlarmHistoryQueryReqVO;
import com.weili.iot_portal.domain.device.resp.AlarmHistoryRespVO;
import com.weili.iot_portal.domain.device.resp.DeviceAlarmHistoryRespVO;
import com.weili.iot_portal.service.device.IDeviceAlarmHistoryBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author luying
 * @className AlarmManageController
 * @description 报警管理
 * @date 2025-12-25 11:03
 **/
@Tag(name = "报警管理")
@RestController
@RequestMapping("/alarm-mgmt")
@Validated
public class AlarmManageController {
    @Resource
    private IDeviceAlarmHistoryBizService deviceAlarmHistoryBizService;

    @PostMapping("/device-history")
    @Operation(summary = "查询设备的报警记录（包含当前的报警）")
    public CommonResult<DeviceAlarmHistoryRespVO> getDeviceAlarmHistory(@Valid @RequestBody DeviceAlarmHistoryQueryReqVO queryReqVO) {
        if (queryReqVO.getDeviceId() == null) {
            throw new IotPortalException(ErrorCodeEnum.PARAMS_ILLEGAL, "设备ID不能为空");
        }
        DeviceAlarmHistoryRespVO result = deviceAlarmHistoryBizService.getDeviceAlarmHistory(queryReqVO);
        return CommonResult.success(result);
    }

    @PostMapping("/list")
    @Operation(summary = "查询报警管理列表")
    public CommonResult<PageResult<AlarmHistoryRespVO>> queryAlarmManageList(@Valid @RequestBody DeviceAlarmHistoryQueryReqVO queryReqVO) {
        PageResult<AlarmHistoryRespVO> result = deviceAlarmHistoryBizService.queryAlarmManageList(queryReqVO);
        return CommonResult.success(result);
    }
}
