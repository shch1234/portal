package com.weili.iot_portal.business.device_mgmt.dal.repository;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.AlarmHistoryDO;

import java.util.List;

/**
 * 报警历史仓储
 */
public interface AlarmHistoryRepository {

    List<AlarmHistoryDO> selectCurrent(String tenantId, String deviceId);

    PageResult<AlarmHistoryDO> selectPage(String tenantId, String deviceId, Long startTs, Long endTs,
                                          Boolean inProgress, int pageNo, int pageSize);
}


