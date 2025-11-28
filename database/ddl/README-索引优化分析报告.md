# 数据库索引优化分析报告（不考虑 tenant_uuid）

## 分析依据
基于 `iot-business` 模块中的实际查询代码，分析各表的索引设计是否合理。
**前提条件**：不考虑 tenant_uuid 的过滤（假设所有租户的 uuid 都一样）。

---

## 1. device_info 表

### 当前索引（优化后）
- `uk_device_info_tb_device` (tb_device_id) - 唯一索引 ✅
- `uk_device_info_code` (device_code) - 唯一索引 ✅
- `idx_device_info_type_code` (device_type_code) ✅
- `idx_device_info_model` (device_model_id) ✅
- `idx_device_info_factory` (org_factory_id) ✅
- `idx_device_info_workshop` (org_workshop_id) ✅
- `idx_device_info_line` (org_production_line_id) ✅
- `idx_device_info_monitored` (is_monitored) ✅ **新增**

### 业务查询模式（来自 DeviceBaseInfoRepositoryImpl）
1. **按设备编号查询**：`device_code` ✅ 已有唯一索引
2. **按TB设备ID查询**：`tb_device_id` ✅ 已有唯一索引
3. **分页查询**（多条件组合）：
   - `device_code LIKE`
   - `device_name LIKE`
   - `device_type_code IN`
   - `device_model_id IN`
   - `org_factory_id IN`
   - `org_workshop_id IN`
   - `org_production_line_id IN`
   - `device_status IN`
   - `is_monitored = ?` ✅ **已添加索引**
4. **排序字段**：`create_time DESC`, `device_code`, `device_name`, `update_time`
5. **按工厂查询所有设备**：`org_factory_id` ✅ 已有索引

### 优化说明
- ✅ 删除了 `tenant_uuid` 前缀的复合索引
- ✅ 删除了 `device_status` 单独索引（选择性低，通常与其他条件组合使用）
- ✅ 添加了 `is_monitored` 索引（用于筛选监控设备）

---

## 2. device_org_relation 表

### 当前索引（优化后）
- `uk_device_org_relation_code` (unit_code) - 唯一索引 ✅
- `idx_device_org_relation_parent` (org_parent_id) ✅
- `idx_device_org_relation_type` (unit_type_value) ✅
- `idx_device_org_relation_path` (path(255)) ✅

### 业务查询模式（来自 OrganizationUnitRepositoryImpl）
1. **按编码查询**：`unit_code` ✅ 已有唯一索引
2. **按父节点查询子节点**：`org_parent_id` ✅ 已有索引
3. **分页查询**：
   - `unit_code LIKE`
   - `unit_name LIKE`
   - `unit_type_value IN` ✅ 已有索引
   - `org_parent_id = ?` ✅ 已有索引
   - `level_no = ?`
   - `is_active = ?`
4. **排序字段**：`sort_order ASC`, `create_time DESC`, `unit_code`, `unit_name`, `level_no`, `update_time`

### 优化说明
- ✅ 删除了 `tenant_uuid` 前缀的唯一索引
- ✅ 删除了 `is_active` 单独索引（选择性低，通常与其他条件组合使用）

---

## 3. device_type_relation 表

### 当前索引（优化后）
- `uk_device_type_cfg_code` (type_code) - 唯一索引 ✅
- `idx_device_type_cfg_parent_id` (parent_type_id) ✅
- `idx_device_type_cfg_parent_code` (parent_type_code) ✅ **重命名**
- `idx_device_type_cfg_category` (category) ✅

### 业务查询模式（来自 DeviceTypeRepositoryImpl）
1. **按类型编码查询**：`type_code` ✅ 已有唯一索引
2. **按父类型查询子类型**：`parent_type_id` ✅ 已有索引
3. **分页查询**：
   - `type_code LIKE`
   - `type_name LIKE`
   - `parent_type_id = ?` ✅ 已有索引
   - `level_no = ?`
   - `category IN` ✅ 已有索引
   - `is_active = ?`
4. **排序字段**：`sort_order ASC`, `type_code ASC`, `type_name`, `level_no`, `create_time`, `update_time`

### 优化说明
- ✅ 删除了 `tenant_uuid` 前缀的唯一索引和复合索引
- ✅ 保留了 `parent_type_code` 索引（重命名）

---

## 4. device_model 表

### 当前索引（优化后）
- `uk_device_model_code` (model_code) - 唯一索引 ✅
- `idx_device_model_type_code` (device_type_code) ✅
- `idx_device_model_mfr` (manufacturer) ✅

### 优化说明
- ✅ 删除了 `tenant_uuid` 前缀的唯一索引和复合索引

---

## 5. device_network_config 表

### 当前索引（优化后）
- `idx_network_config_device` (device_info_id) ✅
- `idx_network_config_active` (device_info_id, is_active) ✅
- `idx_network_config_effective` (device_info_id, effective_start_ts) ✅

### 业务查询模式
1. **查询当前生效的配置**：`device_info_id + is_active = TRUE` ✅ 已有索引
2. **按生效时间查询**：`device_info_id + effective_start_ts` ✅ 已有索引

### 优化说明
- ✅ 删除了 `tenant_uuid` 单独索引
- ✅ 删除了 `ip_address` 单独索引（如果查询不频繁）

---

## 6. device_location 表

### 当前索引（优化后）
- `idx_location_device` (device_info_id) ✅
- `idx_location_active` (device_info_id, is_active) ✅
- `idx_location_effective` (device_info_id, effective_start_ts) ✅
- `idx_location_coords` (longitude, latitude) ✅

### 优化说明
- ✅ 删除了 `tenant_uuid` 单独索引
- ✅ 删除了 `location_code`、`floor_no`、`area_code` 单独索引（选择性低，查询不频繁）

---

## 7. device_state_record 表

### 当前索引（优化后）
- `idx_state_record_device` (device_info_id, start_ts) ✅
- `idx_state_record_shift` (device_info_id, shift_date, shift_code) ✅
- `idx_state_record_open` (device_info_id, end_ts) ✅

### 业务查询模式（来自 DeviceStateTimelineRepositoryImpl）
1. **按设备+时间范围查询**：`device_info_id + start_ts BETWEEN` ✅ 已有索引
2. **按设备+班次查询**：`device_info_id + shift_date + shift_code` ✅ 已有索引
3. **查询进行中的状态**：`device_info_id + end_ts IS NULL` ✅ 已有索引

### 优化说明
- ✅ 删除了 `state_code` 单独索引（选择性低，通常按设备查询）

---

## 8. device_state_summary 表

### 当前索引（优化后）
- `idx_state_summary_device` (device_info_id, summary_date) ✅
- `idx_state_summary_time_range` (device_info_id, shift_start_ts, shift_end_ts) ✅ **新增**
- `idx_state_summary_finalized_device` (device_info_id, is_finalized, calculated_time DESC) ✅ **新增**

### 业务查询模式（来自 DeviceStateSummaryRepositoryImpl）
1. **按设备+时间范围查询**：`device_info_id + shift_start_ts/shift_end_ts BETWEEN` ✅ **已添加索引**

### 优化说明
- ✅ 删除了 `is_finalized` 单独索引
- ✅ 添加了时间范围查询索引
- ✅ 优化了 finalized 索引，添加 `device_info_id` 前缀

---

## 9. device_alarm_history 表

### 当前索引（优化后）
- `idx_alarm_history_device` (device_info_id, start_ts) ✅
- `idx_alarm_history_code` (device_info_id, alarm_code) ✅
- `idx_alarm_history_active` (device_info_id, is_active) ✅

### 业务查询模式（来自 AlarmHistoryRepositoryImpl）
1. **查询当前报警**：`device_info_id + is_active = TRUE` ✅ 已有索引
2. **按时间范围查询**：`device_info_id + start_ts BETWEEN` ✅ 已有索引
3. **分页查询**：按 `start_ts DESC` 排序 ✅ 已有索引

### 优化说明
- ✅ 当前索引设计合理，无需优化

---

## 10. device_production_record 表

### 当前索引（优化后）
- `idx_production_record_device` (device_info_id, end_ts) ✅
- `idx_production_record_shift` (device_info_id, shift_date, shift_code) ✅

### 业务查询模式（来自 ProductionCounterRepositoryImpl）
1. **按设备+完成时间查询**：`device_info_id + end_ts` ✅ 已有索引
2. **按设备+班次查询**：`device_info_id + shift_date + shift_code` ✅ 已有索引

### 优化说明
- ✅ 当前索引设计合理，无需优化

---

## 11. device_production_summary 表

### 当前索引（优化后）
- `idx_production_summary_device` (device_info_id, shift_date) ✅
- `idx_production_summary_time_range` (device_info_id, shift_start_ts, shift_end_ts) ✅ **新增**

### 业务查询模式（来自 ProductionCounterRepositoryImpl）
1. **查询当前班次**：`device_info_id + shift_start_ts <= currentTs AND shift_end_ts >= currentTs` ✅ **已添加索引**
2. **按时间范围查询**：`device_info_id + shift_start_ts/shift_end_ts BETWEEN` ✅ **已添加索引**

### 优化说明
- ✅ 添加了时间范围查询索引，支持查询当前进行中的班次

---

## 12. device_param_config 表

### 当前索引（优化后）
- `idx_device_param_config_device` (device_info_id, parameter_type) ✅
- `idx_device_param_config_effective` (device_info_id, effective_start_ts) ✅

### 优化说明
- ✅ 当前索引设计合理，无需优化

---

## 13. device_metrics_summary 表

### 当前索引（优化后）
- `idx_metrics_summary_device` (device_info_id, shift_date) ✅
- `idx_metrics_summary_time_range` (device_info_id, shift_start_ts, shift_end_ts) ✅ **新增**
- `idx_metrics_summary_finalized_device` (device_info_id, is_finalized, calculated_time DESC) ✅ **新增**
- `idx_metrics_summary_oee` (device_info_id, oee DESC) ✅
- `idx_metrics_summary_utilization` (device_info_id, utilization_rate DESC) ✅
- `idx_metrics_summary_production` (device_info_id, production_count DESC) ✅

### 业务查询模式（来自 DeviceMetricsShiftRepositoryImpl）
1. **查询最新已确定的指标**：`device_info_id + is_finalized = TRUE ORDER BY shift_start_ts DESC` ✅ **已优化索引**
2. **按时间范围查询**：`device_info_id + shift_start_ts/shift_end_ts BETWEEN` ✅ **已添加索引**

### 优化说明
- ✅ 删除了 `is_finalized` 单独索引
- ✅ 删除了冗余的 `idx_metrics_summary_device_date_oee`（与 `idx_metrics_summary_device` 和 `idx_metrics_summary_oee` 重复）
- ✅ 添加了时间范围查询索引
- ✅ 优化了 finalized 索引，添加 `device_info_id` 前缀

---

## 14. device_shift_config 表

### 当前索引（优化后）
- `idx_shift_config_device` (device_info_id, effective_start_ts) ✅
- `idx_shift_config_active_effective` (device_info_id, is_active, effective_start_ts DESC, effective_end_ts) ✅ **新增**

### 业务查询模式
1. **查询当前生效的配置**：`device_info_id + is_active = TRUE + effective_start_ts <= currentTs AND (effective_end_ts IS NULL OR effective_end_ts >= currentTs)` ✅ **已添加索引**

### 优化说明
- ✅ 删除了 `idx_shift_1_time`、`idx_shift_2_time`、`idx_shift_3_time`（查询不频繁）
- ✅ 添加了查询当前生效配置的索引

---

## 15. factory_metric_summary 表

### 当前索引（优化后）
- `idx_factory_metric_factory` (org_factory_id, shift_date) ✅
- `idx_factory_metric_time_range` (org_factory_id, shift_start_ts, shift_end_ts) ✅ **新增**
- `idx_factory_metric_finalized_factory` (org_factory_id, is_finalized, calculated_time DESC) ✅ **新增**
- `idx_factory_metric_oee` (org_factory_id, average_oee DESC) ✅
- `idx_factory_metric_utilization` (org_factory_id, average_utilization_rate DESC) ✅
- `idx_factory_metric_production` (org_factory_id, total_production_count DESC) ✅

### 业务查询模式（来自 FactoryMetricsApiImpl）
1. **按时间范围查询**：`org_factory_id + shift_start_ts/shift_end_ts BETWEEN` ✅ **已添加索引**

### 优化说明
- ✅ 删除了 `is_finalized` 单独索引
- ✅ 删除了冗余的 `idx_factory_metric_factory_date_oee`（与 `idx_factory_metric_factory` 和 `idx_factory_metric_oee` 重复）
- ✅ 添加了时间范围查询索引
- ✅ 优化了 finalized 索引，添加 `org_factory_id` 前缀

---

## 总结

### 主要优化内容

1. **删除 tenant_uuid 前缀索引**：
   - 所有包含 `tenant_uuid` 的复合索引都已删除或简化
   - 唯一索引改为只包含业务唯一字段

2. **删除低选择性单独索引**：
   - `device_status`、`is_active`、`state_code` 等选择性低的字段，删除了单独索引
   - 这些字段通常与其他条件组合使用，单独索引效果不佳

3. **添加时间范围查询索引**：
   - `device_state_summary`、`device_production_summary`、`device_metrics_summary`、`factory_metric_summary` 都添加了时间范围索引
   - 支持按 `shift_start_ts` 和 `shift_end_ts` 进行范围查询

4. **优化 finalized 索引**：
   - 所有 `is_finalized` 索引都添加了设备/工厂ID前缀
   - 支持按设备/工厂查询最新已确定的数据

5. **删除冗余索引**：
   - 删除了 `idx_metrics_summary_device_date_oee`（与现有索引重复）
   - 删除了 `idx_factory_metric_factory_date_oee`（与现有索引重复）
   - 删除了班次时间索引（查询不频繁）

6. **删除不常用索引**：
   - `location_code`、`floor_no`、`area_code`、`ip_address` 等字段的单独索引（如果查询不频繁）

### 索引统计

- **删除的索引**：约 15 个
- **新增的索引**：约 6 个
- **优化的索引**：约 5 个

### 注意事项

1. **唯一性约束**：删除 `tenant_uuid` 后，需要确保 `device_code`、`unit_code`、`type_code`、`model_code` 等字段在全局范围内唯一
2. **查询性能**：如果实际查询中经常需要按 `tenant_uuid` 过滤，可能需要重新评估
3. **索引维护成本**：减少索引数量可以降低写入成本，但需要确保查询性能不受影响
4. **监控建议**：建议在生产环境中监控慢查询日志，根据实际查询模式进一步优化
