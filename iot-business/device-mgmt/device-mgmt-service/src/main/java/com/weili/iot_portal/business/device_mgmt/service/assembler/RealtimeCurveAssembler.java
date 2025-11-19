package com.weili.iot_portal.business.device_mgmt.service.assembler;

import com.weili.iot_portal.business.device_mgmt.dal.dataobject.FeedRateHistoryDO;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.SpindleSpeedHistoryDO;
import com.weili.iot_portal.business.device_mgmt.domain.model.RealtimeCurvePointVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.RealtimeCurveVO;
import com.weili.iot_portal.business.device_mgmt.service.support.TbTelemetryClient;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 曲线装配器
 */
@UtilityClass
public class RealtimeCurveAssembler {

    public static RealtimeCurveVO toVO(String metric, List<TbTelemetryClient.TelemetryPoint> points) {
        RealtimeCurveVO vo = new RealtimeCurveVO();
        vo.setMetric(metric);
        if (points == null || points.isEmpty()) {
            vo.setPoints(List.of());
            return vo;
        }
        vo.setStartTs(points.get(0).getTs());
        vo.setEndTs(points.get(points.size() - 1).getTs());
        vo.setPoints(points.stream().map(RealtimeCurveAssembler::toPoint).collect(Collectors.toList()));
        return vo;
    }

    private static RealtimeCurvePointVO toPoint(TbTelemetryClient.TelemetryPoint telemetryPoint) {
        RealtimeCurvePointVO point = new RealtimeCurvePointVO();
        point.setTs(telemetryPoint.getTs());
        point.setValue(telemetryPoint.getValue());
        return point;
    }

    public static RealtimeCurveVO toVOFromHistory(String metric, List<SpindleSpeedHistoryDO> records) {
        RealtimeCurveVO vo = new RealtimeCurveVO();
        vo.setMetric(metric);
        if (records == null || records.isEmpty()) {
            vo.setPoints(List.of());
            return vo;
        }
        vo.setStartTs(records.get(0).getSampleTs());
        vo.setEndTs(records.get(records.size() - 1).getSampleTs());
        vo.setPoints(records.stream().map(record -> {
            RealtimeCurvePointVO point = new RealtimeCurvePointVO();
            point.setTs(record.getSampleTs());
            point.setValue(record.getSpeed());
            return point;
        }).collect(Collectors.toList()));
        return vo;
    }

    public static RealtimeCurveVO toVOFromFeedHistory(String metric, List<FeedRateHistoryDO> records) {
        RealtimeCurveVO vo = new RealtimeCurveVO();
        vo.setMetric(metric);
        if (records == null || records.isEmpty()) {
            vo.setPoints(List.of());
            return vo;
        }
        vo.setStartTs(records.get(0).getSampleTs());
        vo.setEndTs(records.get(records.size() - 1).getSampleTs());
        vo.setPoints(records.stream().map(record -> {
            RealtimeCurvePointVO point = new RealtimeCurvePointVO();
            point.setTs(record.getSampleTs());
            point.setValue(record.getFeedRate());
            return point;
        }).collect(Collectors.toList()));
        return vo;
    }
}


