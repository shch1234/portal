package com.weili.iot_portal.dal.repository.alarm.impl;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.alarm.AlarmItemDO;
import com.weili.iot_portal.dal.repository.alarm.AlarmListRepository;
import com.weili.iot_portal.dal.mapper.alarm.AlarmListMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 报警列表仓储实现
 */
@Repository
@RequiredArgsConstructor
public class AlarmListRepositoryImpl implements AlarmListRepository {

    private final AlarmListMapper alarmListMapper;

    @Override
    public long countAlarmList(
            String factoryId,
            String workshopId,
            List<String> deviceCodes,
            Long startTime,
            Long endTime,
            Boolean isActive,
            List<String> alarmLevels) {
        return alarmListMapper.countAlarmList(
                factoryId, workshopId, deviceCodes,
                startTime, endTime, isActive, alarmLevels);
    }

    @Override
    public PageResult<AlarmItemDO> selectAlarmList(
            String factoryId,
            String workshopId,
            List<String> deviceCodes,
            Long startTime,
            Long endTime,
            Boolean isActive,
            List<String> alarmLevels,
            int pageNo,
            int pageSize) {
        // 计算总数
        long total = countAlarmList(
                factoryId, workshopId, deviceCodes,
                startTime, endTime, isActive, alarmLevels);

        // 分页查询
        int offset = (pageNo - 1) * pageSize;
        List<AlarmItemDO> records = alarmListMapper.selectAlarmList(
                factoryId, workshopId, deviceCodes,
                startTime, endTime, isActive, alarmLevels,
                offset, pageSize);

        return PageResult.of(records, total, pageNo, pageSize);
    }

    @Override
    public List<AlarmItemDO> selectTopActiveAlarms(String factoryId, int limit) {
        return alarmListMapper.selectTopActiveAlarms(factoryId, limit);
    }
}

