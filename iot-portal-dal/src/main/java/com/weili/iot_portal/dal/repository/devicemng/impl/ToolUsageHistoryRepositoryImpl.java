package com.weili.iot_portal.dal.repository.devicemng.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.devicemng.ToolUsageHistoryDO;
import com.weili.iot_portal.dal.repository.devicemng.ToolUsageHistoryRepository;
import com.weili.iot_portal.dal.mapper.devicemng.ToolUsageHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ToolUsageHistoryRepositoryImpl implements ToolUsageHistoryRepository {

    private final ToolUsageHistoryMapper mapper;

    @Override
    public void insertBatch(List<ToolUsageHistoryDO> list) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        list.forEach(item -> {
            if (StringUtils.isBlank(item.getId())) {
                item.setId(UUID.randomUUID().toString());
            }
        });
        list.forEach(mapper::insert);
    }

    @Override
    public void insert(ToolUsageHistoryDO record) {
        if (record == null) {
            return;
        }
        if (StringUtils.isBlank(record.getId())) {
            record.setId(UUID.randomUUID().toString());
        }
        mapper.insert(record);
    }

    @Override
    public void updateById(ToolUsageHistoryDO record) {
        if (record == null || StringUtils.isBlank(record.getId())) {
            return;
        }
        mapper.updateById(record);
    }

    /**
     * 按时间范围查询刀具使用记录（对应 device_tool_record 表的字段）
     */
    @Override
    public List<ToolUsageHistoryDO> selectByRange(String tenantId, String deviceId, Long startTs, Long endTs, Integer limit) {
        LambdaQueryWrapper<ToolUsageHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ToolUsageHistoryDO::getTenantUuid, tenantId)
                .eq(ToolUsageHistoryDO::getDeviceInfoId, deviceId);
        if (startTs != null) {
            wrapper.ge(ToolUsageHistoryDO::getStartTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(ToolUsageHistoryDO::getEndTs, endTs);
        }
        wrapper.orderByDesc(ToolUsageHistoryDO::getStartTs);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return mapper.selectList(wrapper);
    }

    @Override
    public ToolUsageHistoryDO findLatestOngoing(String tenantId, String deviceId) {
        LambdaQueryWrapper<ToolUsageHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ToolUsageHistoryDO::getTenantUuid, tenantId)
                .eq(ToolUsageHistoryDO::getDeviceInfoId, deviceId)
                .isNull(ToolUsageHistoryDO::getEndTs)
                .orderByDesc(ToolUsageHistoryDO::getStartTs)
                .last("LIMIT 1");
        return mapper.selectOne(wrapper);
    }
}

