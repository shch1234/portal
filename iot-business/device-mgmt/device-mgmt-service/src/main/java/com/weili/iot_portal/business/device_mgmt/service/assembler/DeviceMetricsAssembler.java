package com.weili.iot_portal.business.device_mgmt.service.assembler;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceMetricsShiftDO;
import com.weili.iot_portal.business.device_mgmt.domain.enums.DeviceMetricType;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceMetricHistoryVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceMetricItemVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceMetricValueVO;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 设备指标装配器
 */
@UtilityClass
public class DeviceMetricsAssembler {

    public static DeviceMetricHistoryVO toSingle(DeviceMetricsShiftDO record, List<String> metricCodes) {
        DeviceMetricHistoryVO vo = new DeviceMetricHistoryVO();
        vo.setTotal(record == null ? 0L : 1L);
        vo.setPageNo(1);
        vo.setPageSize(1);
        vo.setTotalPages(1);
        vo.setMetrics(record == null
                ? Collections.emptyList()
                : Collections.singletonList(toItem(record, metricCodes)));
        return vo;
    }

    public static DeviceMetricHistoryVO toPage(PageResult<DeviceMetricsShiftDO> pageResult,
                                               int pageNo, int pageSize,
                                               List<String> metricCodes) {
        DeviceMetricHistoryVO vo = new DeviceMetricHistoryVO();
        vo.setTotal(pageResult.getTotal());
        vo.setPageNo(pageNo);
        vo.setPageSize(pageSize);
        vo.setTotalPages((int) Math.ceil(pageResult.getTotal() / (double) pageSize));
        vo.setMetrics(CollectionUtils.isEmpty(pageResult.getList())
                ? Collections.emptyList()
                : pageResult.getList().stream()
                .map(record -> toItem(record, metricCodes))
                .collect(Collectors.toList()));
        return vo;
    }

    private static DeviceMetricItemVO toItem(DeviceMetricsShiftDO record, List<String> metricCodes) {
        DeviceMetricItemVO vo = new DeviceMetricItemVO();
        vo.setShiftId(record.getId());
        vo.setShiftStartTs(record.getShiftStartTs());
        vo.setShiftEndTs(record.getShiftEndTs());
        vo.setCalculatedTime(record.getCalculatedTime());
        vo.setValues(buildMetricValues(record.getMetrics(), metricCodes));
        return vo;
    }

    private static List<DeviceMetricValueVO> buildMetricValues(Map<String, Object> metrics, List<String> metricCodes) {
        if (metrics == null || metrics.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> filter = CollectionUtils.isEmpty(metricCodes)
                ? null
                : metricCodes.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .collect(Collectors.toCollection(HashSet::new));

        return metrics.entrySet().stream()
                .filter(entry -> filter == null || filter.contains(entry.getKey()))
                .map(entry -> buildMetricValue(entry.getKey(), entry.getValue()))
                .filter(metric -> metric.getValue() != null)
                .sorted((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()))
                .collect(Collectors.toList());
    }

    private static DeviceMetricValueVO buildMetricValue(String code, Object rawValue) {
        Double value = toDouble(rawValue);
        DeviceMetricValueVO vo = new DeviceMetricValueVO();
        vo.setCode(code);
        DeviceMetricType type = DeviceMetricType.of(code);
        if (type != null) {
            vo.setName(type.getDisplayName());
            vo.setUnit(type.getUnit());
        } else {
            vo.setName(code);
            vo.setUnit("%");
        }
        vo.setValue(value);
        return vo;
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }
}


