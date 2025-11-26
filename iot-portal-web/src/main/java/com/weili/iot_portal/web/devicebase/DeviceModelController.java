package com.weili.iot_portal.web.devicebase;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicebase.DeviceModelVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceModelCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceModelQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceModelUpdateReq;
import com.weili.iot_portal.service.devicebase.DeviceModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 设备型号管理
 */
@Tag(name = "设备基础数据-设备型号")
@RestController
@RequestMapping("/device-base/device-models")
@RequiredArgsConstructor
public class DeviceModelController {

    private final DeviceModelService deviceModelService;

    @PostMapping
    @PreAuthorize("hasPermission(null, 'device:model:create')")
    @ApiInterceptor
    @Operation(summary = "创建设备型号")
    public CommonResult<DeviceModelVO> create(@Valid @RequestBody DeviceModelCreateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(deviceModelService.create(tenantId,  request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'device:model:update')")
    @ApiInterceptor
    @Operation(summary = "更新设备型号")
    public CommonResult<DeviceModelVO> update(@PathVariable("id") String id,
                                              @Valid @RequestBody DeviceModelUpdateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        request.setId(id);
        return CommonResult.success(deviceModelService.update(tenantId, request));
    }

    @GetMapping("/{id}")
    @ApiInterceptor
    @Operation(summary = "查询设备型号详情")
    public CommonResult<DeviceModelVO> get(@PathVariable("id") String id) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(deviceModelService.get(tenantId, id));
    }

    @PostMapping("/page")
    @ApiInterceptor
    @Operation(summary = "分页查询设备型号")
    public CommonResult<PageResult<DeviceModelVO>> page(@Valid @RequestBody DeviceModelQueryReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(deviceModelService.page(tenantId, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'device:model:delete')")
    @ApiInterceptor
    @Operation(summary = "删除设备型号")
    public CommonResult<Boolean> delete(@PathVariable("id") String id) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(deviceModelService.delete(tenantId, id));
    }
}

