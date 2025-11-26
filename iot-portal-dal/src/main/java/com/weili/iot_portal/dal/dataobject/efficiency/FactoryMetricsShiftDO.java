package com.weili.iot_portal.dal.dataobject.efficiency;

import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 工厂级效率指标DO（数据对象）
 */
@Data
public class FactoryMetricsShiftDO extends BaseSimpleDO {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private String id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 工厂ID
     */
    private String factoryId;

    /**
     * 班次日期
     */
    private String shiftDate;

    /**
     * 班次编码
     */
    private String shiftCode;

    /**
     * 班次开始时间戳
     */
    private Long shiftStartTs;

    /**
     * 班次结束时间戳
     */
    private Long shiftEndTs;

    /**
     * 班次总时长（毫秒）
     */
    private Long shiftDurationMs;

    /**
     * 指标数据（JSONB，从数据库读取后转换为Map）
     */
    private Map<String, BigDecimal> metrics;

    /**
     * 计算依据的原始数据（JSONB）
     */
    private Map<String, Object> calculationData;

    /**
     * 参与计算的设备数量
     */
    private Integer deviceCount;

    /**
     * 是否已最终确定
     */
    private Boolean isFinalized;

    /**
     * 计算状态
     */
    private String calculationStatus;

    /**
     * 计算时间
     */
    private Long calculatedTime;

    /**
     * 计算来源
     */
    private String calculationSource;

    /**
     * 数据完整度
     */
    private BigDecimal dataCompleteness;

    /**
     * 重算时间
     */
    private Long recalculatedAt;

    /**
     * 重算原因
     */
    private String recalculationReason;

    /**
     * 重算次数
     */
    private Integer recalculationCount;
}

