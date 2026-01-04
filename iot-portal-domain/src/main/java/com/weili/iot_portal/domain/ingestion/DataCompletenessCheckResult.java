package com.weili.iot_portal.domain.ingestion;

import lombok.Data;

/**
 * @author luying
 * @className DataCompletenessCheckResult
 * @description
 * @date 2026-01-04 14:40
 **/
@Data
public class DataCompletenessCheckResult {

    private final boolean complete;
    private final String missingData; // 缺失的数据描述，如："产量数据缺失"、"理论节拍缺失"
    private final boolean missingProductionData;
    private final boolean missingTheoreticalCycle;

    public DataCompletenessCheckResult(boolean complete, String missingData,
                                       boolean missingProductionData, boolean missingTheoreticalCycle) {
        this.complete = complete;
        this.missingData = missingData;
        this.missingProductionData = missingProductionData;
        this.missingTheoreticalCycle = missingTheoreticalCycle;
    }

    public boolean isComplete() {
        return complete;
    }

    public String getMissingData() {
        return missingData;
    }
}
