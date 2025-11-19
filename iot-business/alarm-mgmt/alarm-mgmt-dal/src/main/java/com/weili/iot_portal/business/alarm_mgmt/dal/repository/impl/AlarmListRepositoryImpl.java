package com.weili.iot_portal.business.alarm_mgmt.dal.repository.impl;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.alarm_mgmt.dal.dataobject.AlarmItemDO;
import com.weili.iot_portal.business.alarm_mgmt.dal.mapper.AlarmListMapper;
import com.weili.iot_portal.business.alarm_mgmt.dal.repository.AlarmListRepository;
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
            String tenantId,
            String factoryId,
            String workshopId,
            List<String> deviceCodes,
            Long startTime,
            Long endTime,
            Boolean isActive,
            List<String> alarmLevels) {
        return alarmListMapper.countAlarmList(
                tenantId, factoryId, workshopId, deviceCodes,
                startTime, endTime, isActive, alarmLevels);
    }

    @Override
    public PageResult<AlarmItemDO> selectAlarmList(
            String tenantId,
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
                tenantId, factoryId, workshopId, deviceCodes,
                startTime, endTime, isActive, alarmLevels);

        // 分页查询
        int offset = (pageNo - 1) * pageSize;
        List<AlarmItemDO> records = alarmListMapper.selectAlarmList(
                tenantId, factoryId, workshopId, deviceCodes,
                startTime, endTime, isActive, alarmLevels,
                offset, pageSize);

        return PageResult.of(records, total, pageNo, pageSize);
    }

    @Override
    public List<AlarmItemDO> selectTopActiveAlarms(String tenantId, String factoryId, int limit) {
        return alarmListMapper.selectTopActiveAlarms(tenantId, factoryId, limit);
    }
}

