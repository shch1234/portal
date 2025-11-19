package com.weili.iot_portal.business.device_mgmt.service.assembler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceParameterDO;
import com.weili.iot_portal.business.device_mgmt.domain.enums.DeviceParameterTypeEnum;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceParameterHistoryItemVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceParameterVO;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 设备参数装配器
 */
@UtilityClass
public class DeviceParameterAssembler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public DeviceParameterVO toCurrentVO(String deviceId, List<DeviceParameterDO> records) {
        if (CollectionUtils.isEmpty(records)) {
            DeviceParameterVO empty = new DeviceParameterVO();
            empty.setDeviceId(deviceId);
            return empty;
        }
        DeviceParameterVO vo = new DeviceParameterVO();
        vo.setDeviceId(deviceId);
        Long latestTs = null;
        String latestBy = null;
        for (DeviceParameterDO record : records) {
            applyValue(vo, record);
            if (latestTs == null || record.getEffectiveStartTs() > latestTs) {
                latestTs = record.getEffectiveStartTs();
                latestBy = record.getUpdatedBy();
            }
        }
        vo.setUpdatedTime(latestTs);
        vo.setUpdatedBy(latestBy);
        return vo;
    }

    public DeviceParameterHistoryItemVO toHistoryItem(Long ts, List<DeviceParameterDO> records) {
        DeviceParameterHistoryItemVO item = new DeviceParameterHistoryItemVO();
        item.setTimestamp(ts);
        for (DeviceParameterDO record : records) {
            applyValue(item, record);
            if (StringUtils.isBlank(item.getUpdatedBy()) && StringUtils.isNotBlank(record.getUpdatedBy())) {
                item.setUpdatedBy(record.getUpdatedBy());
            }
        }
        return item;
    }

    private void applyValue(DeviceParameterVO vo, DeviceParameterDO record) {
        switch (DeviceParameterTypeEnum.valueOf(record.getParameterType())) {
            case THEORETICAL_CYCLE -> vo.setTheoreticalCycleHours(toDouble(record.getParameterValue()));
            case PLANNED_DOWNTIME -> vo.setPlannedDowntimeHours(toDouble(record.getParameterValue()));
            case SHIFT_MODE -> vo.setShiftMode(toInteger(record.getParameterValue()));
            case SHIFT_START_TIMES -> vo.setShiftStartTimes(parseShiftStartTimes(record.getParameterText()));
            case REMARK -> vo.setRemark(record.getParameterText());
            default -> throw new IllegalStateException("Unexpected value: " + record.getParameterType());
        }
    }

    private void applyValue(DeviceParameterHistoryItemVO item, DeviceParameterDO record) {
        switch (DeviceParameterTypeEnum.valueOf(record.getParameterType())) {
            case THEORETICAL_CYCLE -> item.setTheoreticalCycleHours(toDouble(record.getParameterValue()));
            case PLANNED_DOWNTIME -> item.setPlannedDowntimeHours(toDouble(record.getParameterValue()));
            case SHIFT_MODE -> item.setShiftMode(toInteger(record.getParameterValue()));
            case SHIFT_START_TIMES -> item.setShiftStartTimes(parseShiftStartTimes(record.getParameterText()));
            case REMARK -> item.setRemark(record.getParameterText());
            default -> throw new IllegalStateException("Unexpected value: " + record.getParameterType());
        }
    }

    private Double toDouble(BigDecimal val) {
        return val == null ? null : val.doubleValue();
    }

    private Integer toInteger(BigDecimal val) {
        return val == null ? null : val.intValue();
    }

    private List<String> parseShiftStartTimes(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(json,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}


