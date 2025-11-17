package com.weili.iot_portal.domain.permission;

import com.weili.basic.common.model.BaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class AuthLoginReqVO extends BaseVO {

    @Serial
    private static final long serialVersionUID = 6209862933544624569L;

    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "密码不能为空")
    @Length(min = 4, max = 16, message = "密码长度为 4-16 位")
    private String password;

    @NotNull(message = "工号不能为空")
    @Schema(description = "工号不能为空", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer jobNumber;
}
