package com.weili.iot_portal.service.ingestion;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookFailLogDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;

import java.util.List;

/**
 * Webhook失败日志服务
 * 负责失败日志表的CRUD操作
 */
public interface WebhookFailLogService {

    /**
     * 记录失败日志（处理失败时调用）
     *
     * @param request      Webhook请求对象（可能为null）
     * @param errorType    错误类型：PROCESS/RETRY/VALIDATION等
     * @param errorMessage 错误详情
     * @param needManual   是否需要人工处理
     */
    void saveFailLog(WebhookRequest request, String errorType, String errorMessage, boolean needManual);

    /**
     * 查询待恢复的失败日志（恢复任务调用）
     * 查询条件：recovered=false 且 need_manual=false
     *
     * @param batchSize 批量大小
     * @return 待恢复的失败日志列表
     */
    List<WebhookFailLogDO> getUnrecoveredLogs(int batchSize);

    /**
     * 标记为已恢复
     *
     * @param failLogId 失败日志ID
     */
    void markRecovered(String failLogId);

    /**
     * 根据消息ID查询失败日志
     *
     * @param messageId 消息ID
     * @return 失败日志，如果不存在返回null
     */
    WebhookFailLogDO getByMessageId(String messageId);

    /**
     * 清理历史失败日志
     * 
     * @param beforeDays 清理多少天前的数据
     * @param recovered 是否已恢复（null 表示不限制）
     * @param needManual 是否需要人工处理（null 表示不限制）
     * @return 清理的记录数
     */
    int cleanup(int beforeDays, Boolean recovered, Boolean needManual);
}

