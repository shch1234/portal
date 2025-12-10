package com.weili.iot_portal.dal.repository.alarm.impl;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.alarm.AlarmDeviceStatisticsDO;
import com.weili.iot_portal.dal.repository.alarm.AlarmStatisticsRepository;
import com.weili.iot_portal.dal.mapper.alarm.AlarmStatisticsMapper;
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
    public List<AlarmDeviceStatisticsDO> countCurrentAlarmDevices(String factoryId, String workshopId) {
        return mapper.countCurrentAlarmDevices(factoryId, workshopId);
    }

    @Override
    public PageResult<AlarmDeviceStatisticsDO> selectCurrentAlarmDevices(
            String factoryId, String workshopId, int pageNo, int pageSize) {
        long offset = (pageNo - 1L) * pageSize;
        long total = mapper.countCurrentAlarmDeviceTotal(factoryId, workshopId);
        List<AlarmDeviceStatisticsDO> records = mapper.selectCurrentAlarmDevices(
                factoryId, workshopId, offset, pageSize);
        return new PageResult<>(records, total);
    }
}

