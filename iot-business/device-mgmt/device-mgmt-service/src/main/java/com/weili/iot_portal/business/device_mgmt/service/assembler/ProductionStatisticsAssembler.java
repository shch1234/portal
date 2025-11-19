package com.weili.iot_portal.business.device_mgmt.service.assembler;

import com.weili.iot_portal.business.device_mgmt.dal.dataobject.ProductionCounterDO;
import com.weili.iot_portal.business.device_mgmt.domain.model.ProductionHistoryItemVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.ProductionHistoryVO;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 产量统计装配器
 */
@UtilityClass
public class ProductionStatisticsAssembler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

    public static ProductionHistoryVO toVO(String deviceId,
                                           List<ProductionCounterDO> list,
                                           long total,
                                           int pageNo,
                                           int pageSize) {
        ProductionHistoryVO vo = new ProductionHistoryVO();
        vo.setDeviceId(deviceId);
        vo.setTotal(total);
        vo.setPageNo(pageNo);
        vo.setPageSize(pageSize);
        vo.setTotalPages((int) Math.ceil(total / (double) pageSize));
        vo.setItems(CollectionUtils.isEmpty(list)
                ? Collections.emptyList()
                : list.stream().map(ProductionStatisticsAssembler::toItem).collect(Collectors.toList()));
        return vo;
    }

    public static ProductionHistoryItemVO toItem(ProductionCounterDO record) {
        ProductionHistoryItemVO item = new ProductionHistoryItemVO();
        if (record.getShiftDate() != null) {
            item.setShiftDate(DATE_FORMATTER.format(record.getShiftDate()));
        }
        item.setShiftCode(record.getShiftCode());
        item.setShiftStartTs(record.getShiftStartTs());
        item.setShiftEndTs(record.getShiftEndTs());
        item.setPartCount(record.getPartCount());
        item.setQualifiedCount(record.getQualifiedCount());
        item.setDefectCount(record.getDefectCount());
        item.setCountMethod(record.getCountMethod());
        item.setFinalized(record.getIsFinalized());
        item.setCalculatedTime(record.getCalculatedTime());
        return item;
    }
}


