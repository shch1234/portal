package com.weili.iot_portal.business.device_mgmt.dal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.ToolUsageHistoryDO;
import com.weili.iot_portal.business.device_mgmt.dal.mapper.ToolUsageHistoryMapper;
import com.weili.iot_portal.business.device_mgmt.dal.repository.ToolUsageHistoryRepository;
import lombok.RequiredArgsConstructor;
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
            if (item.getCreatedTime() == null) {
                item.setCreatedTime(System.currentTimeMillis());
            }
        });
        list.forEach(mapper::insert);
    }

    @Override
    public List<ToolUsageHistoryDO> selectByRange(String tenantId, String deviceId, Long startTs, Long endTs, Integer limit) {
        LambdaQueryWrapper<ToolUsageHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ToolUsageHistoryDO::getTenantId, tenantId)
                .eq(ToolUsageHistoryDO::getDeviceId, deviceId);
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
}

