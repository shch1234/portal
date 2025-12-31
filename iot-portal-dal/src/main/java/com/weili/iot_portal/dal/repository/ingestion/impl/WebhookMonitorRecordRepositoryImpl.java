package com.weili.iot_portal.dal.repository.ingestion.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookMonitorRecordDO;
import com.weili.iot_portal.dal.mapper.ingestion.WebhookMonitorRecordMapper;
import com.weili.iot_portal.dal.repository.ingestion.WebhookMonitorRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Webhook 监控记录仓储实现
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class WebhookMonitorRecordRepositoryImpl implements WebhookMonitorRecordRepository {

    private final WebhookMonitorRecordMapper monitorRecordMapper;

    @Override
    public void insert(WebhookMonitorRecordDO record) {
        monitorRecordMapper.insert(record);
    }

    @Override
    public void insertBatch(List<WebhookMonitorRecordDO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        // 使用 MyBatis-Plus 的批量插入
        // 注意：MyBatis-Plus 的批量插入需要配置批量执行器（在 application.properties 中配置）
        // 或者使用自定义的批量插入 SQL
        // 这里使用循环插入，但 MyBatis-Plus 会在批量模式下自动优化
        for (WebhookMonitorRecordDO record : records) {
            monitorRecordMapper.insert(record);
        }
        log.debug("[Webhook-Monitor-Repository] 批量插入完成: count={}", records.size());
    }

    @Override
    public int deleteBefore(LocalDateTime beforeTime) {
        LambdaQueryWrapper<WebhookMonitorRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(WebhookMonitorRecordDO::getCreateTime, beforeTime);
        int deletedCount = monitorRecordMapper.delete(wrapper);
        log.info("[Webhook-Monitor-Repository] 删除指定时间之前的记录: beforeTime={}, deletedCount={}", beforeTime, deletedCount);
        return deletedCount;
    }
}


