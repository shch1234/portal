package com.weili.iot_portal.business.device_mgmt.dal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.SpindleSpeedHistoryDO;
import com.weili.iot_portal.business.device_mgmt.dal.mapper.SpindleSpeedHistoryMapper;
import com.weili.iot_portal.business.device_mgmt.dal.repository.SpindleSpeedHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SpindleSpeedHistoryRepositoryImpl implements SpindleSpeedHistoryRepository {

    private final SpindleSpeedHistoryMapper mapper;

    @Override
    public void insertBatch(List<SpindleSpeedHistoryDO> list) {
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
    public List<SpindleSpeedHistoryDO> selectByRange(String tenantId, String deviceId, Long startTs, Long endTs, Integer limit) {
        LambdaQueryWrapper<SpindleSpeedHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpindleSpeedHistoryDO::getTenantId, tenantId)
                .eq(SpindleSpeedHistoryDO::getDeviceId, deviceId);
        if (startTs != null) {
            wrapper.ge(SpindleSpeedHistoryDO::getSampleTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(SpindleSpeedHistoryDO::getSampleTs, endTs);
        }
        wrapper.orderByAsc(SpindleSpeedHistoryDO::getSampleTs);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return mapper.selectList(wrapper);
    }
}

