package com.weili.iot_portal.service.device.impl;

import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.dal.dataobject.device.DeviceAlarmHistoryDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceTypeRelationDO;
import com.weili.iot_portal.dal.ddd.device.DeviceAlarmHistoryQuery;
import com.weili.iot_portal.dal.ddd.device.DeviceBaseInfoPageQuery;
import com.weili.iot_portal.dal.repository.device.DeviceAlarmHistoryRepository;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceTypeRelationRepository;
import com.weili.iot_portal.domain.device.req.DeviceAlarmHistoryQueryReqVO;
import com.weili.iot_portal.domain.device.resp.AlarmHistoryRespVO;
import com.weili.iot_portal.domain.device.resp.DeviceAlarmHistoryRespVO;
import com.weili.iot_portal.service.device.IDeviceAlarmHistoryBizService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备告警历史业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAlarmHistoryBizService implements IDeviceAlarmHistoryBizService {

    private final DeviceInfoRepository deviceInfoRepository;
    private final DeviceAlarmHistoryRepository deviceAlarmHistoryRepository;
    private final DeviceTypeRelationRepository deviceTypeRelationRepository;


    @Override
    public DeviceAlarmHistoryRespVO getDeviceAlarmHistory(DeviceAlarmHistoryQueryReqVO queryReqVO) {
        AlarmHistoryRespVO currentAlarm = getCurrentAlarm(queryReqVO.getDeviceId());
        PageResult<AlarmHistoryRespVO> pageResult = queryAlarmManageList(queryReqVO);
        return DeviceAlarmHistoryRespVO.builder()
                .current(currentAlarm)
                .historyList(pageResult)
                .build();
    }

    @Override
    public PageResult<AlarmHistoryRespVO> queryAlarmManageList(DeviceAlarmHistoryQueryReqVO queryReqVO) {
        DeviceAlarmHistoryQuery historyQuery = BeanUtils.toBean(queryReqVO, DeviceAlarmHistoryQuery.class);
        
        // 处理设备编码筛选：通过设备编码查询设备ID列表
        if (StringUtils.isNotBlank(queryReqVO.getDeviceCode())) {
            DeviceBaseInfoPageQuery pageQuery = new DeviceBaseInfoPageQuery();
            pageQuery.setDeviceCode(queryReqVO.getDeviceCode());
            pageQuery.setPageSize(500);
            PageResult<DeviceInfoDO> pageResult = deviceInfoRepository.selectPage(pageQuery);
            if (CollectionUtils.isNotEmpty(pageResult.getList())) {
                historyQuery.setDeviceIds(pageResult.getList().stream().map(DeviceInfoDO::getId).distinct().toList());
            } else {
                // 如果设备编码查询不到设备，返回空结果
                return new PageResult<>(new ArrayList<>(), 0L);
            }
        }
        
        // 处理工厂ID筛选：DeviceAlarmHistoryQueryReqVO使用orgFactoryId，DeviceAlarmHistoryQuery使用factoryId
        if (queryReqVO.getOrgFactoryId() != null) {
            historyQuery.setFactoryId(queryReqVO.getOrgFactoryId());
        }
        
        PageResult<DeviceAlarmHistoryDO> pageResult = deviceAlarmHistoryRepository.selectPage(historyQuery);

        List<Long> deviceIds = pageResult.getList().stream().map(DeviceAlarmHistoryDO::getDeviceInfoId).distinct().toList();
        List<DeviceInfoDO> deviceInfoList = deviceInfoRepository.selectByIds(deviceIds);
        Map<Long, DeviceInfoDO> deviceInfoMap = deviceInfoList.stream().collect(Collectors.toMap(DeviceInfoDO::getId, d -> d));

        List<String> deviceTypeCodes = deviceInfoList.stream().map(DeviceInfoDO::getDeviceTypeCode).distinct().collect(Collectors.toList());
        List<DeviceTypeRelationDO> typeRelationList = deviceTypeRelationRepository.selectByCodes(deviceTypeCodes);
        Map<String, DeviceTypeRelationDO> typeRelationMap = typeRelationList.stream().collect(Collectors.toMap(DeviceTypeRelationDO::getTypeCode, d -> d));
        List<AlarmHistoryRespVO> historyItems = new ArrayList<>();
        for (DeviceAlarmHistoryDO alarm : pageResult.getList()) {
            if (!deviceInfoMap.containsKey(alarm.getDeviceInfoId())) {
                continue;
            }
            DeviceInfoDO deviceInfoDO = deviceInfoMap.get(alarm.getDeviceInfoId());
            AlarmHistoryRespVO vo = buildAlarmHistoryVO(alarm);
            vo.setDeviceCode(deviceInfoDO.getDeviceCode());
            vo.setDeviceName(deviceInfoDO.getDeviceName());
            vo.setDeviceType(typeRelationMap.get(deviceInfoDO.getDeviceTypeCode()) == null ?
                    deviceInfoDO.getDeviceTypeCode() :
                    typeRelationMap.get(deviceInfoDO.getDeviceTypeCode()).getDescription());
            historyItems.add(vo);
        }
        PageResult<AlarmHistoryRespVO> historyPageResult = new PageResult<>();
        historyPageResult.setTotal(pageResult.getTotal());
        historyPageResult.setPageNo(pageResult.getPageNo());
        historyPageResult.setPageSize(pageResult.getPageSize());
        historyPageResult.setList(historyItems);
        return historyPageResult;
    }

    /**
     * 获取当前告警（isActive=1）
     */
    private AlarmHistoryRespVO getCurrentAlarm(Long deviceId) {
        // 查询当前有效的告警
        List<DeviceAlarmHistoryDO> activeAlarms = deviceAlarmHistoryRepository.findActiveByDevice(null, deviceId);

        if (activeAlarms == null || activeAlarms.isEmpty()) {
            return null;
        }

        // 如果有多个活动告警，取最新的一个
        DeviceAlarmHistoryDO latestActiveAlarm = activeAlarms.get(0);
        for (DeviceAlarmHistoryDO alarm : activeAlarms) {
            if (alarm.getStartTs() != null &&
                    (latestActiveAlarm.getStartTs() == null || alarm.getStartTs() > latestActiveAlarm.getStartTs())) {
                latestActiveAlarm = alarm;
            }
        }

        return buildAlarmHistoryVO(latestActiveAlarm);
    }

    /**
     * 构建告警历史VO
     */
    private AlarmHistoryRespVO buildAlarmHistoryVO(DeviceAlarmHistoryDO alarm) {
        // 计算持续时长
        Integer durationS = calculateDuration(alarm);
        return AlarmHistoryRespVO.builder()
                .deviceId(alarm.getDeviceInfoId())
                .alarmCode(alarm.getAlarmCode())
                .alarmText(alarm.getAlarmText())
                .startTime(alarm.getStartTs())
                .endTime(alarm.getEndTs())
                .durationS(durationS)
                .isActive(alarm.getIsActive())
                .build();
    }

    /**
     * 计算持续时长（秒）
     * <p>
     * 注意：数据库中 duration_s 字段存储的是毫秒，需要转换为秒后返回
     * </p>
     */
    private Integer calculateDuration(DeviceAlarmHistoryDO alarm) {
        // 优先使用数据库中已存储的持续时长（数据库存储的是毫秒，需要转换为秒）
        if (alarm.getDurationS() != null) {
            return alarm.getDurationS() / 1000;
        }

        // 如果没有存储的持续时长，则根据开始和结束时间计算
        if (alarm.getStartTs() != null && alarm.getEndTs() != null) {
            long durationMs = alarm.getEndTs() - alarm.getStartTs();
            return (int) (durationMs / 1000);
        }

        // 如果是报警中（endTs为null），计算到当前时间
        if (alarm.getStartTs() != null && alarm.getEndTs() == null) {
            long durationMs = System.currentTimeMillis() - alarm.getStartTs();
            return (int) (durationMs / 1000);
        }

        return 0;
    }
}
