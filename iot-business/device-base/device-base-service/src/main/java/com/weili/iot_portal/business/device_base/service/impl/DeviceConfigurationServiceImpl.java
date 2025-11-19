package com.weili.iot_portal.business.device_base.service.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceBaseInfoDO;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceConfigurationDO;
import com.weili.iot_portal.business.device_base.dal.ddd.DeviceConfigurationPageQuery;
import com.weili.iot_portal.business.device_base.dal.repository.DeviceBaseInfoRepository;
import com.weili.iot_portal.business.device_base.dal.repository.DeviceConfigurationRepository;
import com.weili.iot_portal.business.device_base.domain.model.DeviceConfigurationVO;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceConfigurationCreateReq;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceConfigurationQueryReq;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceConfigurationUpdateReq;
import com.weili.iot_portal.business.device_base.service.DeviceConfigurationService;
import com.weili.iot_portal.business.device_base.service.assembler.DeviceConfigurationAssembler;
import com.weili.iot_portal.service.cache.DeviceFactoryCacheService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备配置服务实现
 */
@Service
@RequiredArgsConstructor
public class DeviceConfigurationServiceImpl implements DeviceConfigurationService {

    private final DeviceConfigurationRepository configurationRepository;
    private final DeviceBaseInfoRepository deviceBaseInfoRepository;
    private final DeviceFactoryCacheService deviceFactoryCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceConfigurationVO create(String tenantId, String operator, DeviceConfigurationCreateReq request) {
        ensureTenant(tenantId);
        validateCreate(request);
        DeviceBaseInfoDO device = findDevice(tenantId, request.getDeviceId());

        if (configurationRepository.existsByDeviceId(tenantId, request.getDeviceId(), null)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备已存在配置，请使用更新接口");
        }

        DeviceConfigurationDO entity = DeviceConfigurationAssembler.fromCreateReq(tenantId, operator, request);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        configurationRepository.insert(entity);

        return DeviceConfigurationAssembler.toVO(entity, device.getDeviceCode(), device.getDeviceName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceConfigurationVO update(String tenantId, String operator, DeviceConfigurationUpdateReq request) {
        ensureTenant(tenantId);
        DeviceConfigurationDO entity = configurationRepository.findById(tenantId, request.getId())
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备配置不存在"));

        if (request.getIpAddress() != null) {
            entity.setIpAddress(request.getIpAddress());
        }
        if (request.getPort() != null) {
            entity.setPort(request.getPort());
        }
        if (request.getMacAddress() != null) {
            entity.setMacAddress(request.getMacAddress());
        }
        if (request.getGateway() != null) {
            entity.setGateway(request.getGateway());
        }
        if (request.getSubnetMask() != null) {
            entity.setSubnetMask(request.getSubnetMask());
        }
        if (request.getProtocol() != null) {
            entity.setProtocol(request.getProtocol());
        }
        if (request.getConnectionParams() != null) {
            entity.setConnectionParams(request.getConnectionParams());
        }
        if (request.getLocationCode() != null) {
            entity.setLocationCode(request.getLocationCode());
        }
        if (request.getLocationDescription() != null) {
            entity.setLocationDescription(request.getLocationDescription());
        }
        if (request.getCoordinates() != null) {
            entity.setCoordinates(request.getCoordinates());
        }
        entity.setUpdatedBy(operator);
        entity.setUpdateTime(LocalDateTime.now());
        configurationRepository.update(entity);

        DeviceBaseInfoDO device = deviceBaseInfoRepository.findById(tenantId, entity.getDeviceId()).orElse(null);
        String deviceCode = device != null ? device.getDeviceCode() : null;
        String deviceName = device != null ? device.getDeviceName() : null;
        return DeviceConfigurationAssembler.toVO(entity, deviceCode, deviceName);
    }

    @Override
    public DeviceConfigurationVO get(String tenantId, String factoryId, String id) {
        ensureTenant(tenantId);
        DeviceConfigurationDO entity = configurationRepository.findById(tenantId, id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备配置不存在"));
        ensureDeviceBelongsToFactory(tenantId, factoryId, entity.getDeviceId());
        DeviceBaseInfoDO device = deviceBaseInfoRepository.findById(tenantId, entity.getDeviceId()).orElse(null);
        return DeviceConfigurationAssembler.toVO(entity,
                device != null ? device.getDeviceCode() : null,
                device != null ? device.getDeviceName() : null);
    }

    @Override
    public DeviceConfigurationVO getByDeviceId(String tenantId, String factoryId, String deviceId) {
        ensureTenant(tenantId);
        ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
        DeviceConfigurationDO entity = configurationRepository.findByDeviceId(tenantId, deviceId)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备配置不存在"));
        DeviceBaseInfoDO device = deviceBaseInfoRepository.findById(tenantId, deviceId).orElse(null);
        return DeviceConfigurationAssembler.toVO(entity,
                device != null ? device.getDeviceCode() : null,
                device != null ? device.getDeviceName() : null);
    }

    @Override
    public PageResult<DeviceConfigurationVO> page(String tenantId, String factoryId, DeviceConfigurationQueryReq request) {
        ensureTenant(tenantId);
        DeviceConfigurationPageQuery query = new DeviceConfigurationPageQuery();
        query.setTenantId(tenantId);
        query.setFactoryId(factoryId);
        query.setIpAddress(request.getIpAddress());
        query.setProtocol(request.getProtocol());
        query.setLocationCode(request.getLocationCode());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setSortBy(request.getSortBy());
        query.setSortDirection(request.getSortDirection());

        PageResult<DeviceConfigurationDO> pageResult = configurationRepository.selectPage(query);
        Map<String, DeviceBaseInfoDO> deviceMap = buildDeviceCache(tenantId, pageResult.getList());
        List<DeviceConfigurationVO> list = pageResult.getList().stream()
                .map(item -> {
                    DeviceBaseInfoDO device = deviceMap.get(item.getDeviceId());
                    String deviceCode = device != null ? device.getDeviceCode() : null;
                    String deviceName = device != null ? device.getDeviceName() : null;
                    return DeviceConfigurationAssembler.toVO(item, deviceCode, deviceName);
                })
                .collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String tenantId, String id) {
        ensureTenant(tenantId);
        return configurationRepository.deleteById(tenantId, id);
    }

    private void ensureTenant(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            throw new ServiceException(ErrorCodeConstants.UNAUTHORIZED.getCode(), "未获取到租户信息");
        }
    }

    private void validateCreate(DeviceConfigurationCreateReq request) {
        if (StringUtils.isBlank(request.getDeviceId())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备ID不能为空");
        }
    }

    private DeviceBaseInfoDO findDevice(String tenantId, String deviceId) {
        return deviceBaseInfoRepository.findById(tenantId, deviceId)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备不存在"));
    }

    private void ensureDeviceBelongsToFactory(String tenantId, String factoryId, String deviceId) {
        if (StringUtils.isBlank(factoryId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "未获取到工厂信息，请先选择工厂");
        }
        String actualFactoryId = resolveFactoryId(tenantId, deviceId);
        if (!factoryId.equals(actualFactoryId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备不属于当前选择的工厂，无权访问");
        }
    }

    private String resolveFactoryId(String tenantId, String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备ID不能为空");
        }
        String cached = deviceFactoryCacheService.get(deviceId);
        if (StringUtils.isNotBlank(cached)) {
            return cached;
        }
        DeviceBaseInfoDO device = findDevice(tenantId, deviceId);
        if (StringUtils.isBlank(device.getFactoryId())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备未关联工厂");
        }
        deviceFactoryCacheService.cache(device.getId(), device.getFactoryId());
        return device.getFactoryId();
    }

    private Map<String, DeviceBaseInfoDO> buildDeviceCache(String tenantId, List<DeviceConfigurationDO> records) {
        List<String> deviceIds = records.stream()
                .map(DeviceConfigurationDO::getDeviceId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (deviceIds.isEmpty()) {
            return Map.of();
        }
        return deviceIds.stream()
                .map(id -> deviceBaseInfoRepository.findById(tenantId, id).orElse(null))
                .filter(item -> item != null)
                .collect(Collectors.toMap(DeviceBaseInfoDO::getId, device -> device));
    }
}

