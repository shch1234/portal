package com.weili.iot_portal.service.alarm.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.alarm.AlarmDeviceStatisticsDO;
import com.weili.iot_portal.dal.repository.alarm.AlarmStatisticsRepository;
import com.weili.iot_portal.domain.alarm.AlarmDeviceItemVO;
import com.weili.iot_portal.domain.alarm.CurrentAlarmDeviceCountVO;
import com.weili.iot_portal.domain.alarm.request.CurrentAlarmDeviceQueryReq;
import com.weili.iot_portal.service.alarm.AlarmStatisticsService;
import com.weili.iot_portal.service.alarm.assembler.AlarmStatisticsAssembler;
import com.weili.iot_portal.service.realtime.RealtimePushService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 报警统计服务实现
 */
@Service
@RequiredArgsConstructor
public class AlarmStatisticsServiceImpl implements AlarmStatisticsService {

    private final AlarmStatisticsRepository alarmStatisticsRepository;
    private final RealtimePushService realtimePushService;

    @Override
    public CurrentAlarmDeviceCountVO getCurrentAlarmDeviceCount(String factoryId, String workshopId) {
        // 参数校验
        if (StringUtils.isBlank(factoryId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "工厂ID不能为空");
        }

        // 查询当前报警设备统计（已去重设备ID）
        List<AlarmDeviceStatisticsDO> statistics = alarmStatisticsRepository.countCurrentAlarmDevices(
                factoryId, workshopId);

        // 获取工厂和车间名称（如果查询的是单个车间）
        String factoryName = null;
        String workshopName = null;
        if (!statistics.isEmpty()) {
            // 从第一条记录中获取工厂和车间名称
            AlarmDeviceStatisticsDO first = statistics.get(0);
            // 如果有车间ID，则使用统计结果中的车间名称
            if (StringUtils.isNotBlank(workshopId)) {
                workshopName = first.getWorkshopName();
            }
            // TODO: 如果需要工厂名称，可以从组织单元表查询或从设备管理模块API获取
            // 这里简化处理，工厂名称可以从其他地方获取
        }

        CurrentAlarmDeviceCountVO result = AlarmStatisticsAssembler.toCountVO(
                factoryId, factoryName, workshopId, workshopName, statistics);

        // 推送实时更新（异步推送，不阻塞返回）
        pushAlarmCountChanged(factoryId, workshopId, result);

        return result;
    }

    /**
     * 推送报警数量变化（供Webhook接收时调用）
     */
    public void pushAlarmCountChanged(String factoryId, String workshopId, CurrentAlarmDeviceCountVO count) {
        try {
            // 构建主题键：alarm.count:tenantId:factoryId:workshopId
            String topicKey = realtimePushService.buildTopicKey("alarm.count", factoryId, workshopId);
            // 推送数据变化消息
            realtimePushService.pushDataChanged(topicKey, count);
        } catch (Exception e) {
            // 推送失败不影响主流程，只记录日志
            // log.error("推送报警数量变化失败", e);
        }
    }

    @Override
    public PageResult<AlarmDeviceItemVO> getCurrentAlarmDeviceList(CurrentAlarmDeviceQueryReq request) {
        // 参数校验
        if (StringUtils.isBlank(request.getFactoryId())) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "工厂ID不能为空");
        }

        // 设置默认分页参数
        int pageNo = request.getPageNo() != null && request.getPageNo() > 0 ? request.getPageNo() : 1;
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 10;

        // 查询当前报警设备列表（分页）
        PageResult<AlarmDeviceStatisticsDO> pageResult = alarmStatisticsRepository.selectCurrentAlarmDevices(
                request.getFactoryId(), request.getWorkshopId(), pageNo, pageSize);

        // 转换为VO
        List<AlarmDeviceItemVO> items = AlarmStatisticsAssembler.toDeviceItemVOList(pageResult.getList());
        return new PageResult<>(items, pageResult.getTotal());
    }
}

