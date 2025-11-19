package com.weili.iot_portal.business.device_mgmt.dal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.AlarmHistoryDO;
import com.weili.iot_portal.business.device_mgmt.dal.mapper.AlarmHistoryMapper;
import com.weili.iot_portal.business.device_mgmt.dal.repository.AlarmHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 报警历史仓储实现
 */
@Repository
@RequiredArgsConstructor
public class AlarmHistoryRepositoryImpl implements AlarmHistoryRepository {

    private final AlarmHistoryMapper mapper;

    @Override
    public List<AlarmHistoryDO> selectCurrent(String tenantId, String deviceId) {
        LambdaQueryWrapper<AlarmHistoryDO> wrapper = baseQuery(tenantId, deviceId)
                .eq(AlarmHistoryDO::getIsActive, Boolean.TRUE)
                .orderByDesc(AlarmHistoryDO::getStartTs);
        return mapper.selectList(wrapper);
    }

    @Override
    public PageResult<AlarmHistoryDO> selectPage(String tenantId, String deviceId, Long startTs, Long endTs,
                                                 Boolean inProgress, int pageNo, int pageSize) {
        LambdaQueryWrapper<AlarmHistoryDO> wrapper = baseQuery(tenantId, deviceId);
        if (startTs != null) {
            wrapper.ge(AlarmHistoryDO::getStartTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(AlarmHistoryDO::getStartTs, endTs);
        }
        if (inProgress != null) {
            wrapper.eq(AlarmHistoryDO::getIsActive, inProgress);
        }
        wrapper.orderByDesc(AlarmHistoryDO::getStartTs);

        Page<AlarmHistoryDO> page = new Page<>(pageNo, pageSize);
        Page<AlarmHistoryDO> result = mapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    private LambdaQueryWrapper<AlarmHistoryDO> baseQuery(String tenantId, String deviceId) {
        LambdaQueryWrapper<AlarmHistoryDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(tenantId)) {
            wrapper.eq(AlarmHistoryDO::getTenantId, tenantId);
        }
        if (StringUtils.isNotBlank(deviceId)) {
            wrapper.eq(AlarmHistoryDO::getDeviceId, deviceId);
        }
        return wrapper;
    }
}


