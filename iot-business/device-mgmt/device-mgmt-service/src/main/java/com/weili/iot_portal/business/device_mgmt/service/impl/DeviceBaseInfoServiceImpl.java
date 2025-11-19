package com.weili.iot_portal.business.device_mgmt.service.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.common.api.device.DeviceBaseDataApi;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceBaseInfoDO;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceModelDO;
import com.weili.iot_portal.business.device_mgmt.dal.ddd.DeviceBaseInfoPageQuery;
import com.weili.iot_portal.business.device_mgmt.dal.repository.DeviceBaseInfoRepository;
import com.weili.iot_portal.business.device_mgmt.dal.repository.DeviceModelRepository;
import com.weili.iot_portal.business.device_mgmt.domain.enums.DeviceStateEnum;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceBaseInfoListVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceBaseInfoVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceBaseInfoCreateReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceBaseInfoQueryReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceBaseInfoUpdateReq;
import com.weili.iot_portal.business.device_mgmt.service.AlarmHistoryService;
import com.weili.iot_portal.business.device_mgmt.service.DeviceBaseInfoService;
import com.weili.iot_portal.business.device_mgmt.service.assembler.DeviceBaseInfoAssembler;
import com.weili.iot_portal.business.device_mgmt.service.support.DeviceFactoryValidator;
import com.weili.iot_portal.business.device_mgmt.service.support.DeviceIdentityCacheService;
import com.weili.iot_portal.business.device_mgmt.service.support.TbTelemetryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 设备基础信息服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceBaseInfoServiceImpl implements DeviceBaseInfoService {

    private final DeviceBaseInfoRepository repository;
    private final DeviceModelRepository deviceModelRepository;
    private final AlarmHistoryService alarmHistoryService;
    private final TbTelemetryClient telemetryClient;
    private final DeviceFactoryValidator deviceFactoryValidator;
    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final DeviceBaseDataApi deviceBaseDataApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceBaseInfoVO create(String tenantId, String operator, DeviceBaseInfoCreateReq request) {
        ensureTenant(tenantId);
        validateUnique(tenantId, request.getDeviceCode(), request.getTbDeviceId(), null);
        DeviceBaseInfoDO entity = DeviceBaseInfoAssembler.fromCreateReq(request, () -> UUID.randomUUID().toString());
        entity.setTenantId(tenantId);
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        repository.insert(entity);
        deviceIdentityCacheService.refresh(entity);
        deviceFactoryValidator.refreshFactoryCache(entity.getId(), entity.getFactoryId());
        DeviceBaseInfoVO vo = DeviceBaseInfoAssembler.toVO(entity);
        fillDeviceNetworkInfo(tenantId, entity.getFactoryId(), vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceBaseInfoVO update(String tenantId, String operator, DeviceBaseInfoUpdateReq request) {
        ensureTenant(tenantId);
        DeviceBaseInfoDO entity = repository.findById(tenantId, request.getId())
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备基础信息不存在"));
        validateUnique(tenantId, request.getDeviceCode(), request.getTbDeviceId(), request.getId());
        String oldDeviceCode = entity.getDeviceCode();
        String oldFactoryId = entity.getFactoryId();
        DeviceBaseInfoAssembler.copyForUpdate(request, entity);
        entity.setUpdatedBy(operator);
        repository.update(entity);
        deviceIdentityCacheService.refresh(entity, oldDeviceCode);
        deviceFactoryValidator.refreshFactoryCache(entity.getId(), entity.getFactoryId());
        DeviceBaseInfoVO vo = DeviceBaseInfoAssembler.toVO(entity);
        fillDeviceNetworkInfo(tenantId, entity.getFactoryId(), vo);
        return vo;
    }

    @Override
    public DeviceBaseInfoVO getById(String tenantId, String factoryId, String id) {
        ensureTenant(tenantId);
        ensureFactory(factoryId);
        Optional<DeviceBaseInfoDO> entity = repository.findById(tenantId, id);
        DeviceBaseInfoDO device = entity
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备基础信息不存在"));
        // 验证设备是否属于当前工厂
        ensureDeviceBelongsToFactory(device, factoryId);
        
        // 关联查询设备型号信息（获取数控系统、控制器型号）
        DeviceModelDO deviceModel = null;
        if (StringUtils.isNotBlank(device.getDeviceModelId())) {
            deviceModel = deviceModelRepository.findById(tenantId, device.getDeviceModelId()).orElse(null);
        }
        
        DeviceBaseInfoVO vo = DeviceBaseInfoAssembler.toVO(device, deviceModel);
        fillDeviceNetworkInfo(tenantId, factoryId, vo);
        return vo;
    }

    @Override
    public DeviceBaseInfoVO getByDeviceCode(String tenantId, String deviceCode) {
        ensureTenant(tenantId);
        Optional<DeviceBaseInfoDO> entity = repository.findByDeviceCode(tenantId, deviceCode);
        if (!entity.isPresent()) {
            return null;
        }
        DeviceBaseInfoDO device = entity.get();
        
        // 关联查询设备型号信息（获取数控系统、控制器型号）
        DeviceModelDO deviceModel = null;
        if (StringUtils.isNotBlank(device.getDeviceModelId())) {
            deviceModel = deviceModelRepository.findById(tenantId, device.getDeviceModelId()).orElse(null);
        }
        
        DeviceBaseInfoVO vo = DeviceBaseInfoAssembler.toVO(device, deviceModel);
        fillDeviceNetworkInfo(tenantId, device.getFactoryId(), vo);
        return vo;
    }

    @Override
    public PageResult<DeviceBaseInfoVO> page(String tenantId, String factoryId, DeviceBaseInfoQueryReq request) {
        ensureTenant(tenantId);
        ensureFactory(factoryId);
        DeviceBaseInfoPageQuery query = buildQuery(tenantId, factoryId, request);
        PageResult<DeviceBaseInfoDO> pageResult = repository.selectPage(query);
        List<DeviceBaseInfoVO> records = pageResult.getList().stream()
                .map(DeviceBaseInfoAssembler::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(records, pageResult.getTotal());
    }

    @Override
    public PageResult<DeviceBaseInfoListVO> list(String tenantId, String factoryId, DeviceBaseInfoQueryReq request) {
        ensureTenant(tenantId);
        ensureFactory(factoryId);
        DeviceBaseInfoPageQuery query = buildQuery(tenantId, factoryId, request);
        
        // 先查询基础数据（不包含报警筛选）
        PageResult<DeviceBaseInfoDO> pageResult = repository.selectPage(query);
        List<DeviceBaseInfoDO> allDevices = pageResult.getList();
        
        // 批量查询报警状态和实时状态
        List<DeviceBaseInfoListVO> listVOs = allDevices.stream()
                .map(DeviceBaseInfoAssembler::toListVO)
                .collect(Collectors.toList());
        
        // 填充实时状态和报警状态
        fillRealtimeStatus(allDevices, listVOs);
        fillAlarmStatus(tenantId, factoryId, listVOs);
        
        // 如果要求筛选报警状态，进行过滤
        if (request.getHasAlarm() != null) {
            listVOs = listVOs.stream()
                    .filter(vo -> request.getHasAlarm().equals(vo.getHasAlarm()))
                    .collect(Collectors.toList());
        }
        
        // 注意：如果进行了报警筛选，总数需要重新计算
        // 这里简化处理，返回筛选后的数量
        // 实际生产环境可能需要先查询所有设备，再筛选，然后分页
        long total = request.getHasAlarm() != null ? listVOs.size() : pageResult.getTotal();
        
        return new PageResult<>(listVOs, total);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String tenantId, String id) {
        ensureTenant(tenantId);
        return repository.deleteById(tenantId, id);
    }

    private void validateUnique(String tenantId, String deviceCode, String tbDeviceId, String excludeId) {
        if (repository.existsByDeviceCode(tenantId, deviceCode, excludeId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备编码已存在");
        }
        if (StringUtils.isNotBlank(tbDeviceId) && repository.existsByTbDeviceId(tenantId, tbDeviceId, excludeId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "TB 设备已关联其它记录");
        }
    }

    private void ensureTenant(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            throw new ServiceException(ErrorCodeConstants.UNAUTHORIZED.getCode(), "未获取到租户信息");
        }
    }

    private void ensureFactory(String factoryId) {
        if (StringUtils.isBlank(factoryId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "未获取到工厂信息，请先选择工厂");
        }
    }

    /**
     * 验证设备是否属于指定工厂
     */
    private void ensureDeviceBelongsToFactory(DeviceBaseInfoDO device, String factoryId) {
        if (device == null) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备不存在");
        }
        if (StringUtils.isBlank(device.getFactoryId())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "设备未关联工厂");
        }
        if (!factoryId.equals(device.getFactoryId())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), 
                "设备不属于当前选择的工厂，无权访问");
        }
    }

    /**
     * 填充设备实时状态（从 ThingsBoard 查询）
     */
    private void fillRealtimeStatus(List<DeviceBaseInfoDO> devices, List<DeviceBaseInfoListVO> listVOs) {
        for (int i = 0; i < devices.size() && i < listVOs.size(); i++) {
            DeviceBaseInfoDO device = devices.get(i);
            DeviceBaseInfoListVO vo = listVOs.get(i);
            
            if (StringUtils.isBlank(device.getTbDeviceId())) {
                vo.setCurrentStatus("未关联");
                continue;
            }
            
            try {
                // 从 ThingsBoard 查询最新的状态数据
                // 查询最近1分钟内的状态数据，取最新的一条
                long endTs = System.currentTimeMillis();
                long startTs = endTs - 60_000; // 1分钟前
                
                List<TbTelemetryClient.TelemetryPoint> statusPoints = telemetryClient.queryTelemetry(
                        device.getTbDeviceId(),
                        "status", // 状态字段名
                        startTs,
                        endTs,
                        1 // 只取最新一条
                );
                
                if (statusPoints != null && !statusPoints.isEmpty()) {
                    // 获取最新的状态值（时间戳最大的）
                    TbTelemetryClient.TelemetryPoint latestPoint = statusPoints.stream()
                            .max((p1, p2) -> Long.compare(p1.getTs(), p2.getTs()))
                            .orElse(statusPoints.get(0));
                    String statusValue = String.valueOf((int) latestPoint.getValue());
                    // 转换为中文状态
                    vo.setCurrentStatus(convertStatusToChinese(statusValue));
                } else {
                    // 如果没有 status 字段，尝试查询 state 字段
                    List<TbTelemetryClient.TelemetryPoint> statePoints = telemetryClient.queryTelemetry(
                            device.getTbDeviceId(),
                            "state", // 备用状态字段名
                            startTs,
                            endTs,
                            1
                    );
                    
                    if (statePoints != null && !statePoints.isEmpty()) {
                        TbTelemetryClient.TelemetryPoint latestPoint = statePoints.stream()
                                .max((p1, p2) -> Long.compare(p1.getTs(), p2.getTs()))
                                .orElse(statePoints.get(0));
                        String stateValue = String.valueOf((int) latestPoint.getValue());
                        vo.setCurrentStatus(convertStatusToChinese(stateValue));
                    } else {
                        vo.setCurrentStatus("未知");
                    }
                }
            } catch (Exception e) {
                // 查询失败时，设置为未知
                vo.setCurrentStatus("未知");
            }
        }
    }

    /**
     * 将状态值转换为中文
     * @param statusValue 状态值（数字或字符串）
     * @return 中文状态
     */
    private String convertStatusToChinese(String statusValue) {
        if (StringUtils.isBlank(statusValue)) {
            return "未知";
        }
        
        try {
            // 尝试解析为数字状态码
            int statusCode = Integer.parseInt(statusValue.trim());
            switch (statusCode) {
                case 1:
                case 2:
                    return "加工中";
                case 3:
                    return "待机";
                case 4:
                    return "故障";
                case 5:
                    return "关机";
                default:
                    return "未知";
            }
        } catch (NumberFormatException e) {
            // 如果不是数字，尝试按枚举值匹配
            String upperValue = statusValue.toUpperCase().trim();
            DeviceStateEnum stateEnum = DeviceStateEnum.of(upperValue);
            
            switch (stateEnum) {
                case RUNNING:
                case WORKING:
                    return "加工中";
                case STANDBY:
                case IDLE:
                    return "待机";
                case FAULT:
                    return "故障";
                case SHUTDOWN:
                    return "关机";
                default:
                    return "未知";
            }
        }
    }

    /**
     * 填充报警状态信息
     */
    private void fillAlarmStatus(String tenantId, String factoryId, List<DeviceBaseInfoListVO> listVOs) {
        for (DeviceBaseInfoListVO vo : listVOs) {
            try {
                // 查询当前报警
                var alarmResult = alarmHistoryService.getCurrentAlarms(tenantId, factoryId, vo.getId());
                if (alarmResult != null && alarmResult.getAlarms() != null && !alarmResult.getAlarms().isEmpty()) {
                    // 过滤出进行中的报警
                    long inProgressCount = alarmResult.getAlarms().stream()
                            .filter(alarm -> Boolean.TRUE.equals(alarm.getInProgress()))
                            .count();
                    vo.setHasAlarm(inProgressCount > 0);
                } else {
                    vo.setHasAlarm(false);
                }
            } catch (Exception e) {
                // 查询报警失败时，默认为无报警
                vo.setHasAlarm(false);
            }
        }
    }

    private DeviceBaseInfoPageQuery buildQuery(String tenantId, String factoryId, DeviceBaseInfoQueryReq request) {
        DeviceBaseInfoPageQuery query = new DeviceBaseInfoPageQuery();
        query.setTenantId(tenantId);
        
        // 强制过滤工厂ID（数据隔离）
        // 如果请求中指定了工厂ID列表，需要确保只包含当前工厂
        if (request.getFactoryIds() != null && !request.getFactoryIds().isEmpty()) {
            // 验证请求的工厂ID是否包含当前工厂
            if (!request.getFactoryIds().contains(factoryId)) {
                throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), 
                    "请求的工厂ID与当前选择的工厂不一致");
            }
            query.setFactoryIds(List.of(factoryId)); // 强制只查询当前工厂
        } else {
            // 如果请求中没有指定工厂ID，强制设置为当前工厂
            query.setFactoryIds(List.of(factoryId));
        }
        
        query.setDeviceCodeLike(request.getDeviceCodeLike());
        query.setDeviceNameLike(request.getDeviceNameLike());
        query.setDeviceTypeIds(request.getDeviceTypeIds());
        query.setDeviceSubTypeNames(request.getDeviceSubTypeNames());
        query.setDeviceModelIds(request.getDeviceModelIds());
        query.setWorkshopIds(request.getWorkshopIds());
        query.setProductionLineIds(request.getProductionLineIds());
        query.setDeviceStatuses(request.getDeviceStatuses());
        query.setIsMonitored(request.getIsMonitored());
        query.setHasAlarm(request.getHasAlarm()); // 传递但不在此处处理
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setSortBy(request.getSortBy());
        query.setSortDirection(request.getSortDirection());
        return query;
    }

    /**
     * 通过 device-base 模块接口补充设备配置信息（IP/Mac 等）
     */
    private void fillDeviceNetworkInfo(String tenantId, String factoryId, DeviceBaseInfoVO target) {
        if (target == null || StringUtils.isBlank(tenantId) || StringUtils.isBlank(target.getId())) {
            return;
        }
        String finalFactoryId = StringUtils.isNotBlank(factoryId) ? factoryId : target.getFactoryId();
        if (StringUtils.isBlank(finalFactoryId)) {
            return;
        }
        try {
            com.weili.iot_portal.business.common.domain.model.device.DeviceBaseInfoVO baseInfo =
                    deviceBaseDataApi.getDeviceById(tenantId, finalFactoryId, target.getId());
            if (baseInfo != null) {
                target.setIpAddress(baseInfo.getIpAddress());
                target.setMacAddress(baseInfo.getMacAddress());
            }
        } catch (Exception ex) {
            log.warn("加载设备配置信息失败 tenantId={}, factoryId={}, deviceId={}, msg={}",
                    tenantId, finalFactoryId, target.getId(), ex.getMessage());
        }
    }
}


