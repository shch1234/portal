package com.weili.iot_portal.web.devicemng;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceLocationDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceNetworkConfigDO;
import com.weili.iot_portal.domain.devicebase.req.DeviceInfoBasePageReqVO;
import com.weili.iot_portal.domain.devicebase.req.DeviceInfoSaveReqVO;
import com.weili.iot_portal.domain.devicebase.resp.DeviceInfoRespVO;
import com.weili.iot_portal.service.devicebase.IDeviceInfoBizService;
import com.weili.iot_portal.service.devicebase.IDeviceLocationBizService;
import com.weili.iot_portal.service.devicebase.IDeviceNetworkConfigBizService;
import com.weili.iot_portal.web.annotation.PermRequired;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备信息管理 Controller
 */
@Tag(name = "设备信息管理")
@RestController
@RequestMapping("/device-mgmt/device-info")
@Validated
public class DeviceInfoController {

    @Resource
    private IDeviceInfoBizService deviceInfoBizService;

    @Resource
    private IDeviceLocationBizService deviceLocationBizService;

    @Resource
    private IDeviceNetworkConfigBizService deviceNetworkConfigBizService;

    @PostMapping("/create")
    @Operation(summary = "创建设备信息")
    @PermRequired(permission = "device-mgmt:device-info:create")
    public CommonResult<String> createDeviceInfo(@Valid @RequestBody DeviceInfoSaveReqVO createReqVO) {
        String deviceInfoId = deviceInfoBizService.createDeviceInfo(createReqVO);
        return CommonResult.success(deviceInfoId);
    }

    @PutMapping("/update")
    @Operation(summary = "更新设备信息")
    @PermRequired(permission = "device-mgmt:device-info:update")
    public CommonResult<Boolean> updateDeviceInfo(@Valid @RequestBody DeviceInfoSaveReqVO updateReqVO) {
        deviceInfoBizService.updateDeviceInfo(updateReqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除设备信息")
    @Parameter(name = "id", description = "设备信息ID", required = true, example = "123456789")
    @PermRequired(permission = "device-mgmt:device-info:delete")
    public CommonResult<Boolean> deleteDeviceInfo(@RequestParam("id") String id) {
        deviceInfoBizService.deleteDeviceInfo(id);
        return CommonResult.success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取设备信息详情（包含位置和网络配置）")
    @Parameter(name = "id", description = "设备信息ID", required = true, example = "123456789")
    public CommonResult<DeviceInfoRespVO> getDeviceInfo(@RequestParam("id") String id) {
        DeviceInfoRespVO deviceInfo = deviceInfoBizService.getDeviceInfoWithDetails(id);
        return CommonResult.success(deviceInfo);
    }

    @GetMapping("/get-by-code")
    @Operation(summary = "根据设备编号获取设备信息")
    @Parameter(name = "deviceCode", description = "设备编号", required = true, example = "WL-S21-JQ001")
    public CommonResult<DeviceInfoRespVO> getDeviceInfoByCode(@RequestParam("deviceCode") String deviceCode) {
        DeviceInfoDO deviceInfo = deviceInfoBizService.getDeviceInfoByCode(deviceCode);
        return CommonResult.success(BeanUtils.toBean(deviceInfo, DeviceInfoRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询设备信息")
    public CommonResult<PageResult<DeviceInfoRespVO>> getDeviceInfoPage(@Valid DeviceInfoBasePageReqVO pageReqVO) {
        PageResult<DeviceInfoDO> pageResult = deviceInfoBizService.getDeviceInfoPage(pageReqVO);
        return CommonResult.success(BeanUtils.toBean(pageResult, DeviceInfoRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获取所有设备信息列表")
    public CommonResult<List<DeviceInfoRespVO>> getDeviceInfoList() {
        List<DeviceInfoDO> list = deviceInfoBizService.getDeviceInfoList();
        return CommonResult.success(BeanUtils.toBean(list, DeviceInfoRespVO.class));
    }

    // ========== 设备位置管理子接口 ==========

    @PutMapping("/{deviceInfoId}/location")
    @Operation(summary = "更新设备位置信息")
    @Parameter(name = "deviceInfoId", description = "设备信息ID", required = true, example = "123456789")
    @PermRequired(permission = "device-mgmt:device-info:update")
    public CommonResult<Boolean> updateDeviceLocation(
            @PathVariable("deviceInfoId") String deviceInfoId,
            @Valid @RequestBody DeviceInfoSaveReqVO.DeviceLocationInfo locationInfo) {
        deviceInfoBizService.updateDeviceLocation(deviceInfoId, locationInfo);
        return CommonResult.success(true);
    }

    @GetMapping("/{deviceInfoId}/location")
    @Operation(summary = "获取设备位置信息")
    @Parameter(name = "deviceInfoId", description = "设备信息ID", required = true, example = "123456789")
    public CommonResult<DeviceInfoRespVO.DeviceLocationInfo> getDeviceLocation(@PathVariable("deviceInfoId") String deviceInfoId) {
        DeviceLocationDO deviceLocation = deviceLocationBizService.getDeviceLocationByDeviceId(deviceInfoId);
        DeviceInfoRespVO.DeviceLocationInfo locationInfo = BeanUtils.toBean(deviceLocation, DeviceInfoRespVO.DeviceLocationInfo.class);
        return CommonResult.success(locationInfo);
    }

    // ========== 设备网络配置管理子接口 ==========

    @PutMapping("/{deviceInfoId}/network")
    @Operation(summary = "更新设备网络配置")
    @Parameter(name = "deviceInfoId", description = "设备信息ID", required = true, example = "123456789")
    @PermRequired(permission = "device-mgmt:device-info:update")
    public CommonResult<Boolean> updateDeviceNetworkConfig(
            @PathVariable("deviceInfoId") String deviceInfoId,
            @Valid @RequestBody DeviceInfoSaveReqVO.DeviceNetworkInfo networkInfo) {
        deviceInfoBizService.updateDeviceNetworkConfig(deviceInfoId, networkInfo);
        return CommonResult.success(true);
    }

    @GetMapping("/{deviceInfoId}/network")
    @Operation(summary = "获取设备网络配置")
    @Parameter(name = "deviceInfoId", description = "设备信息ID", required = true, example = "123456789")
    public CommonResult<DeviceInfoRespVO.DeviceNetworkInfo> getDeviceNetworkConfig(@PathVariable("deviceInfoId") String deviceInfoId) {
        DeviceNetworkConfigDO deviceNetworkConfig = deviceNetworkConfigBizService.getDeviceNetworkConfigByDeviceId(deviceInfoId);
        DeviceInfoRespVO.DeviceNetworkInfo networkInfo = BeanUtils.toBean(deviceNetworkConfig, DeviceInfoRespVO.DeviceNetworkInfo.class);
        return CommonResult.success(networkInfo);
    }
}

