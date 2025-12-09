package com.weili.iot_portal.web.devicemng;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.dal.dataobject.devicemng.ToolCompensationDO;
import com.weili.iot_portal.domain.devicemng.ToolCompensationVO;
import com.weili.iot_portal.service.devicemng.ToolCompensationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "设备管理-刀补补偿查询")
@RestController
@RequestMapping("/tool/compensation")
@RequiredArgsConstructor
public class ToolCompensationController {

    private final ToolCompensationQueryService toolCompensationQueryService;

    @Operation(summary = "查询设备的有效刀补列表")
    @GetMapping("/active")
    public CommonResult<List<ToolCompensationVO>> listActive(@RequestParam("factoryId") String factoryId,
                                                             @RequestParam("deviceId") String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        List<ToolCompensationDO> list = toolCompensationQueryService.listActive(tenantId, factoryId, deviceId);
        List<ToolCompensationVO> result = list.stream().map(this::convert).collect(Collectors.toList());
        return CommonResult.success(result);
    }

    private ToolCompensationVO convert(ToolCompensationDO item) {
        ToolCompensationVO vo = new ToolCompensationVO();
        vo.setId(item.getId());
        vo.setTenantId(item.getTenantUuid());
        vo.setFactoryId(item.getOrgFactoryId());
        vo.setDeviceId(item.getDeviceInfoId());
        vo.setToolHolderNo(item.getToolHolderNo());
        vo.setVersion(item.getVersion());
        vo.setStartTs(item.getStartTs());
        vo.setEndTs(item.getEndTs());
        vo.setCompValue(item.getCompValueJson());
        return vo;
    }
}


