package com.weili.iot_portal.dal.repository.factory.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.factory.FactoryMetricSummaryDO;
import com.weili.iot_portal.dal.mapper.factory.FactoryMetricSummaryMapper;
import com.weili.iot_portal.dal.repository.factory.FactoryMetricSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 工厂班次指标仓储实现
 */
@Repository
@RequiredArgsConstructor
public class FactoryMetricSummaryRepositoryImpl implements FactoryMetricSummaryRepository {

    private final FactoryMetricSummaryMapper mapper;

    @Override
    public FactoryMetricSummaryDO findByShift(Long factoryId, LocalDate shiftDate, String shiftCode) {
        LambdaQueryWrapper<FactoryMetricSummaryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FactoryMetricSummaryDO::getOrgFactoryId, factoryId)
                .eq(FactoryMetricSummaryDO::getShiftDate, shiftDate)
                .eq(FactoryMetricSummaryDO::getShiftCode, shiftCode)
                .last("limit 1");
        return mapper.selectOne(wrapper);
    }

    @Override
    public List<FactoryMetricSummaryDO> selectFinalizedInRange(Long factoryId, Long startTs, Long endTs) {
        LambdaQueryWrapper<FactoryMetricSummaryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FactoryMetricSummaryDO::getOrgFactoryId, factoryId)
                .eq(FactoryMetricSummaryDO::getIsFinalized, Boolean.TRUE);
        if (startTs != null) {
            long startTsMillis = startTs * 1000L;
            wrapper.ge(FactoryMetricSummaryDO::getShiftEndTs, startTsMillis);
        }
        if (endTs != null) {
            long endTsMillis = endTs * 1000L;
            wrapper.le(FactoryMetricSummaryDO::getShiftEndTs, endTsMillis);
        }
        wrapper.orderByAsc(FactoryMetricSummaryDO::getShiftEndTs);
        return mapper.selectList(wrapper);
    }

    @Override
    public List<FactoryMetricSummaryDO> selectFinalizedInRange(Long orgFactoryId, LocalDate startShiftDate, LocalDate endShiftDate) {
        LambdaQueryWrapper<FactoryMetricSummaryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FactoryMetricSummaryDO::getOrgFactoryId, orgFactoryId)
                .eq(FactoryMetricSummaryDO::getIsFinalized, Boolean.TRUE);
        if (startShiftDate != null) {
            wrapper.ge(FactoryMetricSummaryDO::getShiftDate, startShiftDate);
        }
        if (endShiftDate != null) {
            wrapper.le(FactoryMetricSummaryDO::getShiftDate, endShiftDate);
        }
        wrapper.orderByAsc(FactoryMetricSummaryDO::getShiftDate);
        return mapper.selectList(wrapper);
    }

    @Override
    public void insert(FactoryMetricSummaryDO record) {
        mapper.insert(record);
    }

    @Override
    public void update(FactoryMetricSummaryDO record) {
        mapper.updateById(record);
    }
}

