package com.weili.iot_portal.service.devicebase.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceLocationDO;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceNetworkConfigDO;
import com.weili.iot_portal.dal.ddd.device.DeviceNetworkConfigPageQuery;
import com.weili.iot_portal.dal.repository.devicebase.DeviceBaseInfoRepository;
import com.weili.iot_portal.dal.repository.devicebase.DeviceLocationRepository;
import com.weili.iot_portal.dal.repository.devicebase.DeviceNetworkConfigRepository;
import com.weili.iot_portal.domain.devicebase.DeviceNetworkConfigVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceNetworkConfigCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceNetworkConfigQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceNetworkConfigUpdateReq;
import com.weili.iot_portal.service.assembler.DeviceNetworkConfigAssembler;
import com.weili.iot_portal.service.cache.DeviceFactoryCacheService;
import com.weili.iot_portal.service.devicebase.DeviceNetworkConfigService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceNetworkConfigServiceImpl implements DeviceNetworkConfigService {

    private final DeviceNetworkConfigRepository configurationRepository;
    private final DeviceBaseInfoRepository deviceBaseInfoRepository;
    private final DeviceLocationRepository deviceLocationRepository;
    private final DeviceFactoryCacheService deviceFactoryCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceNetworkConfigVO create(DeviceNetworkConfigCreateReq request) {
        validateCreate(request);
        DeviceBaseInfoDO device = findDevice(request.getDeviceId());

        if (configurationRepository.existsByDeviceInfoId(request.getDeviceId(), null)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备已存在配置，请使用更新接口");
        }

        DeviceNetworkConfigDO entity = DeviceNetworkConfigAssembler.fromCreateReq(request);
        entity.setEffectiveStartTs(Instant.now().getEpochSecond());
        entity.setIsActive(Boolean.TRUE);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        configurationRepository.insert(entity);

        // 物理位置独立存储，保持与网络配置的生效版本一致
        DeviceLocationDO location = DeviceNetworkConfigAssembler.toLocationFromCreateReq(request);
        location.setEffectiveStartTs(entity.getEffectiveStartTs());
        location.setActive(Boolean.TRUE);
        location.setCreateTime(now);
        location.setUpdateTime(now);
        deviceLocationRepository.insert(location);

        return DeviceNetworkConfigAssembler.toVO(entity, location, device.getDeviceCode(), device.getDeviceName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceNetworkConfigVO update(DeviceNetworkConfigUpdateReq request) {
        DeviceNetworkConfigDO entity = configurationRepository.findById(request.getId())
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
        entity.setUpdateTime(LocalDateTime.now());
        configurationRepository.update(entity);

        DeviceLocationDO location = deviceLocationRepository.findByDeviceId(entity.getDeviceInfoId())
                .orElseGet(() -> {
                    DeviceLocationDO created = new DeviceLocationDO();
                    created.setId(java.util.UUID.randomUUID().toString());
                    created.setDeviceInfoId(entity.getDeviceInfoId());
                    created.setEffectiveStartTs(entity.getEffectiveStartTs());
                    created.setActive(Boolean.TRUE);
                    created.setCreateTime(entity.getCreateTime());
                    return created;
                });
        if (request.getLocationCode() != null) {
            location.setLocationCode(request.getLocationCode());
        }
        if (request.getLocationDescription() != null) {
            location.setLocationDescription(request.getLocationDescription());
        }
        if (request.getCoordinates() != null) {
            location.setCoordinates(request.getCoordinates());
        }
        location.setUpdateTime(LocalDateTime.now());
        if (location.getCreateTime() == null) {
            location.setCreateTime(entity.getCreateTime());
        }
        if (location.getId() == null) {
            deviceLocationRepository.insert(location);
        } else {
            deviceLocationRepository.update(location);
        }

        DeviceBaseInfoDO device = deviceBaseInfoRepository.findById(entity.getDeviceInfoId()).orElse(null);
        String deviceCode = device != null ? device.getDeviceCode() : null;
        String deviceName = device != null ? device.getDeviceName() : null;
        return DeviceNetworkConfigAssembler.toVO(entity, location, deviceCode, deviceName);
    }

    @Override
    public DeviceNetworkConfigVO get(String factoryId, String id) {
        DeviceNetworkConfigDO entity = configurationRepository.findById(id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备配置不存在"));
        ensureDeviceBelongsToFactory(factoryId, entity.getDeviceInfoId());
        DeviceBaseInfoDO device = deviceBaseInfoRepository.findById(entity.getDeviceInfoId()).orElse(null);
        DeviceLocationDO location = deviceLocationRepository.findByDeviceId(entity.getDeviceInfoId()).orElse(null);
        return DeviceNetworkConfigAssembler.toVO(entity,
                location,
                device != null ? device.getDeviceCode() : null,
                device != null ? device.getDeviceName() : null);
    }

    @Override
    public DeviceNetworkConfigVO getByDeviceId(String factoryId, String deviceId) {
        ensureDeviceBelongsToFactory(factoryId, deviceId);
        DeviceNetworkConfigDO entity = configurationRepository.findByDeviceInfoId(deviceId)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备配置不存在"));
        DeviceBaseInfoDO device = deviceBaseInfoRepository.findById(deviceId).orElse(null);
        DeviceLocationDO location = deviceLocationRepository.findByDeviceId(entity.getDeviceInfoId()).orElse(null);
        return DeviceNetworkConfigAssembler.toVO(entity,
                location,
                device != null ? device.getDeviceCode() : null,
                device != null ? device.getDeviceName() : null);
    }

    @Override
    public PageResult<DeviceNetworkConfigVO> page(String factoryId, DeviceNetworkConfigQueryReq request) {
        DeviceNetworkConfigPageQuery query = new DeviceNetworkConfigPageQuery();
        query.setFactoryId(factoryId);
        if (StringUtils.isNotBlank(request.getLocationCode())) {
            // 位置筛选先反查设备ID，再带入网络配置分页
            query.setDeviceIds(deviceLocationRepository.findDeviceIdsByLocationCode(request.getLocationCode()));
        }
        query.setIpAddress(request.getIpAddress());
        query.setProtocol(request.getProtocol());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setSortBy(request.getSortBy());
        query.setSortDirection(request.getSortDirection());

        PageResult<DeviceNetworkConfigDO> pageResult = configurationRepository.selectPage(query);
        Map<String, DeviceBaseInfoDO> deviceMap = buildDeviceCache(pageResult.getList());
        Map<String, DeviceLocationDO> locationMap = deviceLocationRepository.findByDeviceIds(
                        pageResult.getList().stream().map(DeviceNetworkConfigDO::getDeviceInfoId).toList())
                .stream()
                .collect(Collectors.toMap(DeviceLocationDO::getDeviceInfoId, it -> it, (a, b) -> a));
        List<DeviceNetworkConfigVO> list = pageResult.getList().stream()
                .map(item -> {
                    DeviceBaseInfoDO device = deviceMap.get(item.getDeviceInfoId());
                    String deviceCode = device != null ? device.getDeviceCode() : null;
                    String deviceName = device != null ? device.getDeviceName() : null;
                    return DeviceNetworkConfigAssembler.toVO(item, locationMap.get(item.getDeviceInfoId()), deviceCode, deviceName);
                })
                .collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String id) {
        return configurationRepository.deleteById(id);
    }

    private void validateCreate(DeviceNetworkConfigCreateReq request) {
        if (StringUtils.isBlank(request.getDeviceId())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备ID不能为空");
        }
    }

    private DeviceBaseInfoDO findDevice(String deviceId) {
        return deviceBaseInfoRepository.findById(deviceId)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备不存在"));
    }

    private void ensureDeviceBelongsToFactory(String factoryId, String deviceId) {
        if (StringUtils.isBlank(factoryId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "未获取到工厂信息，请先选择工厂");
        }
        String actualFactoryId = resolveFactoryId(deviceId);
        if (!factoryId.equals(actualFactoryId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备不属于当前选择的工厂，无权访问");
        }
    }

    private String resolveFactoryId(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备ID不能为空");
        }
        String cached = deviceFactoryCacheService.get(deviceId);
        if (StringUtils.isNotBlank(cached)) {
            return cached;
        }
        DeviceBaseInfoDO device = findDevice(deviceId);
        if (StringUtils.isBlank(device.getOrgFactoryId())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备未关联工厂");
        }
        deviceFactoryCacheService.cache(device.getId(), device.getOrgFactoryId());
        return device.getOrgFactoryId();
    }

    private Map<String, DeviceBaseInfoDO> buildDeviceCache(List<DeviceNetworkConfigDO> records) {
        List<String> deviceIds = records.stream()
                .map(DeviceNetworkConfigDO::getDeviceInfoId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (deviceIds.isEmpty()) {
            return Map.of();
        }
        return deviceIds.stream()
                .map(id -> deviceBaseInfoRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(DeviceBaseInfoDO::getId, device -> device));
    }
}

