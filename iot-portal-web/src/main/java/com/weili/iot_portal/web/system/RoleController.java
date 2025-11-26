package com.weili.iot_portal.web.system;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.dal.dataobject.system.RoleDO;
import com.weili.iot_portal.domain.permission.RolePageReqVO;
import com.weili.iot_portal.domain.permission.RoleRespVO;
import com.weili.iot_portal.domain.permission.RoleSaveReqVO;
import com.weili.iot_portal.service.system.IUserRoleBizService;
import com.weili.iot_portal.web.annotation.PermRequired;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;


@Tag(name = "角色管理")
@RestController
@RequestMapping("/system/role")
@Validated
public class RoleController {

    @Resource
    private IUserRoleBizService userRoleBizService;


    @PostMapping("/create")
    @Operation(summary = "创建角色")
    @PermRequired(permission = "system:role:create")
    @ApiInterceptor
    public CommonResult<Long> createRole(@Valid @RequestBody RoleSaveReqVO createReqVO) {
        return CommonResult.success(userRoleBizService.createRole(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改角色")
    @PermRequired(permission = "system:role:update")
    @ApiInterceptor
    public CommonResult<Boolean> updateRole(@Valid @RequestBody RoleSaveReqVO updateReqVO) {
        userRoleBizService.updateRole(updateReqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除角色")
    @Parameter(name = "id", description = "角色编号", required = true, example = "1024")
    @PermRequired(permission = "system:role:delete")
    public CommonResult<Boolean> deleteRole(@RequestParam("id") Long id) {
        userRoleBizService.deleteRole(id);
        return CommonResult.success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得角色信息")
    @ApiInterceptor
    public CommonResult<RoleRespVO> getRole(@RequestParam("id") Long id) {
        RoleDO role = userRoleBizService.getRole(id);
        RoleRespVO respVO = BeanUtils.toBean(role, RoleRespVO.class);
        respVO.setBingUserState(userRoleBizService.userHasBindRole(role.getId()) ? "是" : "否");
        return CommonResult.success(respVO);
    }

    @GetMapping("/page")
    @Operation(summary = "获得角色分页")
    @ApiInterceptor
    public CommonResult<PageResult<RoleRespVO>> getRolePage(RolePageReqVO pageReqVO) {
        PageResult<RoleDO> pageResult = userRoleBizService.getRolePage(pageReqVO);
        PageResult<RoleRespVO> result = BeanUtils.toBean(pageResult, RoleRespVO.class);
        result.getList().forEach(role -> role.setBingUserState(userRoleBizService.userHasBindRole(role.getId()) ? "是" : "否"));
        return CommonResult.success(result);
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获取角色精简信息列表", description = "只包含被开启的角色，主要用于前端的下拉选项")
    @ApiInterceptor
    public CommonResult<List<RoleRespVO>> selectEnableList() {
        List<RoleDO> list = userRoleBizService.selectEnableList();
        list.sort(Comparator.comparing(RoleDO::getSort));
        return CommonResult.success(BeanUtils.toBean(list, RoleRespVO.class));
    }
}
