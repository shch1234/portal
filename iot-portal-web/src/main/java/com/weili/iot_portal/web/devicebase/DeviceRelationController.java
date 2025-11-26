package com.weili.iot_portal.web.devicebase;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicebase.DeviceRelationVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceRelationCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceRelationQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceRelationUpdateReq;
import com.weili.iot_portal.service.devicebase.DeviceRelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 设备关系控制器
 */
@Tag(name = "设备基础数据-设备关系")
@RestController
@RequestMapping("/device-base/device-relations")
@RequiredArgsConstructor
public class DeviceRelationController {

    private final DeviceRelationService deviceRelationService;

    @PostMapping
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:relation:create')")
    @Operation(summary = "创建设备关系")
    public CommonResult<DeviceRelationVO> create(@Valid @RequestBody DeviceRelationCreateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(deviceRelationService.create(tenantId, request));
    }

    @PutMapping("/{id}")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:relation:update')")
    @Operation(summary = "更新设备关系")
    public CommonResult<DeviceRelationVO> update(@PathVariable String id,
                                                 @Valid @RequestBody DeviceRelationUpdateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        request.setId(id);
        return CommonResult.success(deviceRelationService.update(tenantId,request));
    }

    @GetMapping("/{id}")
    @ApiInterceptor
    @Operation(summary = "查询设备关系详情")
    public CommonResult<DeviceRelationVO> get(@PathVariable String id) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(deviceRelationService.get(tenantId, id));
    }

    @PostMapping("/page")
    @ApiInterceptor
    @Operation(summary = "分页查询设备关系")
    public CommonResult<PageResult<DeviceRelationVO>> page(@Valid @RequestBody DeviceRelationQueryReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(deviceRelationService.page(tenantId, request));
    }

    @DeleteMapping("/{id}")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:relation:delete')")
    @Operation(summary = "删除设备关系")
    public CommonResult<Boolean> delete(@PathVariable String id) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(deviceRelationService.delete(tenantId, id));
    }
}

