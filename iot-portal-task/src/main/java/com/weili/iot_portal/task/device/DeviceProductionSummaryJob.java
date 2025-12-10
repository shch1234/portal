package com.weili.iot_portal.task.device;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.weili.iot_portal.dal.dataobject.device.DeviceProductionSummaryDO;
import com.weili.iot_portal.service.shift.IShiftConfigService;
import com.weili.iot_portal.service.shift.model.ShiftTimeRange;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.context.XxlJobHelper;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceProductionSummaryRepository;
import com.weili.iot_portal.dal.repository.device.DeviceProductionRecordRepository;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备产量汇总定时任务
 * 按班次统计 device_production_record 到 device_production_summary
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceProductionSummaryJob extends BaseScheduledJob {

    @Value("${shift.summary.delay-minutes:5}")
    private int delayMinutes;
    private final DeviceInfoRepository deviceInfoRepository;
    private final DeviceProductionRecordRepository productionRecordRepository;
    private final DeviceProductionSummaryRepository deviceProductionSummaryRepository;
    private final IShiftConfigService shiftConfigurationService;

    @Override
    protected String getJobName() {
        return "设备产量汇总任务";
    }

    @Override
    @XxlJob("deviceProductionSummaryJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        long nowSeconds = System.currentTimeMillis() / 1000;
        long statisticsTimeSeconds = nowSeconds - delayMinutes * 60L;
        long statisticsTimeMs = statisticsTimeSeconds * 1000;
        XxlJobHelper.log("产量汇总统计时间: {}, 延迟: {} 分钟", Instant.ofEpochSecond(statisticsTimeSeconds), delayMinutes);

        List<DeviceInfoDO> allDevices = queryAllDevices();
        if (allDevices.isEmpty()) {
            return JobExecutionResult.empty();
        }

        int success = 0, skip = 0, error = 0;
        Map<String, Map<String, List<DeviceInfoDO>>> grouped = allDevices.stream()
                .filter(d -> StringUtils.isNotBlank(d.getOrgFactoryId()))
                .collect(Collectors.groupingBy(DeviceInfoDO::getTenantUuid,
                        Collectors.groupingBy(DeviceInfoDO::getOrgFactoryId)));

        for (Map.Entry<String, Map<String, List<DeviceInfoDO>>> tenantEntry : grouped.entrySet()) {
            for (Map.Entry<String, List<DeviceInfoDO>> factoryEntry : tenantEntry.getValue().entrySet()) {
                List<DeviceInfoDO> devices = factoryEntry.getValue();
                for (DeviceInfoDO device : devices) {
                    try {
                        boolean processed = processDevice(device, statisticsTimeMs);
                        if (processed) {
                            success++;
                        } else {
                            skip++;
                        }
                    } catch (Exception e) {
                        error++;
                        log.error("产量汇总失败 deviceId={}", device.getId(), e);
                    }
                }
            }
        }

        return JobExecutionResult.of(success, skip, error);
    }

    private List<DeviceInfoDO> queryAllDevices() {
        return deviceInfoRepository.findAllActive();
    }

    /**
     * 处理单台设备在统计时间点前已结束的班次
     */
    private boolean processDevice(DeviceInfoDO device, long statisticsTimeMs) {
        ShiftTimeRange range = shiftConfigurationService.calculateShiftRange(device.getOrgFactoryId(), device.getId(), statisticsTimeMs);
        if (range == null || range.getEndTs() == null) {
            return false;
        }
        if (range.getEndTs() > statisticsTimeMs) {
            // 班次尚未结束
            return false;
        }

        long shiftStartSec = range.getStartTs() / 1000;
        long shiftEndSec = range.getEndTs() / 1000;
        long count = productionRecordRepository.countCompletedInRange(device.getId(), shiftStartSec, shiftEndSec);

        LocalDate shiftDate = Instant.ofEpochMilli(range.getStartTs()).atZone(ZoneId.systemDefault()).toLocalDate();
        upsertSummary(device.getId(), shiftDate, range.getShiftCode(), shiftStartSec, shiftEndSec, count, statisticsTimeMs / 1000);
        return true;
    }

    private void upsertSummary(String deviceId, LocalDate shiftDate, String shiftCode,
                               long shiftStartSec, long shiftEndSec, long partCount, long calculatedTimeSec) {
        DeviceProductionSummaryDO existing = deviceProductionSummaryRepository.findByShift(deviceId, shiftDate, shiftCode);
        if (existing == null) {
            DeviceProductionSummaryDO summary = new DeviceProductionSummaryDO();
            summary.setId(IdWorker.getIdStr());
            summary.setDeviceInfoId(deviceId);
            summary.setShiftDate(shiftDate);
            summary.setShiftCode(shiftCode);
            summary.setShiftStartTs(shiftStartSec);
            summary.setShiftEndTs(shiftEndSec);
            summary.setPartCount((int) partCount);
            summary.setQualifiedCount((int) partCount);
            summary.setDefectCount(0);
            summary.setIsFinalized(true);
            summary.setCalculatedTime(calculatedTimeSec);
            deviceProductionSummaryRepository.insert(summary);
        } else {
            existing.setShiftStartTs(shiftStartSec);
            existing.setShiftEndTs(shiftEndSec);
            existing.setPartCount((int) partCount);
            existing.setQualifiedCount((int) partCount);
            existing.setDefectCount(existing.getDefectCount() == null ? 0 : existing.getDefectCount());
            existing.setIsFinalized(true);
            existing.setCalculatedTime(calculatedTimeSec);
            deviceProductionSummaryRepository.update(existing);
        }
    }
}


