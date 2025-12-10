package com.weili.iot_portal.service.devicemng.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.dal.repository.devicemng.FeedRateHistoryRepository;
import com.weili.iot_portal.dal.repository.devicemng.SpindleSpeedHistoryRepository;
import com.weili.iot_portal.domain.devicemng.RealtimeCurveVO;
import com.weili.iot_portal.domain.devicemng.RealtimeMetricValueVO;
import com.weili.iot_portal.service.assembler.RealtimeCurveAssembler;
import com.weili.iot_portal.service.devicemng.RealtimeCurveService;
import com.weili.iot_portal.service.support.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 实时曲线服务实现
 */
@Service
@RequiredArgsConstructor
public class RealtimeCurveServiceImpl implements RealtimeCurveService {

    private static final String METRIC_SPINDLE_LOAD = "spindleLoad";
    private static final String METRIC_FEED_RATE = "feedRate";

    private final TbTelemetryClient telemetryClient;
    private final DeviceFactoryValidator deviceFactoryValidator;
    private final SpindleSpeedCache spindleSpeedCache;
    private final SpindleSpeedHistoryRepository spindleSpeedHistoryRepository;
    private final FeedRateCache feedRateCache;
    private final FeedOverrideCache feedOverrideCache;
    private final FeedRateHistoryRepository feedRateHistoryRepository;

    @Override
    public RealtimeCurveVO getRealtimeSpindleLoad(String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);
        long endTs = System.currentTimeMillis();
        long startTs = endTs - 10 * 60 * 1000L;
        List<TbTelemetryClient.TelemetryPoint> points = telemetryClient
                .queryTelemetry(deviceId, METRIC_SPINDLE_LOAD, startTs, endTs, 200);
        return RealtimeCurveAssembler.toVO(METRIC_SPINDLE_LOAD, points);
    }

    @Override
    public RealtimeCurveVO getHistorySpindleLoad(String factoryId, String deviceId, Long startTs, Long endTs) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);
        if (startTs == null || endTs == null || startTs >= endTs) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "无效的时间范围");
        }
        List<TbTelemetryClient.TelemetryPoint> points = telemetryClient
                .queryTelemetry(deviceId, METRIC_SPINDLE_LOAD, startTs, endTs, 1000);
        return RealtimeCurveAssembler.toVO(METRIC_SPINDLE_LOAD, points);
    }

    @Override
    public RealtimeCurveVO getRealtimeSpindleSpeed(String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);
        return spindleSpeedCache.getLatest(deviceId)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                        "暂未收到主轴转速数据，请稍后重试"));
    }

    @Override
    public RealtimeCurveVO getHistorySpindleSpeed(String factoryId, String deviceId, Long startTs, Long endTs) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);
        if (startTs == null || endTs == null || startTs >= endTs) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "无效的时间范围");
        }
        return RealtimeCurveAssembler.toVOFromHistory("spindleSpeed",
                spindleSpeedHistoryRepository.selectByRange(deviceId, startTs, endTs, 2000));
    }

    @Override
    public RealtimeCurveVO getRealtimeFeedRate(String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);
        return feedRateCache.getLatest(deviceId)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                        "暂未收到进给率数据，请稍后重试"));
    }

    @Override
    public RealtimeCurveVO getHistoryFeedRate(String factoryId, String deviceId, Long startTs, Long endTs) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);
        if (startTs == null || endTs == null || startTs >= endTs) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "无效的时间范围");
        }
        return RealtimeCurveAssembler.toVOFromFeedHistory(METRIC_FEED_RATE,
                feedRateHistoryRepository.selectByRange(deviceId, startTs, endTs, 2000));
    }

    @Override
    public RealtimeMetricValueVO getRealtimeFeedOverride(String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);
        return feedOverrideCache.get(deviceId)
                .orElseThrow(() -> new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                        "暂未收到倍率数据，请稍后重试"));
    }
}


