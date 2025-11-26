package com.weili.iot_portal.service.devicebase.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.ddd.device.DeviceBaseInfoPageQuery;
import com.weili.iot_portal.dal.repository.devicebase.DeviceBaseInfoRepository;
import com.weili.iot_portal.domain.devicebase.DeviceBaseInfoListVO;
import com.weili.iot_portal.domain.devicebase.DeviceBaseInfoVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceBaseInfoCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceBaseInfoQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceBaseInfoUpdateReq;
import com.weili.iot_portal.service.assembler.DeviceBaseInfoAssembler;
import com.weili.iot_portal.service.devicebase.DeviceBaseInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 设备基础信息服务实现（主数据管理）
 */
@Service
@RequiredArgsConstructor
public class DeviceBaseInfoServiceImpl implements DeviceBaseInfoService {

    private final DeviceBaseInfoRepository deviceBaseInfoRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceBaseInfoVO create(String tenantId, DeviceBaseInfoCreateReq request) {
        ensureTenant(tenantId);
        ensureFactory(request.getFactoryId());
        validateUnique(tenantId, request.getDeviceCode(), request.getTbDeviceId(), null);

        DeviceBaseInfoDO entity = DeviceBaseInfoAssembler.fromCreateReq(request, () -> UUID.randomUUID().toString());
        entity.setTenantId(tenantId);
        deviceBaseInfoRepository.insert(entity);
        return DeviceBaseInfoAssembler.toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceBaseInfoVO update(String tenantId,  DeviceBaseInfoUpdateReq request) {
        ensureTenant(tenantId);
        ensureFactory(request.getFactoryId());
        DeviceBaseInfoDO entity = deviceBaseInfoRepository.findById(tenantId, request.getId())
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备基础信息不存在"));
        validateUnique(tenantId, request.getDeviceCode(), request.getTbDeviceId(), request.getId());
        DeviceBaseInfoAssembler.copyForUpdate(request, entity);
        deviceBaseInfoRepository.update(entity);
        return DeviceBaseInfoAssembler.toVO(entity);
    }

    @Override
    public DeviceBaseInfoVO getById(String tenantId, String factoryId, String id) {
        ensureTenant(tenantId);
        ensureFactory(factoryId);
        DeviceBaseInfoDO entity = deviceBaseInfoRepository.findById(tenantId, id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备基础信息不存在"));
        ensureDeviceBelongsToFactory(entity, factoryId);
        return DeviceBaseInfoAssembler.toVO(entity);
    }

    @Override
    public DeviceBaseInfoVO getByDeviceCode(String tenantId, String deviceCode) {
        ensureTenant(tenantId);
        return deviceBaseInfoRepository.findByDeviceCode(tenantId, deviceCode)
                .map(DeviceBaseInfoAssembler::toVO)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备基础信息不存在"));
    }

    @Override
    public PageResult<DeviceBaseInfoVO> page(String tenantId, String factoryId, DeviceBaseInfoQueryReq request) {
        ensureTenant(tenantId);
        ensureFactory(factoryId);
        DeviceBaseInfoPageQuery query = buildPageQuery(tenantId, factoryId, request);
        PageResult<DeviceBaseInfoDO> pageResult = deviceBaseInfoRepository.selectPage(query);
        return PageResult.of(DeviceBaseInfoAssembler.toVOList(pageResult.getList()),
                pageResult.getTotal(),
                pageResult.getPageNo(),
                pageResult.getPageSize());
    }

    @Override
    public PageResult<DeviceBaseInfoListVO> list(String tenantId, String factoryId, DeviceBaseInfoQueryReq request) {
        ensureTenant(tenantId);
        ensureFactory(factoryId);
        DeviceBaseInfoPageQuery query = buildPageQuery(tenantId, factoryId, request);
        PageResult<DeviceBaseInfoDO> pageResult = deviceBaseInfoRepository.selectPage(query);
        return PageResult.of(DeviceBaseInfoAssembler.toListVOList(pageResult.getList()),
                pageResult.getTotal(),
                pageResult.getPageNo(),
                pageResult.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String tenantId, String id) {
        ensureTenant(tenantId);
        return deviceBaseInfoRepository.deleteById(tenantId, id);
    }

    private DeviceBaseInfoPageQuery buildPageQuery(String tenantId, String factoryId, DeviceBaseInfoQueryReq request) {
        DeviceBaseInfoPageQuery query = new DeviceBaseInfoPageQuery();
        query.setTenantId(tenantId);
        query.setDeviceCodeLike(request.getDeviceCodeLike());
        query.setDeviceNameLike(request.getDeviceNameLike());
        query.setDeviceTypeIds(request.getDeviceTypeIds());
        query.setDeviceSubTypeNames(request.getDeviceSubTypeNames());
        query.setDeviceModelIds(request.getDeviceModelIds());
        query.setFactoryIds(mergeIds(request.getFactoryIds(), factoryId));
        query.setWorkshopIds(request.getWorkshopIds());
        query.setProductionLineIds(request.getProductionLineIds());
        query.setDeviceStatuses(request.getDeviceStatuses());
        query.setIsMonitored(request.getIsMonitored());
        query.setHasAlarm(request.getHasAlarm());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setSortBy(request.getSortBy());
        query.setSortDirection(request.getSortDirection());
        return query;
    }

    private List<String> mergeIds(List<String> ids, String appendId) {
        if (!StringUtils.hasText(appendId)) {
            return ids;
        }
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.singletonList(appendId);
        }
        if (ids.contains(appendId)) {
            return ids;
        }
        List<String> copy = new ArrayList<>(ids);
        copy.add(appendId);
        return copy;
    }

    private void validateUnique(String tenantId, String deviceCode, String tbDeviceId, String excludeId) {
        if (StringUtils.hasText(deviceCode) && deviceBaseInfoRepository.existsByDeviceCode(tenantId, deviceCode, excludeId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备编号已存在");
        }
        if (StringUtils.hasText(tbDeviceId) && deviceBaseInfoRepository.existsByTbDeviceId(tenantId, tbDeviceId, excludeId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "TB设备已关联其它记录");
        }
    }

    private void ensureTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new ServiceException(ErrorCodeConstants.UNAUTHORIZED.getCode(), "未获取到租户信息");
        }
    }

    private void ensureFactory(String factoryId) {
        if (!StringUtils.hasText(factoryId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "未获取到工厂信息，请先选择工厂");
        }
    }

    private void ensureDeviceBelongsToFactory(DeviceBaseInfoDO device, String factoryId) {
        if (device == null) {
            return;
        }
        if (factoryId != null && !factoryId.equals(device.getFactoryId())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备不属于当前工厂");
        }
    }
}

