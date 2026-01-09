package com.weili.iot_portal.domain.permission;

import com.weili.basic.common.model.BaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "用户信息 Response VO")
@Getter
@Setter
public class LoginUserRespVO  extends BaseVO {
    @Serial
    private static final long serialVersionUID = 6541113041221746588L;

    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名称")
    private String userName;

    @Schema(description = "员工号")
    private Integer jobNumber;

    @Schema(description = "手机号码")
    private String mobile;

    @Schema(description = "所属角色列表")
    private List<Long> roleList;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "最后登录时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "时间戳格式")
    private LocalDateTime loginDate;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "时间戳格式")
    private LocalDateTime createTime;

}
