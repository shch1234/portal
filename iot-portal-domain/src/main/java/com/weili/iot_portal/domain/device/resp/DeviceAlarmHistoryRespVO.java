package com.weili.iot_portal.domain.device.resp;

import com.weili.basic.common.model.PageResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备告警历史响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "设备告警历史响应")
public class DeviceAlarmHistoryRespVO {

    @Schema(description = "当前告警")
    private AlarmHistoryRespVO current;

    @Schema(description = "告警历史列表")
    private PageResult<AlarmHistoryRespVO> historyList;
}
