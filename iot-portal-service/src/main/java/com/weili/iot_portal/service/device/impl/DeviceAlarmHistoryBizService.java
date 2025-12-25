package com.weili.iot_portal.service.device.impl;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceAlarmHistoryDO;
import com.weili.iot_portal.dal.repository.device.DeviceAlarmHistoryRepository;
import com.weili.iot_portal.domain.device.req.AlarmManageQueryReqVO;
import com.weili.iot_portal.domain.device.req.DeviceAlarmHistoryQueryReqVO;
import com.weili.iot_portal.domain.device.resp.AlarmManageRespVO;
import com.weili.iot_portal.domain.device.resp.DeviceAlarmHistoryRespVO;
import com.weili.iot_portal.service.device.IDeviceAlarmHistoryBizService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备告警历史业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAlarmHistoryBizService implements IDeviceAlarmHistoryBizService {

    private final DeviceAlarmHistoryRepository deviceAlarmHistoryRepository;

    @Override
    public DeviceAlarmHistoryRespVO getDeviceAlarmHistory(DeviceAlarmHistoryQueryReqVO queryReqVO) {
        Long deviceId = queryReqVO.getDeviceId();
        Long startTime = queryReqVO.getStartTime();
        Long endTime = queryReqVO.getEndTime();
        Integer pageNo = queryReqVO.getPageNo();
        Integer pageSize = queryReqVO.getPageSize();
        Integer offset = (pageNo - 1) * pageSize;

        // 查询当前告警（isActive=1）
        DeviceAlarmHistoryRespVO.AlarmHistory currentAlarm = getCurrentAlarm(deviceId);

        // 数据库分页查询历史告警（所有记录，包括已解除的）
        List<DeviceAlarmHistoryDO> alarmList = deviceAlarmHistoryRepository
                .findByRangeWithPage(deviceId, startTime, endTime, offset, pageSize);

        // 查询总数
        Long total = deviceAlarmHistoryRepository
                .countByRange(deviceId, startTime, endTime);

        // 构建历史告警VO列表
        List<DeviceAlarmHistoryRespVO.AlarmHistory> historyItems = new ArrayList<>();
        for (DeviceAlarmHistoryDO alarm : alarmList) {
            DeviceAlarmHistoryRespVO.AlarmHistory vo = buildAlarmHistoryVO(alarm);
            historyItems.add(vo);
        }

        // 构建历史告警分页结果
        PageResult<DeviceAlarmHistoryRespVO.AlarmHistory> historyPageResult = new PageResult<>();
        historyPageResult.setTotal(total);
        historyPageResult.setPageNo(pageNo);
        historyPageResult.setPageSize(pageSize);
        historyPageResult.setList(historyItems);

        // 构建最终响应
        return DeviceAlarmHistoryRespVO.builder()
                .current(currentAlarm)
                .historyList(historyPageResult)
                .build();
    }

    @Override
    public PageResult<AlarmManageRespVO> queryAlarmManageList(AlarmManageQueryReqVO queryReqVO) {
        Integer pageNo = queryReqVO.getPageNo();
        Integer pageSize = queryReqVO.getPageSize();
        Integer offset = (pageNo - 1) * pageSize;

        String deviceCode = queryReqVO.getDeviceCode();
        String deviceType = queryReqVO.getDeviceType();
        Integer isActive = queryReqVO.getIsActive();

        // 将时间字符串转换为时间戳
        Long startTime = convertToTimestamp(queryReqVO.getReportTimeStart());
        Long endTime = convertToTimestamp(queryReqVO.getReportTimeEnd());

        // 查询总数
        Long total = deviceAlarmHistoryRepository.countAlarmManageList(
                deviceCode, deviceType, isActive, startTime, endTime);

        // 查询列表
        List<AlarmManageRespVO> list = deviceAlarmHistoryRepository.selectAlarmManageList(
                deviceCode, deviceType, isActive, startTime, endTime, offset, pageSize);

        // 设置序号
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setRowNum(offset + i + 1);
        }

        // 构建分页结果
        PageResult<AlarmManageRespVO> result = new PageResult<>();
        result.setTotal(total);
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        result.setList(list);

        return result;
    }

    /**
     * 获取当前告警（isActive=1）
     */
    private DeviceAlarmHistoryRespVO.AlarmHistory getCurrentAlarm(Long deviceId) {
        // 查询当前有效的告警
        List<DeviceAlarmHistoryDO> activeAlarms = deviceAlarmHistoryRepository
                .findActiveByDevice(null, deviceId);

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
    private DeviceAlarmHistoryRespVO.AlarmHistory buildAlarmHistoryVO(DeviceAlarmHistoryDO alarm) {
        // 计算持续时长
        Integer durationS = calculateDuration(alarm);
        String durationStr = formatDuration(durationS);

        return DeviceAlarmHistoryRespVO.AlarmHistory.builder()
                .alarmCode(alarm.getAlarmCode())
                .alarmText(alarm.getAlarmText())
                .startTs(alarm.getStartTs())
                .endTs(alarm.getEndTs())
                .duration(durationStr)
                .durationS(durationS)
                .isActive(alarm.getIsActive())
                .build();
    }

    /**
     * 计算持续时长（秒）
     */
    private Integer calculateDuration(DeviceAlarmHistoryDO alarm) {
        // 优先使用数据库中已存储的持续时长
        if (alarm.getDurationS() != null) {
            return alarm.getDurationS();
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

    /**
     * 格式化持续时长
     *
     * @param durationS 持续时长（秒）
     * @return 格式化字符串，如："2小时30分钟"、"45分钟"、"30秒"
     */
    private String formatDuration(Integer durationS) {
        if (durationS == null || durationS == 0) {
            return "0秒";
        }

        long hours = durationS / 3600;
        long minutes = (durationS % 3600) / 60;
        long seconds = durationS % 60;

        StringBuilder sb = new StringBuilder();

        if (hours > 0) {
            sb.append(hours).append("小时");
        }

        if (minutes > 0) {
            sb.append(minutes).append("分钟");
        }

        if (seconds > 0 && hours == 0) {
            // 只有当小时为0时才显示秒数
            sb.append(seconds).append("秒");
        }

        if (sb.length() == 0) {
            return "0秒";
        }

        return sb.toString();
    }

    /**
     * 将时间字符串转换为时间戳（毫秒）
     *
     * @param timeStr 时间字符串（格式：yyyy-MM-dd HH:mm:ss）
     * @return 时间戳（毫秒），如果转换失败则返回null
     */
    private Long convertToTimestamp(String timeStr) {
        if (!StringUtils.hasText(timeStr)) {
            return null;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            return sdf.parse(timeStr).getTime();
        } catch (ParseException e) {
            log.error("时间字符串转换失败: {}", timeStr, e);
            return null;
        }
    }
}
