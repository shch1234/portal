package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 工厂级联 Response VO（工厂-车间-产线三层结构）
 */
@Schema(description = "工厂级联 Response VO")
@Data
public class DeviceOrgRelationSubRespVO {

    @Schema(description = "工厂ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "工厂编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    @Schema(description = "工厂名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitName;

    @Schema(description = "车间列表")
    private List<WorkshopVO> workshops;

    /**
     * 车间 VO
     */
    @Schema(description = "车间 VO")
    @Data
    public static class WorkshopVO {

        @Schema(description = "车间ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private String id;

        @Schema(description = "车间编码", requiredMode = Schema.RequiredMode.REQUIRED)
        private String unitCode;

        @Schema(description = "车间名称", requiredMode = Schema.RequiredMode.REQUIRED)
        private String unitName;

        @Schema(description = "产线列表")
        private List<ProductionLineVO> productionLines;
    }

    /**
     * 产线 VO
     */
    @Schema(description = "产线 VO")
    @Data
    public static class ProductionLineVO {

        @Schema(description = "产线ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private String id;

        @Schema(description = "产线编码", requiredMode = Schema.RequiredMode.REQUIRED)
        private String unitCode;

        @Schema(description = "产线名称", requiredMode = Schema.RequiredMode.REQUIRED)
        private String unitName;
    }
}

