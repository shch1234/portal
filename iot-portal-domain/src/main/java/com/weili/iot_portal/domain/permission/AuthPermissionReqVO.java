package com.weili.iot_portal.domain.permission;

import com.weili.basic.common.model.BaseVO;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class AuthPermissionReqVO extends BaseVO {
    private String userId;
}
