package com.weili.iot_portal.service.device;

/**
 * 设备状态跨班次拆分服务
 * <p>
 * 功能：定期检查进行中的状态记录，如果跨班次则自动拆分
 * </p>
 */
public interface IDeviceStateShiftSplitService {

    /**
     * 处理跨班次拆分
     * <p>
     * 扫描所有进行中的状态记录，检查是否跨班次，如果跨班次则进行拆分
     * </p>
     *
     * @param startTsAfter 只处理开始时间在此时间之后的记录（用于性能优化）
     * @param batchSize 每批处理的记录数量
     * @return 处理结果统计
     */
    ShiftSplitResult processCrossShiftSplit(Long startTsAfter, Integer batchSize);

    /**
     * 拆分结果统计
     */
    record ShiftSplitResult(
            int totalProcessed,      // 总处理记录数
            int splitCount,          // 拆分记录数
            int skipCount,           // 跳过记录数（未跨班次）
            int errorCount           // 错误记录数
    ) {
        public static ShiftSplitResult of(int totalProcessed, int splitCount, int skipCount, int errorCount) {
            return new ShiftSplitResult(totalProcessed, splitCount, skipCount, errorCount);
        }
    }
}
