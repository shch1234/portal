package com.weili.iot_portal.service.ingestion.handler;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.dal.dataobject.devicemng.ToolUsageHistoryDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.devicemng.ToolUsageHistoryRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.support.DeviceIdentityCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * 刀具换刀事件处理器
 * 事件类型：DEVICE_TOOL_CHANGE
 * 逻辑类似状态事件：比对上一个刀具号，关闭旧记录，插入新记录
 *
 * 事件数据要求（eventData）：
 * - previousToolNo: 上一个刀号
 * - currentToolNo: 当前刀号（必填）
 * - toolHolderNumber/toolMagazineNo（可选）
 * - toolId/toolType/compensationSnapshot（可选）
 * - timestamp 或 dataTimestamp（毫秒）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceToolChangeEventHandler implements WebhookEventHandler {

    private static final String EVENT_TYPE = "DEVICE_TOOL_CHANGE";
    private static final String LOCK_KEY_PREFIX = "device_tool_lock:";
    private static final long LOCK_TIMEOUT_SECONDS = 5;

    private final ToolUsageHistoryRepository toolUsageHistoryRepository;
    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean supports(String eventType) {
        return EVENT_TYPE.equals(eventType);
    }

    @Override
    public int order() {
        return 25; // 在轴之后，刀具信息之前
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        var eventData = request.getEventData();
        if (eventData == null) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "事件数据不能为空");
        }

        String previousToolNo = getString(eventData, "previousToolNo");
        String currentToolNo = getString(eventData, "currentToolNo");
        if (StringUtils.isBlank(currentToolNo)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "当前刀号不能为空");
        }

        Long eventTimestampMs = request.getDataTimestamp() != null ? request.getDataTimestamp() : request.getTimestamp();
        if (eventTimestampMs == null) {
            eventTimestampMs = System.currentTimeMillis();
        }
        long eventTsSeconds = eventTimestampMs / 1000;

        // 解析设备
        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getTenantId(), request.getDeviceCode(),
                        request.getDeviceId(), "DeviceToolChangeEvent");
        String deviceInfoId = identity.getDeviceId();

        // 分布式锁，避免并发换刀
        String lockKey = LOCK_KEY_PREFIX + deviceInfoId;
        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(LOCK_TIMEOUT_SECONDS));
        if (!Boolean.TRUE.equals(lockAcquired)) {
            log.warn("获取刀具锁失败，可能正在并发处理: deviceInfoId={}", deviceInfoId);
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "刀具变更处理中，请稍后重试");
        }

        try {
            // 1) 关闭旧刀记录（如果存在）
            ToolUsageHistoryDO latest = toolUsageHistoryRepository.findLatestOngoing(request.getTenantId(), deviceInfoId);
            if (latest != null) {
                if (StringUtils.isNotBlank(previousToolNo) && !previousToolNo.equalsIgnoreCase(latest.getToolNo())) {
                    log.warn("刀号不匹配: DB={}, eventPrevious={}, deviceInfoId={}", latest.getToolNo(), previousToolNo, deviceInfoId);
                }
                latest.setEndTs(eventTsSeconds);
                if (latest.getStartTs() != null) {
                    latest.setDurationS((int) (eventTsSeconds - latest.getStartTs()));
                }
                toolUsageHistoryRepository.updateById(latest);
            }

            // 2) 插入新刀记录
            ToolUsageHistoryDO newRecord = new ToolUsageHistoryDO();
            newRecord.setId(IdWorker.getIdStr());
            newRecord.setTenantUuid(request.getTenantId());
            newRecord.setDeviceInfoId(deviceInfoId);
            newRecord.setToolNo(currentToolNo);
            newRecord.setToolMagazineNo(getString(eventData, "toolHolderNumber"));
            if (StringUtils.isBlank(newRecord.getToolMagazineNo())) {
                newRecord.setToolMagazineNo(getString(eventData, "toolMagazineNo"));
            }
            newRecord.setToolId(getString(eventData, "toolId"));
            newRecord.setToolType(getString(eventData, "toolType"));
            newRecord.setStartTs(eventTsSeconds);
            newRecord.setEndTs(null);
            newRecord.setDurationS(null);
            toolUsageHistoryRepository.insert(newRecord);

            log.info("刀具变更完成: deviceId={}, prev={}, curr={}, ts={}", deviceInfoId, previousToolNo, currentToolNo, eventTsSeconds);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }
}

