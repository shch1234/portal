package com.weili.iot_portal.dal.repository.devicemng.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.devicemng.SpindleSpeedHistoryDO;
import com.weili.iot_portal.dal.repository.devicemng.SpindleSpeedHistoryRepository;
import com.weili.iot_portal.dal.mapper.devicemng.SpindleSpeedHistoryMapper;
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
        });
        list.forEach(mapper::insert);
    }

    /**
     * 按时间范围查询主轴转速历史（对应 device_spindle_speed_history 表的字段）
     */
    @Override
    public List<SpindleSpeedHistoryDO> selectByRange(String tenantId, String deviceId, Long startTs, Long endTs, Integer limit) {
        LambdaQueryWrapper<SpindleSpeedHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpindleSpeedHistoryDO::getTenantUuid, tenantId)
                .eq(SpindleSpeedHistoryDO::getDeviceInfoId, deviceId);
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

