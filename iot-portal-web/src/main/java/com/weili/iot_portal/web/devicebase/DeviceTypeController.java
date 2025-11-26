package com.weili.iot_portal.web.devicebase;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicebase.DeviceTypeVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceTypeCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceTypeQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceTypeUpdateReq;
import com.weili.iot_portal.service.devicebase.DeviceTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 设备类型管理
 */
@Tag(name = "设备基础数据-设备类型")
@RestController
@RequestMapping("/device-base/device-types")
@RequiredArgsConstructor
public class DeviceTypeController {

    private final DeviceTypeService deviceTypeService;

    @PostMapping
    @PreAuthorize("hasPermission(null, 'device:type:create')")
    @ApiInterceptor
    @Operation(summary = "创建设备类型")
    public CommonResult<DeviceTypeVO> create(@Valid @RequestBody DeviceTypeCreateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        DeviceTypeVO result = deviceTypeService.create(tenantId, request);
        return CommonResult.success(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'device:type:update')")
    @ApiInterceptor
    @Operation(summary = "更新设备类型")
    public CommonResult<DeviceTypeVO> update(@PathVariable("id") String id,
                                             @Valid @RequestBody DeviceTypeUpdateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        request.setId(id);
        DeviceTypeVO result = deviceTypeService.update(tenantId,  request);
        return CommonResult.success(result);
    }

    @GetMapping("/{id}")
    @ApiInterceptor
    @Operation(summary = "查询设备类型详情")
    public CommonResult<DeviceTypeVO> get(@PathVariable("id") String id) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        DeviceTypeVO result = deviceTypeService.get(tenantId, id);
        return CommonResult.success(result);
    }

    @PostMapping("/page")
    @ApiInterceptor
    @Operation(summary = "分页查询设备类型")
    public CommonResult<PageResult<DeviceTypeVO>> page(@Valid @RequestBody DeviceTypeQueryReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        PageResult<DeviceTypeVO> result = deviceTypeService.page(tenantId, request);
        return CommonResult.success(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'device:type:delete')")
    @ApiInterceptor
    @Operation(summary = "删除设备类型")
    public CommonResult<Boolean> delete(@PathVariable("id") String id) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        boolean success = deviceTypeService.delete(tenantId, id);
        return CommonResult.success(success);
    }
}

