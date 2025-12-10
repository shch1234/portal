package com.weili.iot_portal.web.devicebase;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicebase.DeviceBaseInfoListVO;
import com.weili.iot_portal.domain.devicebase.DeviceBaseInfoVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceBaseInfoCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceBaseInfoQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceBaseInfoUpdateReq;
import com.weili.iot_portal.service.devicebase.DeviceBaseInfoService;
import com.weili.iot_portal.web.annotation.PermRequired;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 设备基础信息管理（主数据模块）
 */
@Tag(name = "设备基础数据-设备基础信息")
@RestController
@RequestMapping("/device-base/device-info")
@RequiredArgsConstructor
public class DeviceBaseInfoController {

    private final DeviceBaseInfoService deviceBaseInfoService;

    @PostMapping
    @PermRequired(permission = "device-base:info:create")
    @Operation(summary = "创建设备基础信息")
    public CommonResult<DeviceBaseInfoVO> create(@Valid @RequestBody DeviceBaseInfoCreateReq request) {
        return CommonResult.success(deviceBaseInfoService.create(request));
    }

    @PutMapping("/{id}")
    @PermRequired(permission = "device-base:info:update")
    @Operation(summary = "更新设备基础信息")
    public CommonResult<DeviceBaseInfoVO> update(@PathVariable("id") String id,
                                                 @Valid @RequestBody DeviceBaseInfoUpdateReq request) {
        request.setId(id);
        return CommonResult.success(deviceBaseInfoService.update(request));
    }

    @GetMapping("/{id}/{factoryId}")
    @Operation(summary = "根据ID查询设备基础信息")
    public CommonResult<DeviceBaseInfoVO> get(@PathVariable("id") String id, @PathVariable("factoryId") String factoryId) {
        return CommonResult.success(deviceBaseInfoService.getById(factoryId, id));
    }

    @PostMapping("/page")
    @Operation(summary = "分页查询设备基础信息")
    public CommonResult<PageResult<DeviceBaseInfoVO>> page(@Valid @RequestBody DeviceBaseInfoQueryReq request) {
        return CommonResult.success(deviceBaseInfoService.page(request.getOrgFactoryId(), request));
    }

    @PostMapping("/list")
    @Operation(summary = "分页查询设备列表（含列表展示字段）")
    public CommonResult<PageResult<DeviceBaseInfoListVO>> list(@Valid @RequestBody DeviceBaseInfoQueryReq request) {
        return CommonResult.success(deviceBaseInfoService.list(request.getOrgFactoryId(), request));
    }

    @DeleteMapping("/{id}")
    @PermRequired(permission = "device-base:info:delete")
    @Operation(summary = "删除设备基础信息")
    public CommonResult<Boolean> delete(@PathVariable("id") String id) {
        return CommonResult.success(deviceBaseInfoService.delete(id));
    }
}

