package com.weili.iot_portal.dal.repository.devicemng.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.devicemng.FeedRateHistoryDO;
import com.weili.iot_portal.dal.repository.devicemng.FeedRateHistoryRepository;
import com.weili.iot_portal.dal.mapper.devicemng.FeedRateHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FeedRateHistoryRepositoryImpl implements FeedRateHistoryRepository {

    private final FeedRateHistoryMapper mapper;

    @Override
    public void insertBatch(List<FeedRateHistoryDO> list) {
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
     * 按时间范围查询进给率历史（对应 device_feed_rate_history 表的字段）
     */
    @Override
    public List<FeedRateHistoryDO> selectByRange(String deviceId, Long startTs, Long endTs, Integer limit) {
        LambdaQueryWrapper<FeedRateHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FeedRateHistoryDO::getDeviceInfoId, deviceId);
        if (startTs != null) {
            wrapper.ge(FeedRateHistoryDO::getSampleTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(FeedRateHistoryDO::getSampleTs, endTs);
        }
        wrapper.orderByAsc(FeedRateHistoryDO::getSampleTs);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return mapper.selectList(wrapper);
    }
}

