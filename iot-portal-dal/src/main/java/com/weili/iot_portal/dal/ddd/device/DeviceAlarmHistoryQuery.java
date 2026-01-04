package com.weili.iot_portal.dal.ddd.device;

import com.weili.basic.common.model.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @author luying
 * @className AlarmHistoryQuery
 * @description
 * @date 2025-12-25 11:37
 **/
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceAlarmHistoryQuery extends PageParam {
    private Long deviceId;
    private Long orgFactoryId;
    private List<Long> deviceIds;
    private Long factoryId;
    private Integer isActive;
    private Long startTime;
    private Long endTime;
}
