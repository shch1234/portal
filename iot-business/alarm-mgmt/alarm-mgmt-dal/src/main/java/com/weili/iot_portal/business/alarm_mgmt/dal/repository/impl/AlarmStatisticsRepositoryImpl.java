package com.weili.iot_portal.business.alarm_mgmt.dal.repository.impl;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.alarm_mgmt.dal.dataobject.AlarmDeviceStatisticsDO;
import com.weili.iot_portal.business.alarm_mgmt.dal.mapper.AlarmStatisticsMapper;
import com.weili.iot_portal.business.alarm_mgmt.dal.repository.AlarmStatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 报警统计仓储实现
 */
@Repository
@RequiredArgsConstructor
public class AlarmStatisticsRepositoryImpl implements AlarmStatisticsRepository {

    private final AlarmStatisticsMapper mapper;

    @Override
    public List<AlarmDeviceStatisticsDO> countCurrentAlarmDevices(String tenantId, String factoryId, String workshopId) {
        return mapper.countCurrentAlarmDevices(tenantId, factoryId, workshopId);
    }

    @Override
    public PageResult<AlarmDeviceStatisticsDO> selectCurrentAlarmDevices(
            String tenantId, String factoryId, String workshopId, int pageNo, int pageSize) {
        long offset = (pageNo - 1L) * pageSize;
        long total = mapper.countCurrentAlarmDeviceTotal(tenantId, factoryId, workshopId);
        List<AlarmDeviceStatisticsDO> records = mapper.selectCurrentAlarmDevices(
                tenantId, factoryId, workshopId, offset, pageSize);
        return new PageResult<>(records, total);
    }
}

