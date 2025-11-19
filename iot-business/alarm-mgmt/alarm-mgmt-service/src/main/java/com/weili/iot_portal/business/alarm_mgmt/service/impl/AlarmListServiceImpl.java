package com.weili.iot_portal.business.alarm_mgmt.service.impl;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.alarm_mgmt.dal.dataobject.AlarmItemDO;
import com.weili.iot_portal.business.alarm_mgmt.dal.repository.AlarmListRepository;
import com.weili.iot_portal.business.alarm_mgmt.domain.model.AlarmItemVO;
import com.weili.iot_portal.business.alarm_mgmt.domain.model.request.AlarmListQueryReq;
import com.weili.iot_portal.business.alarm_mgmt.service.AlarmListService;
import com.weili.iot_portal.business.alarm_mgmt.service.assembler.AlarmListAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 报警列表服务实现
 */
@Service
@RequiredArgsConstructor
public class AlarmListServiceImpl implements AlarmListService {

    private final AlarmListRepository alarmListRepository;

    @Override
    public PageResult<AlarmItemVO> getAlarmList(String tenantId, AlarmListQueryReq request) {
        // 参数校验
        if (request == null || !StringUtils.hasText(request.getFactoryId())) {
            throw new IllegalArgumentException("工厂ID不能为空");
        }

        // 解析设备编号列表
        List<String> deviceCodes = parseDeviceCodes(request.getDeviceCodes());

        // 解析报警级别列表
        List<String> alarmLevels = parseAlarmLevels(request.getAlarmLevels());

        // 查询数据
        PageResult<AlarmItemDO> pageResult = alarmListRepository.selectAlarmList(
                tenantId,
                request.getFactoryId(),
                request.getWorkshopId(),
                deviceCodes,
                request.getStartTime(),
                request.getEndTime(),
                request.getIsActive(),
                alarmLevels,
                request.getPageNo() != null ? request.getPageNo() : 1,
                request.getPageSize() != null ? request.getPageSize() : 10);

        // 转换为VO
        List<AlarmItemVO> voList = AlarmListAssembler.toVOList(pageResult.getRecords());

        // 对于进行中的报警，实时计算持续时间（数据库已计算，这里可以再次确认）
        long currentTime = System.currentTimeMillis();
        voList.forEach(vo -> {
            if (Boolean.TRUE.equals(vo.getIsActive()) && vo.getDurationMs() != null) {
                // 确保持续时间是实时计算的（数据库已计算，这里再次确认）
                if (vo.getStartTime() != null) {
                    vo.setDurationMs(currentTime - vo.getStartTime());
                }
            }
        });

        return PageResult.of(voList, pageResult.getTotal(), pageResult.getPageNo(), pageResult.getPageSize());
    }

    /**
     * 解析设备编号列表
     */
    private List<String> parseDeviceCodes(String deviceCodes) {
        if (!StringUtils.hasText(deviceCodes)) {
            return Collections.emptyList();
        }
        return Arrays.stream(deviceCodes.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    /**
     * 解析报警级别列表
     */
    private List<String> parseAlarmLevels(String alarmLevels) {
        if (!StringUtils.hasText(alarmLevels)) {
            return Collections.emptyList();
        }
        return Arrays.stream(alarmLevels.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }
}

