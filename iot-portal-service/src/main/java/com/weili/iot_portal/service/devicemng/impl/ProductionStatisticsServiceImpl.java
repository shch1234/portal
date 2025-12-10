package com.weili.iot_portal.service.devicemng.impl;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicemng.ProductionCounterDO;
import com.weili.iot_portal.dal.ddd.device.ProductionCounterPageQuery;
import com.weili.iot_portal.dal.repository.devicemng.ProductionCounterRepository;
import com.weili.iot_portal.domain.devicemng.ProductionHistoryVO;
import com.weili.iot_portal.domain.devicemng.request.ProductionHistoryReq;
import com.weili.iot_portal.service.assembler.ProductionStatisticsAssembler;
import com.weili.iot_portal.service.devicemng.ProductionStatisticsService;
import com.weili.iot_portal.service.support.DeviceFactoryValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 产量统计服务实现
 */
@Service
@RequiredArgsConstructor
public class ProductionStatisticsServiceImpl implements ProductionStatisticsService {

    private final ProductionCounterRepository productionCounterRepository;
    private final DeviceFactoryValidator deviceFactoryValidator;

    @Override
    public ProductionHistoryVO getCurrentShift(String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);
        ProductionCounterDO record = productionCounterRepository
                .findCurrent(deviceId, System.currentTimeMillis())
                .orElse(null);
        if (record == null) {
            return ProductionStatisticsAssembler.toVO(deviceId, Collections.emptyList(), 0, 1, 1);
        }
        return ProductionStatisticsAssembler.toVO(deviceId, Collections.singletonList(record), 1, 1, 1);
    }

    @Override
    public ProductionHistoryVO getHistory(String factoryId, ProductionHistoryReq request) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, request.getDeviceId());
        ProductionCounterPageQuery query = new ProductionCounterPageQuery();
        query.setDeviceId(request.getDeviceId());
        query.setStartTs(request.getStartTs());
        query.setEndTs(request.getEndTs());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setSortBy("shiftStartTs");
        query.setSortDirection("desc");

        PageResult<ProductionCounterDO> pageResult = productionCounterRepository.selectPage(query);
        return ProductionStatisticsAssembler.toVO(
                request.getDeviceId(),
                pageResult.getList(),
                pageResult.getTotal(),
                request.getPageNo(),
                request.getPageSize());
    }
}


