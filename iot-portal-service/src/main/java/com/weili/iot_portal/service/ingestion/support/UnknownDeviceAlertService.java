package com.weili.iot_portal.service.ingestion.support;

import com.weili.basic.common.util.JsonUtils;
import com.weili.basic.redis.client.RedisClient;
import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.domain.ingestion.UnknownDeviceAlert;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 未识别设备告警服务
 */
@Service
@Slf4j
public class UnknownDeviceAlertService {

    private static final long DEFAULT_TTL_SECONDS = Duration.ofDays(1).toSeconds();

    @Resource
    private RedisClient redisClient;

    public void record(String deviceCode, String tbDeviceId, String source) {
        if (StringUtils.isBlank(deviceCode)) {
            return;
        }
        UnknownDeviceAlert alert = new UnknownDeviceAlert(
                deviceCode,
                tbDeviceId,
                StringUtils.defaultIfBlank(source, "unknown"),
                System.currentTimeMillis()
        );
        String key = String.format(RedisConstant.UNKNOWN_DEVICE_ALERT,
                deviceCode,
                alert.getTimestamp());
        redisClient.set(key, JsonUtils.toJsonString(alert), DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
        log.warn("收到未知设备数据，请检查编号或建档: deviceCode={}, tbDeviceId={}, source={}",
                deviceCode, tbDeviceId, source);
    }
}

