package com.weili.iot_portal.web.system;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.enums.StatusEnum;
import com.weili.iot_portal.dal.dataobject.system.MenuDO;
import com.weili.iot_portal.domain.permission.MenuListReqVO;
import com.weili.iot_portal.domain.permission.MenuRespVO;
import com.weili.iot_portal.domain.permission.MenuSaveVO;
import com.weili.iot_portal.domain.permission.MenuSimpleRespVO;
import com.weili.iot_portal.service.system.IMenuBizService;
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

@Tag(name = "菜单管理")
@RestController
@RequestMapping("/system/menu")
@Validated
public class MenuController {

    @Resource
    private IMenuBizService menuBizService;

    /**
     * 创建菜单
     */
    @PostMapping("/create")
    @ApiInterceptor
    @Operation(summary = "创建菜单")
    @PermRequired(permission = "system:menu:create")
    public CommonResult<Long> createMenu(@Valid @RequestBody MenuSaveVO createReqVO) {
        Long menuId = menuBizService.create(createReqVO);
        return CommonResult.success(menuId);
    }


    @PutMapping("/update")
    @ApiInterceptor
    @Operation(summary = "修改菜单")
    @PermRequired(permission = "system:menu:update")
    public CommonResult<Boolean> updateMenu(@Valid @RequestBody MenuSaveVO updateReqVO) {
        menuBizService.update(updateReqVO);
        return CommonResult.success(true);
    }

    /**
     * 删除菜单
     */
    @DeleteMapping("/delete")
    @ApiInterceptor
    @Operation(summary = "删除菜单")
    @Parameter(name = "id", description = "菜单编号", required = true, example = "1024")
    @PermRequired(permission = "system:menu:delete")
    public CommonResult<Boolean> deleteMenu(@RequestParam("id") Long id) {
        menuBizService.delete(id);
        return CommonResult.success(true);
    }

    /**
     * 获取菜单列表
     */
    @GetMapping("/list")
    @ApiInterceptor
    @Operation(summary = "获取菜单列表", description = "用于【菜单管理】界面")
    public CommonResult<List<MenuRespVO>> getMenuList(MenuListReqVO reqVO) {
        List<MenuDO> list = menuBizService.getMenuList(reqVO);
        list.sort(Comparator.comparing(MenuDO::getSort));
        return CommonResult.success(BeanUtils.toBean(list, MenuRespVO.class));
    }

    /**
     * 获取菜单精简信息列表。只包含被开启的菜单，用于【角色分配菜单】功能的选项
     */
    @GetMapping({"simple-list"})
    @ApiInterceptor
    @Operation(summary = "获取菜单精简信息列表", description = "只包含被开启的菜单，用于【角色分配菜单】功能的选项。")
    public CommonResult<List<MenuSimpleRespVO>> getSimpleMenuList(MenuListReqVO reqVO) {
        reqVO.setStatus(StatusEnum.ENABLE.getStatus());
        List<MenuDO> list = menuBizService.getSimpleMenuList(reqVO);
        list = menuBizService.filterDisableMenus(list);
        list.sort(Comparator.comparing(MenuDO::getSort));
        return CommonResult.success(BeanUtils.toBean(list, MenuSimpleRespVO.class));
    }

    /**
     * 获取菜单信息
     */
    @GetMapping("/get")
    @ApiInterceptor
    @Operation(summary = "获取菜单信息")
    public CommonResult<MenuRespVO> getMenu(Long id) {
        MenuDO menu = menuBizService.getMenu(id);
        return CommonResult.success(BeanUtils.toBean(menu, MenuRespVO.class));
    }
}
