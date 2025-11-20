package com.weili.iot_portal.domain.permission;

import com.weili.basic.common.model.BaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.util.List;

/**
 * @author luying
 * @description: TODO
 * @date 2025/6/21 0:57
 */
@Getter
@Setter
public class LoginUserSaveReqVO extends BaseVO {
    @Serial
    private static final long serialVersionUID = -3024170846739114065L;

    @Schema(description = "用户id")
    @NotNull
    private Long userId;
    @Schema(description = "关联角色id列表")
    @NotNull
    private List<Long> roleList;
}
