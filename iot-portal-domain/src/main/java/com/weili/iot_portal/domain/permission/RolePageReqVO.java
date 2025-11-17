package com.weili.iot_portal.domain.permission;

import com.weili.basic.common.model.PageParam;
import com.weili.basic.common.util.DateUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.time.LocalDateTime;


@Schema(description = "角色分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RolePageReqVO extends PageParam {

    @Serial
    private static final long serialVersionUID = -4459994438704769690L;
    @Schema(description = "角色名称，模糊匹配")
    private String name;

    @Schema(description = "创建时间", example = "[2022-07-01 00:00:00,2022-07-01 23:59:59]")
    @DateTimeFormat(pattern = DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
