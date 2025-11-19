package com.weili.iot_portal.business.device_mgmt.service.impl;

import com.weili.iot_portal.business.device_mgmt.domain.model.AxisCoordinateListVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.AxisCoordinateVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.AxisCoordinateWebhookRequest;
import com.weili.iot_portal.business.device_mgmt.service.AxisCoordinateWebhookService;
import com.weili.iot_portal.business.device_mgmt.service.support.DeviceIdentityCacheService;
import com.weili.iot_portal.service.ingestion.dispatcher.RealtimeIngestionDispatcher;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEvent;
import com.weili.iot_portal.service.ingestion.model.RealtimeIngestionEventType;
import com.weili.iot_portal.service.ingestion.support.WebhookIdempotentService;
import com.weili.iot_portal.service.ingestion.support.WebhookSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 轴坐标Webhook服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AxisCoordinateWebhookServiceImpl implements AxisCoordinateWebhookService {

    private final RealtimeIngestionDispatcher realtimeIngestionDispatcher;
    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final WebhookIdempotentService webhookIdempotentService;
    private final WebhookSecurityService webhookSecurityService;

    @Override
    public void handleAxisCoordinateWebhook(AxisCoordinateWebhookRequest request, String secret) {
        webhookSecurityService.validate(secret);

        if (!webhookIdempotentService.tryConsume(request.getMessageId())) {
            log.warn("轴坐标Webhook重复，忽略: messageId={}", request.getMessageId());
            return;
        }

        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getTenantId(), request.getDeviceCode(),
                        request.getTbDeviceId(), "axisCoordinate");
        String deviceId = identity.getDeviceId();
        String factoryId = identity.getFactoryId();

        List<AxisCoordinateVO> axes = convertAxes(request);
        AxisCoordinateListVO listVO = buildListVO(deviceId, request.getTs(), axes);
        RealtimeIngestionEvent event = RealtimeIngestionEvent.builder()
                .eventType(RealtimeIngestionEventType.AXIS_COORDINATE)
                .messageId(request.getMessageId())
                .tenantId(request.getTenantId())
                .factoryId(factoryId)
                .deviceId(deviceId)
                .timestamp(request.getTs())
                .payload(listVO)
                .build();

        realtimeIngestionDispatcher.dispatch(event);

        log.info("接收轴坐标Webhook成功: messageId={}, tenantId={}, deviceCode={}, deviceId={}, factoryId={}, axes={}, tbDeviceId={}",
                request.getMessageId(), request.getTenantId(), request.getDeviceCode(), deviceId, factoryId, axes.size(), request.getTbDeviceId());
    }

    private List<AxisCoordinateVO> convertAxes(AxisCoordinateWebhookRequest request) {
        return request.getAxes()
                .stream()
                .map(axis -> {
                    AxisCoordinateVO vo = new AxisCoordinateVO();
                    vo.setAxisName(StringUtils.upperCase(axis.getAxisName()));
                    vo.setAbsoluteCoordinate(axis.getAbsoluteCoordinate());
                    vo.setRelativeCoordinate(axis.getRelativeCoordinate());
                    vo.setMachineCoordinate(axis.getMachineCoordinate());
                    vo.setRemainingCoordinate(axis.getRemainingCoordinate());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private AxisCoordinateListVO buildListVO(String deviceId, Long ts, List<AxisCoordinateVO> axes) {
        AxisCoordinateListVO vo = new AxisCoordinateListVO();
        vo.setDeviceId(deviceId);
        vo.setQueryTime(ts);
        vo.setAxes(axes);
        return vo;
    }
}

