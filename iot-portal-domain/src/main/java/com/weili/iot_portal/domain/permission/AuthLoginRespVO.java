package com.weili.iot_portal.domain.permission;

import com.weili.basic.common.model.BaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder
public class AuthLoginRespVO  extends BaseVO {

    @Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;

    @Schema(description = "工号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer jobNumber;

    @Schema(description = "访问令牌", requiredMode = Schema.RequiredMode.REQUIRED)
    private String accessToken;

    @Schema(description = "刷新令牌", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;

    @Schema(description = "过期时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private long expiresTime;
}
