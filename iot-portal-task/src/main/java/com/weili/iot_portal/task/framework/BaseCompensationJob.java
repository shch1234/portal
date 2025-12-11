package com.weili.iot_portal.task.framework;

import com.xxl.job.core.context.XxlJobHelper;
import lombok.extern.slf4j.Slf4j;

/**
 * 补偿任务基类
 * 用于扫描未处理的数据并进行补偿处理
 * 
 * 功能：
 * - 继承 BaseBatchJob 的所有功能
 * - 统一的补偿任务日志格式
 * - 可配置扫描天数
 * 
 * 使用方式：
 * 1. 继承此类
 * 2. 实现 queryData() 方法查询未处理的数据
 * 3. 实现 processItem() 方法处理单个数据项
 * 4. 实现 getItemId() 方法返回数据项唯一标识
 */
@Slf4j
public abstract class BaseCompensationJob<T> extends BaseBatchJob<T> {
    
    /**
     * 获取补偿扫描天数（由子类实现或使用默认值）
     * 子类可以通过 @Value 注解配置
     */
    protected int getCompensationDays() {
        return 7; // 默认7天
    }
    
    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        XxlJobHelper.log("开始执行补偿任务，扫描最近 {} 天的未处理记录", getCompensationDays());
        return super.executeInternal();
    }
}

