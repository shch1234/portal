package com.weili.iot_portal.dal.repository.ingestion.impl;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookMonitorRecordDO;
import com.weili.iot_portal.dal.mapper.ingestion.WebhookMonitorRecordMapper;
import com.weili.iot_portal.dal.repository.ingestion.WebhookMonitorRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WebhookMonitorRecordRepositoryImpl implements WebhookMonitorRecordRepository {

    private final WebhookMonitorRecordMapper monitorRecordMapper;

    @Override
    public void insert(WebhookMonitorRecordDO record) {
        monitorRecordMapper.insert(record);
    }
}


