package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.dal.dataobject.device.DeviceToolRecordDO;
import com.weili.iot_portal.dal.repository.device.DeviceToolRecordRepository;
import com.weili.iot_portal.domain.device.req.DeviceToolRecordQueryReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceToolRecordRespVO;
import com.weili.iot_portal.service.device.IDeviceToolRecordBizService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 设备刀具记录业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceToolRecordBizService implements IDeviceToolRecordBizService {

    private final DeviceToolRecordRepository deviceToolRecordRepository;

    @Override
    public List<DeviceToolRecordRespVO> getDeviceToolRecords(DeviceToolRecordQueryReqVO queryReqVO) {
        Long deviceId = queryReqVO.getDeviceInfoId();
        Long startTime = queryReqVO.getStartTime();
        Long endTime = queryReqVO.getEndTime();
        Integer limit = queryReqVO.getLimit() != null ? queryReqVO.getLimit() : 100;

        // 查询刀具记录
        List<DeviceToolRecordDO> recordList = deviceToolRecordRepository
                .selectByRange(deviceId, startTime, endTime, limit);

        List<DeviceToolRecordRespVO> result = new ArrayList<>();
        for (DeviceToolRecordDO record : recordList) {
            DeviceToolRecordRespVO vo = buildToolRecordVO(record);
            result.add(vo);
        }

        return result;
    }

    /**
     * 构建刀具记录VO
     */
    private DeviceToolRecordRespVO buildToolRecordVO(DeviceToolRecordDO record) {
        // 计算持续时长
        Long durationMs = calculateDuration(record);
        String durationStr = formatDuration(durationMs);

        return DeviceToolRecordRespVO.builder()
                .toolNo(record.getToolNo())
                .toolMagazineNo(record.getToolMagazineNo())
                .startTs(record.getStartTs())
                .endTs(record.getEndTs())
                .duration(durationStr)
                .durationMs(durationMs)
                .build();
    }

    /**
     * 计算持续时长（毫秒）
     */
    private Long calculateDuration(DeviceToolRecordDO record) {
        // 优先使用数据库中已存储的持续时长
        if (record.getDurationS() != null) {
            return record.getDurationS();
        }

        // 如果没有存储的持续时长，则根据开始和结束时间计算
        if (record.getStartTs() != null && record.getEndTs() != null) {
            return record.getEndTs() - record.getStartTs();
        }

        // 如果是正在使用中的刀具（endTs为null），返回null
        if (record.getStartTs() != null && record.getEndTs() == null) {
            return System.currentTimeMillis() - record.getStartTs();
        }

        return 0L;
    }

    /**
     * 格式化持续时长
     *
     * @param durationMs 持续时长（毫秒）
     * @return 格式化字符串，如："2小时30分钟"、"45分钟"、"30秒"
     */
    private String formatDuration(Long durationMs) {
        if (durationMs == null || durationMs == 0) {
            return "0秒";
        }

        long hours = TimeUnit.MILLISECONDS.toHours(durationMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;

        StringBuilder sb = new StringBuilder();

        if (hours > 0) {
            sb.append(hours).append("小时");
        }

        if (minutes > 0) {
            sb.append(minutes).append("分钟");
        }

        if (seconds > 0 && hours == 0) {
            // 只有当小时为0时才显示秒数，避免"2小时30分钟15秒"这样过于详细
            sb.append(seconds).append("秒");
        }

        if (sb.length() == 0) {
            return "0秒";
        }

        return sb.toString();
    }
}
