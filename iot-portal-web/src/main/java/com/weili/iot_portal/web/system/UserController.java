package com.weili.iot_portal.web.system;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.permission.LoginUserPageReqVO;
import com.weili.iot_portal.domain.permission.LoginUserRespVO;
import com.weili.iot_portal.service.system.ILoginUserBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * @author luying
 * @description: TODO
 * @date 2025/6/16 22:51
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/system/user")
@Slf4j
public class UserController {

    @Resource
    private ILoginUserBizService loginUserBizService;


    @GetMapping("/page")
    @Operation(summary = "获得用户分页列表")
    public CommonResult<PageResult<LoginUserRespVO>> getUserPage(@Valid LoginUserPageReqVO pageReqVO) {
        PageResult<LoginUserRespVO> pageResult = loginUserBizService.selectPage(pageReqVO);
        return CommonResult.success(pageResult);
    }

    @GetMapping("/get")
    @Operation(summary = "获得用户详情")
    @Parameter(name = "id", description = "用户id", required = true)
    public CommonResult<LoginUserRespVO> getUser(@RequestParam("id") Long id) {
        return CommonResult.success(loginUserBizService.getById(id));
    }
}
