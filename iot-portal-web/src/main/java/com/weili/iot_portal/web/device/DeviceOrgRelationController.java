package com.weili.iot_portal.web.device;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceOrgRelationDO;
import com.weili.iot_portal.domain.device.req.DeviceOrgRelationPageReqVO;
import com.weili.iot_portal.domain.device.req.DeviceOrgRelationSaveReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceOrgRelationRespVO;
import com.weili.iot_portal.domain.device.resp.DeviceOrgRelationSubRespVO;
import com.weili.iot_portal.service.device.IDeviceOrgRelationBizService;
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
 * 设备组织单元管理 Controller
 */
@Tag(name = "设备组织单元管理")
@RestController
@RequestMapping("/device-mgmt/org-relation")
@Validated
public class DeviceOrgRelationController {

    @Resource
    private IDeviceOrgRelationBizService deviceOrgRelationBizService;

    @PostMapping("/create")
    @Operation(summary = "创建设备组织单元")
    @PermRequired(permission = "device-mgmt:device-org-relation:create")
    public CommonResult<Long> createDeviceOrgRelation(@Valid @RequestBody DeviceOrgRelationSaveReqVO createReqVO) {
        Long deviceOrgRelationId = deviceOrgRelationBizService.createDeviceOrgRelation(createReqVO);
        return CommonResult.success(deviceOrgRelationId);
    }

    @PutMapping("/update")
    @Operation(summary = "更新设备组织单元")
    @PermRequired(permission = "device-mgmt:device-org-relation:update")
    public CommonResult<Boolean> updateDeviceOrgRelation(@Valid @RequestBody DeviceOrgRelationSaveReqVO updateReqVO) {
        deviceOrgRelationBizService.updateDeviceOrgRelation(updateReqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除设备组织单元")
    @Parameter(name = "id", description = "设备组织单元ID", required = true)
    @PermRequired(permission = "device-mgmt:device-org-relation:delete")
    public CommonResult<Boolean> deleteDeviceOrgRelation(@RequestParam("id") String id) {
        deviceOrgRelationBizService.deleteDeviceOrgRelation(id);
        return CommonResult.success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取设备组织单元详情")
    @Parameter(name = "id", description = "设备组织单元ID", required = true)
    public CommonResult<DeviceOrgRelationRespVO> getDeviceOrgRelation(@RequestParam("id") String id) {
        DeviceOrgRelationDO deviceOrgRelation = deviceOrgRelationBizService.getDeviceOrgRelation(id);
        DeviceOrgRelationRespVO respVO = BeanUtils.toBean(deviceOrgRelation, DeviceOrgRelationRespVO.class);
        // 转换 orgParentId 从 Long 到 String（用于响应）
        if (deviceOrgRelation.getOrgParentId() != null) {
            respVO.setOrgParentId(String.valueOf(deviceOrgRelation.getOrgParentId()));
        }
        //补充父id（产线需要显示车间ID和产线ID）
        if (deviceOrgRelation.getLevelNo() == 3 && deviceOrgRelation.getOrgParentId() != null) {
            DeviceOrgRelationDO parentOrgRelation = deviceOrgRelationBizService.getDeviceOrgRelation(String.valueOf(deviceOrgRelation.getOrgParentId()));
            if (parentOrgRelation != null && parentOrgRelation.getOrgParentId() != null) {
                respVO.setOrgParentId(parentOrgRelation.getOrgParentId() + "," + deviceOrgRelation.getOrgParentId());
            }
        }
        return CommonResult.success(respVO);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询设备组织单元")
    public CommonResult<PageResult<DeviceOrgRelationRespVO>> getDeviceOrgRelationPage(@Valid DeviceOrgRelationPageReqVO pageReqVO) {
        PageResult<DeviceOrgRelationDO> pageResult = deviceOrgRelationBizService.getDeviceOrgRelationPage(pageReqVO);
        PageResult<DeviceOrgRelationRespVO> result = BeanUtils.toBean(pageResult, DeviceOrgRelationRespVO.class);
        // 转换 orgParentId 从 Long 到 String（用于响应）
        // BeanUtils.toBean 转换 PageResult 时会保持列表顺序，所以可以直接通过索引匹配
        List<DeviceOrgRelationDO> originalList = pageResult.getList();
        List<DeviceOrgRelationRespVO> respList = result.getList();
        for (int i = 0; i < respList.size() && i < originalList.size(); i++) {
            DeviceOrgRelationRespVO respVO = respList.get(i);
            DeviceOrgRelationDO original = originalList.get(i);
            if (original.getOrgParentId() != null) {
                respVO.setOrgParentId(String.valueOf(original.getOrgParentId()));
            }
        }
        return CommonResult.success(result);
    }

    @GetMapping("/options")
    @Operation(summary = "获取组织单元级联树（工厂-车间-产线）")
    public CommonResult<List<DeviceOrgRelationSubRespVO>> getOrgRelationCascadeTree() {
        List<DeviceOrgRelationSubRespVO> tree = deviceOrgRelationBizService.getOrgRelationCascadeTree();
        return CommonResult.success(tree);
    }
}



