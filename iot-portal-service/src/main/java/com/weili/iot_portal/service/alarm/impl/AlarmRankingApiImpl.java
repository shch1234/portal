package com.weili.iot_portal.service.alarm.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.api.alarm.AlarmRankingApi;
import com.weili.iot_portal.dal.repository.alarm.AlarmListRepository;
import com.weili.iot_portal.domain.alarm.AlarmRankingItemVO;
import com.weili.iot_portal.service.alarm.assembler.AlarmRankingAssembler;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 报警排行API实现
 */
@Service
@RequiredArgsConstructor
public class AlarmRankingApiImpl implements AlarmRankingApi {

    private static final int DEFAULT_LIMIT = 5;

    private final AlarmListRepository alarmListRepository;

    @Override
    public List<AlarmRankingItemVO> getTopActiveAlarms(String factoryId, Integer limit) {
        validateParams(factoryId);
        int size = (limit == null || limit <= 0) ? DEFAULT_LIMIT : limit;
        return AlarmRankingAssembler.toList(
                alarmListRepository.selectTopActiveAlarms(factoryId, size));
    }

    private void validateParams(String factoryId) {
        if (StringUtils.isBlank(factoryId)) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "未获取到工厂信息");
        }
    }
}


