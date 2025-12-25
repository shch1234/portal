package com.weili.iot_portal.domain.device.resp;

import com.weili.basic.common.model.PageResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备刀具记录响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "设备刀具记录响应")
public class DeviceToolRecordRespVO {

    @Schema(description = "当前刀具使用记录")
    private ToolRecord current;

    @Schema(description = "刀具使用记录列表")
    private PageResult<ToolRecord> recordList;
}
