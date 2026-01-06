package com.weili.iot_portal.web.device;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceModelDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceTypeRelationDO;
import com.weili.iot_portal.domain.device.req.DeviceModelPageReqVO;
import com.weili.iot_portal.domain.device.req.DeviceModelSaveReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceModelRespVO;
import com.weili.iot_portal.service.device.IDeviceModelBizService;
import com.weili.iot_portal.service.device.IDeviceTypeRelationBizService;
import com.weili.iot_portal.web.annotation.PermRequired;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 设备型号管理 Controller
 */
@Tag(name = "设备型号管理")
@RestController
@RequestMapping("/device-mgmt/model")
@Validated
public class DeviceModelController {

    @Resource
    private IDeviceModelBizService deviceModelBizService;
    @Resource
    private IDeviceTypeRelationBizService deviceTypeRelationBizService;

    @PostMapping("/create")
    @Operation(summary = "创建设备型号")
    @PermRequired(permission = "device-mgmt:device-model:create")
    public CommonResult<Long> createDeviceModel(@Valid @RequestBody DeviceModelSaveReqVO createReqVO) {
        Long deviceModelId = deviceModelBizService.createDeviceModel(createReqVO);
        return CommonResult.success(deviceModelId);
    }

    @PutMapping("/update")
    @Operation(summary = "更新设备型号")
    @PermRequired(permission = "device-mgmt:device-model:update")
    public CommonResult<Boolean> updateDeviceModel(@Valid @RequestBody DeviceModelSaveReqVO updateReqVO) {
        deviceModelBizService.updateDeviceModel(updateReqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除设备型号")
    @Parameter(name = "id", description = "设备型号ID", required = true, example = "123456789")
    @PermRequired(permission = "device-mgmt:device-model:delete")
    public CommonResult<Boolean> deleteDeviceModel(@RequestParam("id") Long id) {
        deviceModelBizService.deleteDeviceModel(id);
        return CommonResult.success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取设备型号详情")
    @Parameter(name = "id", description = "设备型号ID", required = true, example = "123456789")
    public CommonResult<DeviceModelRespVO> getDeviceModel(@RequestParam("id") Long id) {
        DeviceModelDO deviceModel = deviceModelBizService.getDeviceModel(id);
        DeviceModelRespVO deviceModelRespVO = BeanUtils.toBean(deviceModel, DeviceModelRespVO.class);
        //组织父子类型
        String deviceTypeCode = deviceModel.getDeviceTypeCode();
        DeviceTypeRelationDO typeRelationDO = deviceTypeRelationBizService.getDeviceTypeRelationByCode(deviceTypeCode);
        if (typeRelationDO != null) {
            String typeCode = typeRelationDO.getTypeCode();
            String parentTypeCode = typeRelationDO.getParentTypeCode();
            if (StringUtils.isNotBlank(parentTypeCode)) {
                deviceTypeCode = parentTypeCode + "," + typeCode;
            }
        }
        deviceModelRespVO.setDeviceTypeCode(deviceTypeCode);
        return CommonResult.success(deviceModelRespVO);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询设备型号")
    public CommonResult<PageResult<DeviceModelRespVO>> getDeviceModelPage(@Valid DeviceModelPageReqVO pageReqVO) {
        PageResult<DeviceModelDO> pageResult = deviceModelBizService.getDeviceModelPage(pageReqVO);
        return CommonResult.success(BeanUtils.toBean(pageResult, DeviceModelRespVO.class));
    }
}









