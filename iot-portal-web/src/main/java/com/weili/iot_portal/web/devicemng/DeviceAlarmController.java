package com.weili.iot_portal.web.devicemng;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceAlarmHistoryDO;
import com.weili.iot_portal.domain.devicemng.DeviceAlarmVO;
import com.weili.iot_portal.service.devicemng.DeviceAlarmQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "设备管理-报警查询")
@RestController
@RequestMapping("/alarm")
@RequiredArgsConstructor
public class DeviceAlarmController {

    private final DeviceAlarmQueryService deviceAlarmQueryService;

    @Operation(summary = "查询当前设备正在发生的报警（未结束）")
    @GetMapping("/active")
    public CommonResult<List<DeviceAlarmVO>> listActive(@RequestParam("factoryId") String factoryId,
                                                        @RequestParam("deviceId") String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        List<DeviceAlarmHistoryDO> list = deviceAlarmQueryService.listActive(tenantId, factoryId, deviceId);
        List<DeviceAlarmVO> result = list.stream().map(this::convert).collect(Collectors.toList());
        return CommonResult.success(result);
    }

    @Operation(summary = "查询设备在时间段内的所有报警（包含已结束/未结束）")
    @GetMapping("/history")
    public CommonResult<List<DeviceAlarmVO>> listHistory(@RequestParam("factoryId") String factoryId,
                                                         @RequestParam("deviceId") String deviceId,
                                                         @RequestParam("startTs") Long startTs,
                                                         @RequestParam("endTs") Long endTs) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        List<DeviceAlarmHistoryDO> list = deviceAlarmQueryService.listByRange(tenantId, factoryId, deviceId, startTs, endTs);
        List<DeviceAlarmVO> result = list.stream().map(this::convert).collect(Collectors.toList());
        return CommonResult.success(result);
    }

    private DeviceAlarmVO convert(DeviceAlarmHistoryDO item) {
        DeviceAlarmVO vo = new DeviceAlarmVO();
        vo.setId(item.getId());
        vo.setTenantId(item.getTenantUuid());
        vo.setFactoryId(item.getOrgFactoryId());
        vo.setDeviceId(item.getDeviceInfoId());
        vo.setAlarmCode(item.getAlarmCode());
        vo.setAlarmText(item.getAlarmText());
        vo.setAlarmLevel(item.getAlarmLevel());
        vo.setStartTs(item.getStartTs());
        vo.setEndTs(item.getEndTs());
        vo.setDurationS(item.getDurationS());
        vo.setProperties(item.getProperties());
        vo.setIsActive(item.getIsActive());
        return vo;
    }
}


