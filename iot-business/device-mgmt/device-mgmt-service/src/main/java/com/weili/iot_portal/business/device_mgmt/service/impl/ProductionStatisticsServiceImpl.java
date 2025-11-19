package com.weili.iot_portal.business.device_mgmt.service.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.ProductionCounterDO;
import com.weili.iot_portal.business.device_mgmt.dal.ddd.ProductionCounterPageQuery;
import com.weili.iot_portal.business.device_mgmt.dal.repository.ProductionCounterRepository;
import com.weili.iot_portal.business.device_mgmt.domain.model.ProductionHistoryVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.ProductionHistoryReq;
import com.weili.iot_portal.business.device_mgmt.service.ProductionStatisticsService;
import com.weili.iot_portal.business.device_mgmt.service.assembler.ProductionStatisticsAssembler;
import com.weili.iot_portal.business.device_mgmt.service.support.DeviceFactoryValidator;
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
    public ProductionHistoryVO getCurrentShift(String tenantId, String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
        ProductionCounterDO record = productionCounterRepository
                .findCurrent(tenantId, deviceId, System.currentTimeMillis())
                .orElse(null);
        if (record == null) {
            return ProductionStatisticsAssembler.toVO(deviceId, Collections.emptyList(), 0, 1, 1);
        }
        return ProductionStatisticsAssembler.toVO(deviceId, Collections.singletonList(record), 1, 1, 1);
    }

    @Override
    public ProductionHistoryVO getHistory(String tenantId, String factoryId, ProductionHistoryReq request) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, request.getDeviceId());
        ProductionCounterPageQuery query = new ProductionCounterPageQuery();
        query.setTenantId(tenantId);
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


