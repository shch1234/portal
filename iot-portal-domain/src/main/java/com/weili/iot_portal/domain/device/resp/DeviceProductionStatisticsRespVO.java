package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 设备产量统计 Response VO
 */
@Schema(description = "设备产量统计 Response VO")
@Data
public class DeviceProductionStatisticsRespVO {

    @Schema(description = "当日加工数量", example = "652")
    private Integer todayProductionCount;

    @Schema(description = "产量明细列表（按日期或班次展示）")
    private List<ProductionDetailVO> productionDetails;

    /**
     * 产量明细 VO
     */
    @Schema(description = "产量明细 VO")
    @Data
    public static class ProductionDetailVO {

        @Schema(description = "日期标识（格式：MM-DD）", example = "12-23")
        private String dateLabel;

        @Schema(description = "加工数量", example = "1024")
        private Integer productionCount;

        @Schema(description = "合格数量", example = "1000")
        private Integer qualifiedCount;

        @Schema(description = "不合格数量", example = "24")
        private Integer defectCount;
    }
}
