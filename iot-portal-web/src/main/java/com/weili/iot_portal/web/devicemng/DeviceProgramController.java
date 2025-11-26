package com.weili.iot_portal.web.devicemng;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.common.enums.ProgramCodeType;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicemng.ProgramCodeVO;
import com.weili.iot_portal.domain.devicemng.ProgramInfoVO;
import com.weili.iot_portal.service.devicemng.ProgramInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "设备管理-程序信息")
@RestController
@RequestMapping("/device-mgmt/devices/{deviceId}/program")
@RequiredArgsConstructor
public class DeviceProgramController {

    private final ProgramInfoService programInfoService;

    @GetMapping("/info")
    @Operation(summary = "查询当前程序信息")
    public CommonResult<ProgramInfoVO> getProgramInfo(@PathVariable String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(programInfoService.getCurrentProgramInfo(tenantId, factoryId, deviceId));
    }

    @GetMapping("/g-code")
    @Operation(summary = "查询G代码内容")
    public CommonResult<ProgramCodeVO> getGCode(@PathVariable String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(programInfoService.getProgramCode(tenantId, factoryId, deviceId, ProgramCodeType.G_CODE));
    }

    @GetMapping("/m-code")
    @Operation(summary = "查询M代码内容")
    public CommonResult<ProgramCodeVO> getMCode(@PathVariable String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(programInfoService.getProgramCode(tenantId, factoryId, deviceId, ProgramCodeType.M_CODE));
    }
}

