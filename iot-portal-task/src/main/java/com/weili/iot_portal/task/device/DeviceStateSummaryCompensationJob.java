package com.weili.iot_portal.task.devicemng;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateSummaryDO;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.mapper.devicemng.DeviceStateSummaryMapper;
import com.weili.iot_portal.dal.mapper.devicebase.DeviceBaseInfoMapper;
import com.weili.iot_portal.dal.repository.devicemng.DeviceStateTimelineRepository;
import com.weili.iot_portal.dal.repository.devicemng.ShiftConfigurationRepository;
import com.weili.iot_portal.service.support.ShiftConfigurationService;
import com.weili.iot_portal.service.support.ShiftTimeRange;
import com.weili.iot_portal.task.framework.BaseCompensationJob;
import com.weili.iot_portal.task.framework.ItemProcessResult;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 设备状态汇总补偿任务
 * 
 * 功能：扫描未处理的班次汇总记录，进行补偿处理
 * 
 * 配置说明：
 * - shift.summary.compensation.days: 扫描最近N天的未处理记录（默认7天）
 * - job.batch.size: 每批处理数量（默认50）
 * - 建议在XXL-Job中配置cron表达式，例如：每小时执行一次
 * 
 * 使用框架：BaseCompensationJob
 * - 自动异常处理
 * - 自动统计收集
 * - 自动日志记录
 * - 分批处理支持
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceStateSummaryCompensationJob extends BaseCompensationJob<DeviceStateSummaryDO> {

    @Value("${shift.summary.compensation.days:7}")
    private int compensationDays;
    
    @Override
    protected int getCompensationDays() {
        return compensationDays;
    }

    private final DeviceStateSummaryMapper stateSummaryMapper;
    private final DeviceBaseInfoMapper deviceBaseInfoMapper;
    private final ShiftConfigurationRepository shiftConfigurationRepository;
    private final ShiftConfigurationService shiftConfigurationService;
    private final DeviceStateTimelineRepository stateTimelineRepository;
    private final DeviceStateSummaryJob summaryJob;

    @Override
    protected String getJobName() {
        return "设备状态汇总补偿任务";
    }

    @Override
    @XxlJob("deviceStateSummaryCompensationJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected List<DeviceStateSummaryDO> queryData() {
        // 扫描未处理的汇总记录
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(compensationDays);

        LambdaQueryWrapper<DeviceStateSummaryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceStateSummaryDO::getIsFinalized, false)
               .between(DeviceStateSummaryDO::getSummaryDate, startDate, endDate)
               .orderByAsc(DeviceStateSummaryDO::getSummaryDate)
               .orderByAsc(DeviceStateSummaryDO::getShiftCode);

        List<DeviceStateSummaryDO> unprocessedSummaries = stateSummaryMapper.selectList(wrapper);
        
        if (unprocessedSummaries != null && !unprocessedSummaries.isEmpty()) {
            XxlJobHelper.log("发现 {} 条未处理的汇总记录", unprocessedSummaries.size());
        }
        
        return unprocessedSummaries;
    }

    @Override
    protected ItemProcessResult processItem(DeviceStateSummaryDO summary) {
        try {
            boolean processed = recalculateSummary(summary);
            if (processed) {
                return ItemProcessResult.success();
            } else {
                return ItemProcessResult.skipped("设备不存在或未配置班次");
            }
        } catch (Exception e) {
            log.error("补偿处理失败: summaryId={}, deviceId={}, shiftDate={}, shiftCode={}", 
                    summary.getId(), summary.getDeviceInfoId(), 
                    summary.getSummaryDate(), summary.getShiftCode(), e);
            return ItemProcessResult.failed(e.getMessage());
        }
    }

    @Override
    protected String getItemId(DeviceStateSummaryDO item) {
        return item.getId();
    }

    /**
     * 重新计算汇总记录
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean recalculateSummary(DeviceStateSummaryDO summary) {
        // 查询设备信息
        DeviceBaseInfoDO device = deviceBaseInfoMapper.selectById(summary.getDeviceInfoId());
        if (device == null || Boolean.TRUE.equals(device.getDeleted())) {
            log.warn("设备不存在或已删除: deviceId={}", summary.getDeviceInfoId());
            return false;
        }

        // 查询班次配置
        long shiftStartTs = summary.getShiftStartTs() * 1000L;
        Optional<com.weili.iot_portal.dal.dataobject.devicemng.ShiftConfigurationDO> configOpt = 
                shiftConfigurationRepository.findActiveByDeviceAndTime(
                        summary.getDeviceInfoId(), shiftStartTs);

        if (configOpt.isEmpty()) {
            log.warn("设备未配置班次: deviceId={}", summary.getDeviceInfoId());
            return false;
        }

        // 计算班次时间范围
        ShiftTimeRange shiftRange = shiftConfigurationService.calculateShiftRange(
                device.getOrgFactoryId(), 
                summary.getDeviceInfoId(), 
                shiftStartTs);

        if (shiftRange == null) {
            log.warn("无法计算班次时间范围: deviceId={}, shiftDate={}, shiftCode={}", 
                    summary.getDeviceInfoId(), summary.getSummaryDate(), summary.getShiftCode());
            return false;
        }

        // 查询班次内的状态记录
        List<com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateTimelineDO> stateRecords = 
                stateTimelineRepository.selectByRange(
                        summary.getDeviceInfoId(), 
                        shiftRange.getStartTs() / 1000, 
                        shiftRange.getEndTs() / 1000);

        // 重新计算状态统计
        java.util.Map<String, DeviceStateSummaryJob.StateStatistics> stateStats = 
                summaryJob.calculateStateStatisticsInternal(
                        stateRecords, 
                        shiftRange.getStartTs() / 1000, 
                        shiftRange.getEndTs() / 1000);

        // 更新汇总记录
        summaryJob.saveOrUpdateSummary(
                summary.getDeviceInfoId(), 
                device.getOrgFactoryId(), 
                summary.getSummaryDate(), 
                shiftRange, 
                stateStats);

        return true;
    }
}

