package com.weili.iot_portal.web.device;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.domain.device.req.DeviceInfoBasePageReqVO;
import com.weili.iot_portal.domain.device.req.DeviceInfoSaveReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceInfoOptionsRespVO;
import com.weili.iot_portal.domain.device.resp.DeviceInfoRespVO;
import com.weili.iot_portal.service.device.IDeviceInfoBizService;
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

    @GetMapping("/options")
    @Operation(summary = "获取设备信息选项数据（用于新增/编辑页面）", 
               description = "一次性返回设备类型、组织关系（厂区/车间/产线）、设备型号等下拉选项数据，减少前端接口调用")
    public CommonResult<DeviceInfoOptionsRespVO> getDeviceInfoOptions() {
        DeviceInfoOptionsRespVO options = deviceInfoBizService.getDeviceInfoOptions();
        return CommonResult.success(options);
    }
}

