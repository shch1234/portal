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
 * 设备信息服务实现（对应 device_info 表，主数据管理）
 */
@Service
@RequiredArgsConstructor
public class DeviceBaseInfoServiceImpl implements DeviceBaseInfoService {

    private final DeviceBaseInfoRepository deviceBaseInfoRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceBaseInfoVO create(DeviceBaseInfoCreateReq request) {
        // 从请求中获取厂区ID（organizationUnitId 映射到 orgFactoryId）
        String factoryId = request.getOrgFactoryId();
        if (StringUtils.hasText(request.getOrganizationUnitId())) {
            factoryId = request.getOrganizationUnitId();
        }
        ensureFactory(factoryId);
        validateUnique(request.getDeviceCode(), request.getTbDeviceId(), null);

        DeviceBaseInfoDO entity = DeviceBaseInfoAssembler.fromCreateReq(request, () -> UUID.randomUUID().toString());
        // 确保 orgFactoryId 被设置
        if (StringUtils.hasText(factoryId)) {
            entity.setOrgFactoryId(factoryId);
        }
        deviceBaseInfoRepository.insert(entity);
        return DeviceBaseInfoAssembler.toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceBaseInfoVO update(DeviceBaseInfoUpdateReq request) {
        ensureFactory(request.getOrgFactoryId());
        DeviceBaseInfoDO entity = deviceBaseInfoRepository.findById(request.getId())
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备信息不存在"));
        validateUnique(request.getDeviceCode(), request.getTbDeviceId(), request.getId());
        DeviceBaseInfoAssembler.copyForUpdate(request, entity);
        deviceBaseInfoRepository.update(entity);
        return DeviceBaseInfoAssembler.toVO(entity);
    }

    @Override
    public DeviceBaseInfoVO getById(String factoryId, String id) {
        ensureFactory(factoryId);
        DeviceBaseInfoDO entity = deviceBaseInfoRepository.findById(id)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备信息不存在"));
        ensureDeviceBelongsToFactory(entity, factoryId);
        return DeviceBaseInfoAssembler.toVO(entity);
    }

    @Override
    public DeviceBaseInfoVO getByDeviceCode(String deviceCode) {
        return deviceBaseInfoRepository.findByDeviceCode(deviceCode)
                .map(DeviceBaseInfoAssembler::toVO)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备信息不存在"));
    }

    @Override
    public PageResult<DeviceBaseInfoVO> page(String factoryId, DeviceBaseInfoQueryReq request) {
        ensureFactory(factoryId);
        DeviceBaseInfoPageQuery query = buildPageQuery(factoryId, request);
        PageResult<DeviceBaseInfoDO> pageResult = deviceBaseInfoRepository.selectPage(query);
        return PageResult.of(DeviceBaseInfoAssembler.toVOList(pageResult.getList()),
                pageResult.getTotal(),
                pageResult.getPageNo(),
                pageResult.getPageSize());
    }

    @Override
    public PageResult<DeviceBaseInfoListVO> list(String factoryId, DeviceBaseInfoQueryReq request) {
        ensureFactory(factoryId);
        DeviceBaseInfoPageQuery query = buildPageQuery(factoryId, request);
        PageResult<DeviceBaseInfoDO> pageResult = deviceBaseInfoRepository.selectPage(query);
        return PageResult.of(DeviceBaseInfoAssembler.toListVOList(pageResult.getList()),
                pageResult.getTotal(),
                pageResult.getPageNo(),
                pageResult.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String id) {
        return deviceBaseInfoRepository.deleteById(id);
    }

    /**
     * 构建分页查询条件（对应 device_info 表的字段）
     */
    private DeviceBaseInfoPageQuery buildPageQuery(String factoryId, DeviceBaseInfoQueryReq request) {
        DeviceBaseInfoPageQuery query = new DeviceBaseInfoPageQuery();
        query.setDeviceCodeLike(request.getDeviceCodeLike());
        query.setDeviceNameLike(request.getDeviceNameLike());
        query.setDeviceTypeCodes(request.getDeviceTypeCodes());
        query.setDeviceSubTypeNames(request.getDeviceSubTypeNames());
        query.setDeviceModelIds(request.getDeviceModelIds());
        query.setOrgFactoryIds(mergeIds(request.getOrgFactoryIds(), factoryId));
        query.setOrgWorkshopIds(request.getOrgWorkshopIds());
        query.setOrgProductionLineIds(request.getOrgProductionLineIds());
        query.setDeviceStatuses(request.getDeviceStatuses());
        query.setIsMonitored(request.getIsMonitored());
        query.setHasAlarm(request.getHasAlarm());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setSortBy(request.getSortBy());
        query.setSortDirection(request.getSortDirection());
        return query;
    }

    /**
     * 合并ID列表（用于合并查询条件中的厂区ID）
     */
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

    /**
     * 验证设备编号和TB设备ID的唯一性（对应 device_info 表的 device_code 和 tb_device_id）
     */
    private void validateUnique(String deviceCode, String tbDeviceId, String excludeId) {
        if (StringUtils.hasText(deviceCode) && deviceBaseInfoRepository.existsByDeviceCode(deviceCode, excludeId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备编号已存在");
        }
        if (StringUtils.hasText(tbDeviceId) && deviceBaseInfoRepository.existsByTbDeviceId(tbDeviceId, excludeId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "TB设备已关联其它记录");
        }
    }

    /**
     * 确保厂区ID不为空（对应 device_info 表的 org_factory_id）
     */
    private void ensureFactory(String factoryId) {
        if (!StringUtils.hasText(factoryId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "未获取到工厂信息，请先选择工厂");
        }
    }

    /**
     * 确保设备属于指定厂区（对应 device_info 表的 org_factory_id）
     */
    private void ensureDeviceBelongsToFactory(DeviceBaseInfoDO device, String factoryId) {
        if (device == null) {
            return;
        }
        if (factoryId != null && !factoryId.equals(device.getOrgFactoryId())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备不属于当前工厂");
        }
    }
}
