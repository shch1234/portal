# 雪花算法 ID 迁移指南

## 一、使用雪花算法生成 ID 的注意事项

### 1.1 数据类型变更

**原设计（UUID）：**
- 数据类型：`CHAR(36)` 或 `VARCHAR(36)`
- 存储空间：36 字节（固定）
- 示例：`550e8400-e29b-41d4-a716-446655440000`

**雪花算法（Snowflake）：**
- 数据类型：`BIGINT`（64位整数）
- 存储空间：8 字节
- 取值范围：-9,223,372,036,854,775,808 到 9,223,372,036,854,775,807
- 示例：`1234567890123456789`

### 1.2 关键注意事项

#### ✅ 优势
1. **存储空间更小**：BIGINT 8字节 vs CHAR(36) 36字节，节省约 78% 存储空间
2. **查询性能更好**：整数比较比字符串比较快，索引效率更高
3. **有序性**：雪花算法生成的ID按时间有序，有利于数据库物理存储优化
4. **分布式友好**：无需中心化ID生成服务

#### ⚠️ 注意事项
1. **外部系统兼容性**：
   - `tenant_id`：如果 `tenant` 表也使用雪花算法，需要同步修改
   - `tb_device_id`：ThingsBoard 的 `device` 表可能仍使用 UUID，需要确认
   - 如果外部系统使用 UUID，需要保持 `CHAR(36)` 类型

2. **CHECK 约束修改**：
   - `device_relation` 表的 `CHECK (from_device_id <> to_device_id)` 约束需要适配 BIGINT 类型

3. **应用层代码修改**：
   - Java 实体类：`String id` → `Long id`
   - MyBatis Plus：`@TableId(type = IdType.INPUT)` 保持不变，但需要确保生成器返回 Long
   - JSON 序列化：前端可能需要处理 Long 类型（JavaScript 最大安全整数为 2^53-1）

4. **数据迁移**：
   - 如果已有数据，需要编写迁移脚本将 UUID 转换为雪花算法 ID
   - 需要维护 UUID 到雪花ID 的映射表（用于历史数据关联）

### 1.3 需要修改的字段类型

#### 主键 ID（所有表）
- `device_org_unit.id`: `CHAR(36)` → `BIGINT`
- `device_type_config.id`: `CHAR(36)` → `BIGINT`
- `device_model.id`: `CHAR(36)` → `BIGINT`
- `device_info.id`: `CHAR(36)` → `BIGINT`
- `device_network_config.id`: `CHAR(36)` → `BIGINT`
- `device_location.id`: `CHAR(36)` → `BIGINT`
- `device_relation.id`: `CHAR(36)` → `BIGINT`
- `device_state_record.id`: `CHAR(36)` → `BIGINT`
- `device_state_summary.id`: `CHAR(36)` → `BIGINT`
- `device_tool_record.id`: `CHAR(36)` → `BIGINT`
- `device_alarm_history.id`: `CHAR(36)` → `BIGINT`
- `device_production_record.id`: `CHAR(36)` → `BIGINT`
- `device_production_summary.id`: `CHAR(36)` → `BIGINT`
- `device_param_config.id`: `CHAR(36)` → `BIGINT`
- `device_metrics_summary.id`: `CHAR(36)` → `BIGINT`
- `device_shift_config.id`: `CHAR(36)` → `BIGINT`
- `factory_metric_summary.id`: `CHAR(36)` → `BIGINT`

#### 外键字段（需要确认外部表类型）
- `tenant_id`: 如果 `tenant` 表使用雪花算法 → `BIGINT`，否则保持 `CHAR(36)`
- `tb_device_id`: 如果 ThingsBoard `device` 表使用雪花算法 → `BIGINT`，否则保持 `CHAR(36)`
- `parent_id`（自引用）: `CHAR(36)` → `BIGINT`
- `device_id`（引用 device_info）: `CHAR(36)` → `BIGINT`
- `device_model_id`: `CHAR(36)` → `BIGINT`
- `factory_id`, `workshop_id`, `production_line_id`: `CHAR(36)` → `BIGINT`
- `from_device_id`, `to_device_id`: `CHAR(36)` → `BIGINT`
- `factory_id`（factory_metric_summary）: `CHAR(36)` → `BIGINT`

## 二、SQL 修改清单

### 2.1 必须修改的部分

1. **所有主键 ID 字段**：`CHAR(36)` → `BIGINT`
2. **所有自引用外键**：`CHAR(36)` → `BIGINT`
3. **所有引用 device_* 表的外键**：`CHAR(36)` → `BIGINT`
4. **CHECK 约束**：适配 BIGINT 类型

### 2.2 需要确认的部分

1. **tenant_id**：
   - 如果 `tenant` 表主键是 `BIGINT` → 修改为 `BIGINT`
   - 如果 `tenant` 表主键是 `CHAR(36)` → 保持 `CHAR(36)`

2. **tb_device_id**：
   - 如果 ThingsBoard `device` 表主键是 `BIGINT` → 修改为 `BIGINT`
   - 如果 ThingsBoard `device` 表主键是 `CHAR(36)` → 保持 `CHAR(36)`

## 三、应用层代码修改

### 3.1 Java 实体类修改示例

```java
// 修改前
@TableId(type = IdType.INPUT)
private String id;

// 修改后
@TableId(type = IdType.INPUT)
private Long id;
```

### 3.2 雪花算法 ID 生成器配置

确保 ID 生成器返回 `Long` 类型：

```java
@Component
public class SnowflakeIdGenerator {
    private final Snowflake snowflake;
    
    public SnowflakeIdGenerator() {
        // 配置数据中心ID和机器ID
        this.snowflake = new Snowflake(1, 1);
    }
    
    public Long nextId() {
        return snowflake.nextId();
    }
}
```

### 3.3 前端处理

JavaScript 中处理大整数：

```javascript
// 雪花算法ID可能超过 JavaScript 安全整数范围
// 需要作为字符串处理
const deviceId = String(response.data.id); // 转换为字符串
```

## 四、数据迁移方案

如果已有 UUID 数据，需要迁移：

1. **创建映射表**：
```sql
CREATE TABLE id_mapping (
    old_id CHAR(36) PRIMARY KEY,
    new_id BIGINT NOT NULL,
    table_name VARCHAR(100) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

2. **迁移步骤**：
   - 为每条 UUID 记录生成新的雪花算法 ID
   - 更新所有外键引用
   - 验证数据完整性

## 五、验证清单

- [ ] 所有主键字段已改为 `BIGINT`
- [ ] 所有自引用外键已改为 `BIGINT`
- [ ] 所有引用 device_* 表的外键已改为 `BIGINT`
- [ ] `tenant_id` 类型已确认（根据 tenant 表类型）
- [ ] `tb_device_id` 类型已确认（根据 ThingsBoard device 表类型）
- [ ] CHECK 约束已适配 BIGINT
- [ ] 外键约束定义正确
- [ ] 索引定义正确（BIGINT 索引性能更好）
- [ ] 应用层实体类已修改
- [ ] ID 生成器已配置
- [ ] 前端已适配大整数处理

