package com.weili.iot_portal.service.device.impl;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceAlarmHistoryDO;
import com.weili.iot_portal.dal.repository.device.DeviceAlarmHistoryRepository;
import com.weili.iot_portal.domain.device.req.DeviceAlarmHistoryQueryReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceAlarmHistoryRespVO;
import com.weili.iot_portal.service.device.IDeviceAlarmHistoryBizService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 设备告警历史业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAlarmHistoryBizService implements IDeviceAlarmHistoryBizService {

    private final DeviceAlarmHistoryRepository deviceAlarmHistoryRepository;

    @Override
    public PageResult<DeviceAlarmHistoryRespVO> getDeviceAlarmHistory(DeviceAlarmHistoryQueryReqVO queryReqVO) {
        Long deviceId = queryReqVO.getDeviceInfoId();
        Long startTime = queryReqVO.getStartTime();
        Long endTime = queryReqVO.getEndTime();
        Integer pageNo = queryReqVO.getPageNo();
        Integer pageSize = queryReqVO.getPageSize();
        Integer offset = (pageNo - 1) * pageSize;

        // 数据库分页查询
        List<DeviceAlarmHistoryDO> alarmList = deviceAlarmHistoryRepository
                .findByRangeWithPage(deviceId, startTime, endTime, offset, pageSize);

        // 查询总数
        Long total = deviceAlarmHistoryRepository
                .countByRange(deviceId, startTime, endTime);

        // 构建VO列表
        List<DeviceAlarmHistoryRespVO> items = new ArrayList<>();
        for (DeviceAlarmHistoryDO alarm : alarmList) {
            DeviceAlarmHistoryRespVO vo = buildAlarmHistoryVO(alarm);
            items.add(vo);
        }

        // 构建分页结果
        PageResult<DeviceAlarmHistoryRespVO> pageResult = new PageResult<>();
        pageResult.setTotal(total);
        pageResult.setPageNo(pageNo);
        pageResult.setPageSize(pageSize);
        pageResult.setList(items);

        return pageResult;
    }

    /**
     * 构建告警历史VO
     */
    private DeviceAlarmHistoryRespVO buildAlarmHistoryVO(DeviceAlarmHistoryDO alarm) {
        // 计算持续时长
        Integer durationS = calculateDuration(alarm);
        String durationStr = formatDuration(durationS);

        return DeviceAlarmHistoryRespVO.builder()
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
}
