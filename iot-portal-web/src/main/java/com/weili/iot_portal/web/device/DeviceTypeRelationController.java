package com.weili.iot_portal.web.device;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceTypeRelationDO;
import com.weili.iot_portal.domain.device.req.DeviceTypeRelationPageReqVO;
import com.weili.iot_portal.domain.device.req.DeviceTypeRelationSaveReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceTypeRelationRespVO;
import com.weili.iot_portal.service.device.IDeviceTypeRelationBizService;
import com.weili.iot_portal.web.annotation.PermRequired;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


/**
 * 设备类型管理 Controller
 */
@Tag(name = "设备类型管理")
@RestController
@RequestMapping("/device-mgmt/type-relation")
@Validated
public class DeviceTypeRelationController {

    @Resource
    private IDeviceTypeRelationBizService deviceTypeRelationBizService;

    @PostMapping("/create")
    @Operation(summary = "创建设备类型")
    @PermRequired(permission = "device-mgmt:device-type-relation:create")
    public CommonResult<Long> createDeviceTypeRelation(@Valid @RequestBody DeviceTypeRelationSaveReqVO createReqVO) {
        Long deviceTypeRelationId = deviceTypeRelationBizService.createDeviceTypeRelation(createReqVO);
        return CommonResult.success(deviceTypeRelationId);
    }

    @PutMapping("/update")
    @Operation(summary = "更新设备类型")
    @PermRequired(permission = "device-mgmt:device-type-relation:update")
    public CommonResult<Boolean> updateDeviceTypeRelation(@Valid @RequestBody DeviceTypeRelationSaveReqVO updateReqVO) {
        deviceTypeRelationBizService.updateDeviceTypeRelation(updateReqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除设备类型")
    @Parameter(name = "id", description = "设备类型ID", required = true, example = "123456789")
    @PermRequired(permission = "device-mgmt:device-type-relation:delete")
    public CommonResult<Boolean> deleteDeviceTypeRelation(@RequestParam("id") Long id) {
        deviceTypeRelationBizService.deleteDeviceTypeRelation(id);
        return CommonResult.success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取设备类型详情")
    @Parameter(name = "id", description = "设备类型ID", required = true, example = "123456789")
    public CommonResult<DeviceTypeRelationRespVO> getDeviceTypeRelation(@RequestParam("id") Long id) {
        DeviceTypeRelationDO deviceTypeRelation = deviceTypeRelationBizService.getDeviceTypeRelation(id);
        return CommonResult.success(BeanUtils.toBean(deviceTypeRelation, DeviceTypeRelationRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询设备类型")
    public CommonResult<PageResult<DeviceTypeRelationRespVO>> getDeviceTypeRelationPage(@Valid DeviceTypeRelationPageReqVO pageReqVO) {

        PageResult<DeviceTypeRelationDO> pageResult = deviceTypeRelationBizService.getDeviceTypeRelationPage(pageReqVO);
        return CommonResult.success(BeanUtils.toBean(pageResult, DeviceTypeRelationRespVO.class));
    }
}



