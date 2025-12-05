package com.weili.iot_portal.domain.permission;

import com.weili.basic.common.model.BaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author luying
 * @className AuthUserInfoVO
 * @description
 * @date 2025-08-29 17:01
 **/
@Schema(description = "用户信息 VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthUserInfoRespVO extends BaseVO {

    @Schema(description = "用户Id", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long userId;

    @Schema(description = "登录用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private String userName;

    @Schema(description = "员工号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private String jobNumber;

    @Schema(description = "部门编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long deptId;

    @Schema(description = "部门名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private String deptName;
}
