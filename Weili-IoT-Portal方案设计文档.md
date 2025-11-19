# Weili-IoT-Portal 方案设计文档

## 文档信息

| 项目 | 内容 |
|------|------|
| **文档名称** | Weili-IoT-Portal 方案设计文档 |
| **版本** | v1.0 |
| **编写日期** | 2025-11-21 |
| **文档状态** | 待完善 |

---

## 目录

- [1. 项目概述](#1-项目概述)
- [2. 技术架构](#2-技术架构)
- [3. 模块设计](#3-模块设计)
- [4. 数据库设计](#4-数据库设计)
- [5. 接口设计](#5-接口设计)
- [6. 核心功能实现](#6-核心功能实现)
- [7. 数据同步方案](#7-数据同步方案)
- [8. 安全与权限](#8-安全与权限)
- [9. 性能优化](#9-性能优化)
- [10. 部署运维](#10-部署运维)
- [11. 开发规范](#11-开发规范)
- [12. 附录](#12-附录)

---

## 1. 项目概述

### 1.1 项目背景

**Weili-IoT-Portal** 是一个工业设备数据采集业务系统，负责：

- 接收并处理来自 TB 的设备数据
- 提供业务数据存储和查询服务
- 实现业务逻辑处理和数据分析
- 为前端应用提供 RESTful API 服务

### 1.3 业务范围

- **设备管理**：设备基础信息、设备类型、设备型号、设备配置、设备关系
- **报警管理**：报警统计、报警列表查询、报警实时推送
- **效率管理**：OEE 指标、设备利用率、效率指标排行
- **数字大屏**：厂区布局、设备状态监测、报警排行、工厂级效率指标
- **数据同步**：与 TB 的数据同步

### 1.4 技术选型

| 技术栈 | 选型 | 版本 | 说明 |
|--------|------|------|------|
| **开发语言** | Java | 8+ | 企业级应用开发 |
| **核心框架** | Spring Boot | 2.7+ | 微服务框架 |
| **ORM 框架** | MyBatis Plus | 3.5+ | 数据库操作 |
| **数据库** | MySQL | 8.0+ | 主数据库 |
| **缓存** | Redis | 6.0+ | 缓存、分布式锁 |
| **任务调度** | XXL-Job | 2.3+ | 定时任务 |
| **配置中心** | Apollo | 1.9+ | 配置管理 |
| **消息推送** | WebSocket | - | 实时推送 |
| **API 文档** | OpenAPI 3.0 | 3.0.3 | API 规范 |
| **构建工具** | Maven | 3.6+ | 项目管理 |

### 1.5 业务架构

![image-20251121145224191](C:\Users\WL\AppData\Roaming\Typora\typora-user-images\image-20251121145224191.png)



#### 1.5.1 设备接入

- 前置条件
  - iot-portal中基础数据表里已经录入设备基本信息，包括设备编号（威力公司内部编号）
  - TB中的设备必须携带设备编号
  - TB发送的数据通过设备编号与portal中关联

![iot-Portal设备绑定](C:\Users\WL\Downloads\iot-Portal设备绑定.png)

#### 1.5.2 TB与Portal的数据传递

在暂不引入Kafka/RabbitMQ的阶段，Webhook是TB → Portal唯一通道。需要从“保障送达、保障处理”层面设计机制，确保网络不通或Portal故障时数据仍可追溯。

##### 风险场景

| 场景 | 描述 | 影响 |
|------|------|------|
| 网络不可达 | VPN/专线抖动、Portal维护、DNS异常 | TB无法直连Portal，Webhook请求失败 |
| Portal短暂故障 | 应用重启、流量高峰、线程池耗尽 | TB收到5xx/超时，数据未入库 |
| Portal成功但业务处理失败 | 业务异常、幂等冲突 | 数据落入“黑洞”，TB无法感知 |
| TB节点重启 | 正在重试的消息缓冲丢失 | 产生不可恢复的数据缺口 |

##### TB侧保障手段

1. **本地持久化缓冲（必选）**  
   - Rule Engine在发送Webhook前，将事件写入`webhook_buffer`（PostgreSQL/Timescale/Redis Streams皆可）。  
   - 字段：`tenantId、deviceId、eventType、payload、status、retryCount、nextRetryTime、lastError`.  
   - Webhook成功后标记`status=SUCCESS`并定期归档；失败保留以便重放。
2. **重试与指数退避**  
   - HTTP节点配置`maxAttempts≥5`、`initialDelay=1s`、`multiplier=2`，可覆盖1s→32s的瞬断。  
   - 重试记录写回缓冲表，TB重启后可继续尝试。
3. **Resend Job（异步补偿）**  
   - 新增`WebhookResendJob`：每5分钟扫描`status in (FAILED,PENDING)`且`nextRetryTime <= now()`的记录，分批重发。  
   - Job限流（如500条/批）并基于`messageId`去重，避免恢复后压垮Portal。
4. **死信与告警**  
   - `retryCount`超过阈值（如10次）将记录转入`webhook_dead_letter`表，触发钉钉/短信告警。  
   - 告警信息需包含`tenantId/deviceId/eventType/lastError`，方便现场排查。

##### Portal侧保障手段

1. **快速ACK + 异步处理**  

   - Controller仅做校验+写`portal_webhook_inbox`表/队列，立即返回200，缩短TB等待。  
   - 后台Worker消费`inbox`完成入库、业务逻辑，失败时写`webhook_fail_log`。

2. **幂等控制**  

   - 用`messageId`或`tenantId+deviceId+timestamp`作为幂等Key存入Redis（TTL≥24h）。  
   - 重复消息直接跳过并返回成功，配合TB重试保证“至少一次”变成“恰好一次”。

3. **失败闭环** 

     \- Portal处理失败时将原因写入`webhook_fail_log`并返回500，触发TB重试；若为业务校验失败（例如设备未注册），返回4xx并记录`need_manual=true`，由运营介入

**端到端流程**

```shel
1. TB事件 → 写入webhook_buffer(status=PENDING)
2. TB发送HTTP → Portal -> Inbox → Worker处理
3. Portal成功：返回200 +（可选）回调确认接口
4. TB据此标记SUCCESS并归档
5. 若失败：TB记录错误→重试/Resend；Portal失败→Fail Log→Recovery Job
```

**时序图** 

![数据处理时序图](C:\Users\WL\Downloads\数据处理时序图.png)

**事件数据发送流程**

![关键事件数据发送流程-水平](C:\Users\WL\Downloads\关键事件数据发送流程-水平.png)

**事件数据接收流程**

![关键事件数据接收流程](C:\Users\WL\Downloads\关键事件数据接收流程.png)

**实时数据传输流程**

![实时数据处理流程-水平](C:\Users\WL\Downloads\实时数据处理流程-水平.png)

## 2. 技术架构

### 2.1 整体架构



### 2.2 分层架构

#### 2.2.1 Web 层（表现层）

**职责**：

- 接收 HTTP 请求
- 参数校验
- 调用 Service 层
- 返回响应结果
- 异常处理

**技术实现**：
- Spring MVC Controller
- 统一响应格式（CommonResult）
- 全局异常处理（GlobalExceptionHandler）
- 参数校验（@Valid、@Validated）

#### 2.2.2 Service 层（业务层）

**职责**：
- 业务逻辑处理
- 事务管理
- 调用 DAL 层
- 数据转换（DO → VO）
- 业务规则校验

**技术实现**：
- Spring Service
- @Transactional 事务管理
- 业务异常抛出（BusinessException）
- 数据组装（Assembler）

#### 2.2.3 Domain 层（领域层）

**职责**：
- 领域模型定义
- VO（View Object）视图对象
- BO（Business Object）业务对象
- DTO（Data Transfer Object）数据传输对象
- 请求/响应模型

**技术实现**：
- POJO 类
- Lombok 简化代码
- 字段校验注解

#### 2.2.4 DAL 层（数据访问层）

**职责**：
- 数据库操作
- Entity 实体类定义
- Mapper 接口定义
- MyBatis XML 映射文件

**技术实现**：
- MyBatis Plus
- Entity 实体类
- BaseMapper 基础操作
- 自定义 SQL（XML）

#### 2.2.5 Common 层（公共层）

**职责**：
- 工具类
- 常量定义
- 基础配置
- 公共异常
- 公共响应模型

**技术实现**：
- 工具类包（util）
- 常量类（constant）
- 配置类（config）

### 2.3 模块划分

#### 2.3.1 基础模块

```
iot-portal-common/          # 公共模块
iot-portal-dal/             # 数据访问层（基础）
iot-portal-domain/          # 领域模型层（基础）
iot-portal-service/         # 业务服务层（基础）
iot-portal-web/             # Web 控制层（基础）
iot-portal-starter/         # 启动模块
iot-portal-task/            # 任务调度模块
iot-portal-data-sync/       # 数据同步模块
```

#### 2.3.2 业务模块

```
iot-business/
├── common/                 # 业务公共模块
│   ├── common-api/        # 业务公共 API
│   └── common-domain/      # 业务公共领域模型
├── device-base/            # 设备基础数据模块
│   ├── device-base-api/    # API 接口定义
│   ├── device-base-dal/   # 数据访问层
│   ├── device-base-domain/# 领域模型层
│   ├── device-base-service/# 业务服务层
│   └── device-base-web/   # Web 控制层
├── device-mgmt/            # 设备管理模块
├── alarm-mgmt/             # 报警管理模块
├── efficiency-mgmt/        # 效率管理模块
└── digital-screen/         # 数字大屏模块
```

### 2.4 技术组件

#### 2.4.1 数据访问

- **MyBatis Plus**：CRUD 操作、分页查询、条件构造器
- **数据库连接池**：HikariCP
- **事务管理**：Spring @Transactional

#### 2.4.2 缓存

- **Redis**：数据缓存、分布式锁、Session 存储
- **缓存策略**：Cache-Aside、Write-Through

#### 2.4.3 任务调度

- **XXL-Job**：定时任务、分布式任务调度
- **任务类型**：数据同步、数据补偿、数据对账、报表生成

#### 2.4.4 消息推送

- **WebSocket**：实时数据推送
- **推送场景**：报警实时推送、设备状态变化推送

#### 2.4.5 配置管理

- **Apollo**：配置中心、动态配置
- **配置类型**：数据库配置、业务配置、开关配置

---

## 3. 模块设计

### 3.1 设备基础数据模块（device-base）

#### 3.1.1 模块职责

- 设备基础信息管理（CRUD）
- 设备类型管理（主类型、子类型）
- 设备型号管理
- 设备配置管理（网络配置、位置配置）
- 组织单元管理（厂区、车间、产线）
- 设备关系管理

#### 3.1.2 核心实体

- **DeviceBaseInfo**：设备基础信息
- **DeviceType**：设备类型
- **DeviceModel**：设备型号
- **DeviceConfiguration**：设备配置
- **OrganizationUnit**：组织单元
- **DeviceRelation**：设备关系

#### 3.1.3 接口设计

- `/api/device-base/v1/device-info`：设备基础信息接口
- `/api/device-base/v1/device-types`：设备类型接口
- `/api/device-base/v1/device-models`：设备型号接口
- `/api/device-base/v1/device-configurations`：设备配置接口
- `/api/device-base/v1/organization-units`：组织单元接口
- `/api/device-base/v1/device-relations`：设备关系接口

#### 3.1.4 实现要点

- 多租户数据隔离（tenantId）
- 树形结构处理（设备类型、组织单元）
- 数据冗余设计（便于查询和展示）
- 软删除设计（isActive 字段）

#### 3.1.5 详细设计

##### 3.1.5.1 设备基础信息（DeviceBaseInfo）

**实体说明**：
设备基础信息是系统的核心实体，存储设备的基本属性、关联关系和组织归属信息。

**核心字段**：

| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | VARCHAR(36) | 主键（UUID） | PRIMARY KEY |
| tenant_id | VARCHAR(36) | 租户ID | NOT NULL, INDEX |
| tb_device_id | VARCHAR(36) | TB设备ID | UNIQUE |
| device_code | VARCHAR(50) | 设备编号（威力编号） | NOT NULL, UNIQUE |
| device_name | VARCHAR(100) | 设备名称 | NOT NULL |
| device_type_id | VARCHAR(36) | 设备类型ID | NOT NULL, FK |
| device_model_id | VARCHAR(36) | 设备型号ID | NOT NULL, FK |
| organization_unit_id | VARCHAR(36) | 组织单元ID | NOT NULL, FK |
| device_type_name | VARCHAR(100) | 设备类型名称（冗余） | - |
| device_sub_type_name | VARCHAR(100) | 设备子类型名称 | - |
| model_name | VARCHAR(100) | 型号名称（冗余） | - |
| manufacturer | VARCHAR(100) | 制造商 | - |
| factory_id | VARCHAR(36) | 厂区ID（冗余） | INDEX |
| workshop_id | VARCHAR(36) | 车间ID（冗余） | INDEX |
| production_line_id | VARCHAR(36) | 产线ID（冗余） | INDEX |
| factory_name | VARCHAR(100) | 厂区名称（冗余） | - |
| workshop_name | VARCHAR(100) | 车间名称（冗余） | - |
| production_line_name | VARCHAR(100) | 产线名称（冗余） | - |
| device_status | VARCHAR(20) | 设备状态 | DEFAULT 'ACTIVE' |
| is_monitored | BOOLEAN | 是否监控 | DEFAULT true |
| extra_properties | JSON | 扩展属性 | - |
| remarks | TEXT | 备注 | - |

**业务规则**：

1. **唯一性约束**：
   - `device_code` 在租户内唯一
   - `tb_device_id` 全局唯一（用于关联TB设备）
2. **必填字段**：
   - 设备编号、设备名称、设备类型、设备型号、组织单元为必填
3. **冗余字段设计**：
   - `device_type_name`、`model_name`、`factory_name` 等冗余字段用于：
     - 减少关联查询，提升查询性能
     - 支持历史数据查询（即使关联数据被删除，仍可查看历史信息）
4. **数据同步**：
   - 设备基础信息可通过TB Webhook同步创建
   - 支持手动创建和批量导入

**接口设计**：

**创建设备**：
```
POST /api/device-base/v1/device-info
Request Body: DeviceBaseInfoCreateReq
Response: CommonResult<DeviceBaseInfoVO>
```

**查询设备详情**：
```
GET /api/device-base/v1/device-info/{id}
Response: CommonResult<DeviceBaseInfoVO>
```

**更新设备**：
```
PUT /api/device-base/v1/device-info/{id}
Request Body: DeviceBaseInfoUpdateReq
Response: CommonResult<DeviceBaseInfoVO>
```

**删除设备**：
```
DELETE /api/device-base/v1/device-info/{id}
Response: CommonResult<Boolean>
```

**分页查询设备**：
```
POST /api/device-base/v1/device-info/page
Request Body: DeviceBaseInfoQueryReq
Response: CommonResult<PageResult<DeviceBaseInfoVO>>
```

**查询设备列表**（简化字段）：
```
POST /api/device-base/v1/device-info/list
Request Body: DeviceBaseInfoQueryReq
Response: CommonResult<PageResult<DeviceBaseInfoListVO>>
```

**查询条件支持**：
- 设备编号模糊查询（deviceCodeLike）
- 设备名称模糊查询（deviceNameLike）
- 设备类型筛选（deviceTypeIds）
- 设备型号筛选（deviceModelIds）
- 厂区/车间/产线筛选（factoryIds/workshopIds/productionLineIds）
- 设备状态筛选（deviceStatuses）
- 是否监控筛选（isMonitored）
- 是否有报警筛选（hasAlarm）

##### 3.1.5.2 设备类型（DeviceType）

**实体说明**：
设备类型采用树形结构，支持主类型和子类型的层级关系，用于对设备进行分类管理。

**核心字段**：

| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | VARCHAR(36) | 主键（UUID） | PRIMARY KEY |
| tenant_id | VARCHAR(36) | 租户ID | NOT NULL, INDEX |
| type_code | VARCHAR(50) | 类型编码 | NOT NULL, UNIQUE |
| type_name | VARCHAR(100) | 类型名称 | NOT NULL |
| parent_type_id | VARCHAR(36) | 父类型ID | FK |
| parent_type_name | VARCHAR(100) | 父类型名称（冗余） | - |
| level | INT | 层级 | NOT NULL, DEFAULT 1 |
| category | VARCHAR(50) | 分类（CNC/PLC等） | - |
| description | TEXT | 类型描述 | - |
| icon | VARCHAR(200) | 图标URL或图标代码 | - |
| custom_fields | JSON | 自定义字段 | - |
| is_active | BOOLEAN | 是否启用 | DEFAULT true |
| sort_order | INT | 排序顺序 | DEFAULT 0 |

**业务规则**：

1. **树形结构**：
   - `level = 1`：主类型（如：CNC、PLC、机器人）
   - `level = 2`：子类型（如：五轴铣车中心、立式加工中心）
   - 子类型必须关联父类型（parent_type_id）

2. **唯一性约束**：
   - `type_code` 在租户内唯一

3. **层级限制**：
   - 最多支持2层（主类型、子类型）
   - 主类型的 `parent_type_id` 为 NULL

4. **软删除**：
   - 删除类型时检查是否有设备使用该类型
   - 有设备使用时不允许删除，只能禁用（is_active = false）

**接口设计**：

- `POST /api/device-base/v1/device-types`：创建设备类型
- `GET /api/device-base/v1/device-types/{id}`：查询设备类型详情
- `PUT /api/device-base/v1/device-types/{id}`：更新设备类型
- `DELETE /api/device-base/v1/device-types/{id}`：删除设备类型
- `POST /api/device-base/v1/device-types/page`：分页查询设备类型

**查询条件支持**：
- 类型编码模糊查询（typeCodeLike）
- 类型名称模糊查询（typeNameLike）
- 父类型筛选（parentTypeId）
- 层级筛选（level：1-主类型，2-子类型）
- 分类筛选（categories）
- 是否启用筛选（isActive）

##### 3.1.5.3 设备型号（DeviceModel）

**实体说明**：
设备型号用于定义设备的具体型号规格，关联设备类型，存储型号的详细参数信息。

**核心字段**：

| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | VARCHAR(36) | 主键（UUID） | PRIMARY KEY |
| tenant_id | VARCHAR(36) | 租户ID | NOT NULL, INDEX |
| model_code | VARCHAR(50) | 型号编码 | NOT NULL, UNIQUE |
| model_name | VARCHAR(100) | 型号名称 | NOT NULL |
| device_type_id | VARCHAR(36) | 设备类型ID | NOT NULL, FK |
| device_type_name | VARCHAR(100) | 设备类型名称（冗余） | - |
| manufacturer | VARCHAR(100) | 制造商 | - |
| specifications | JSON | 规格参数 | - |
| type_specific_attrs | JSON | 类型特定属性 | - |
| is_active | BOOLEAN | 是否启用 | DEFAULT true |

**业务规则**：

1. **关联关系**：
   - 设备型号必须关联设备类型（device_type_id）
   - 一个设备类型可以有多个设备型号

2. **唯一性约束**：
   - `model_code` 在租户内唯一

3. **规格参数**：
   - `specifications` 为JSON格式，存储通用规格参数
   - 例如：`{"maxSpeed": 10000, "power": "15kW", "weight": "5000kg"}`

4. **类型特定属性**：
   - `type_specific_attrs` 为JSON格式，存储该设备类型特有的属性
   - 例如：CNC类型可能有"主轴数量"、"刀库容量"等属性

**接口设计**：

- `POST /api/device-base/v1/device-models`：创建设备型号
- `GET /api/device-base/v1/device-models/{id}`：查询设备型号详情
- `PUT /api/device-base/v1/device-models/{id}`：更新设备型号
- `DELETE /api/device-base/v1/device-models/{id}`：删除设备型号
- `POST /api/device-base/v1/device-models/page`：分页查询设备型号

**查询条件支持**：
- 型号编码模糊查询（modelCodeLike）
- 型号名称模糊查询（modelNameLike）
- 设备类型筛选（deviceTypeIds）
- 制造商筛选（manufacturer）
- 是否启用筛选（isActive）

##### 3.1.5.4 设备配置（DeviceConfiguration）

**实体说明**：
设备配置存储设备的网络配置、位置配置等信息，用于设备连接和定位。

**核心字段**：

| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | VARCHAR(36) | 主键（UUID） | PRIMARY KEY |
| device_id | VARCHAR(36) | 设备ID | NOT NULL, UNIQUE, FK |
| device_code | VARCHAR(50) | 设备编号（冗余） | - |
| device_name | VARCHAR(100) | 设备名称（冗余） | - |
| ip_address | VARCHAR(50) | IP地址 | - |
| port | INT | 端口号 | - |
| mac_address | VARCHAR(50) | MAC地址 | - |
| gateway | VARCHAR(50) | 网关地址 | - |
| subnet_mask | VARCHAR(50) | 子网掩码 | - |
| protocol | VARCHAR(50) | 通信协议 | - |
| connection_params | JSON | 连接参数 | - |
| location_code | VARCHAR(50) | 位置编码 | - |
| location_description | VARCHAR(200) | 位置描述 | - |
| coordinates | JSON | 坐标信息 | - |

**业务规则**：

1. **一对一关系**：
   - 一个设备只能有一个配置（device_id 唯一）
   - 设备配置必须关联设备

2. **网络配置**：
   - IP地址、端口、MAC地址等用于设备网络连接
   - 协议类型：MQTT、HTTP、Modbus、OPC-UA等

3. **连接参数**：
   - `connection_params` 为JSON格式，存储协议特定的连接参数
   - 例如：`{"username": "admin", "password": "***", "topic": "device/data"}`

4. **位置配置**：
   - `coordinates` 为JSON格式，存储设备在布局图中的坐标
   - 例如：`{"x": 100, "y": 200, "z": 0}`

**接口设计**：

- `POST /api/device-base/v1/device-configurations`：创建设备配置
- `GET /api/device-base/v1/device-configurations/{id}`：查询设备配置详情
- `PUT /api/device-base/v1/device-configurations/{id}`：更新设备配置
- `DELETE /api/device-base/v1/device-configurations/{id}`：删除设备配置
- `POST /api/device-base/v1/device-configurations/page`：分页查询设备配置

**查询条件支持**：
- IP地址查询（ipAddress）
- 协议筛选（protocol）
- 位置编码查询（locationCode）

##### 3.1.5.5 组织单元（OrganizationUnit）

**实体说明**：
组织单元采用树形结构，支持厂区、车间、产线的三级组织架构，用于设备归属管理。

**核心字段**：

| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | VARCHAR(36) | 主键（UUID） | PRIMARY KEY |
| tenant_id | VARCHAR(36) | 租户ID | NOT NULL, INDEX |
| unit_code | VARCHAR(50) | 组织单元编码 | NOT NULL, UNIQUE |
| unit_name | VARCHAR(100) | 组织单元名称 | NOT NULL |
| unit_type | VARCHAR(20) | 单元类型 | NOT NULL |
| parent_id | VARCHAR(36) | 父组织单元ID | FK |
| parent_name | VARCHAR(100) | 父组织单元名称（冗余） | - |
| level | INT | 层级 | NOT NULL |
| path | VARCHAR(500) | 路径 | - |
| description | TEXT | 描述 | - |
| location | VARCHAR(200) | 位置 | - |
| is_active | BOOLEAN | 是否启用 | DEFAULT true |
| sort_order | INT | 排序顺序 | DEFAULT 0 |

**业务规则**：

1. **树形结构**：
   - `level = 1`：厂区（FACTORY）
   - `level = 2`：车间（WORKSHOP）
   - `level = 3`：产线（LINE）
   - 最多支持3层

2. **单元类型**：
   - FACTORY：厂区
   - WORKSHOP：车间
   - LINE：产线

3. **路径字段**：
   - `path` 存储从根到当前节点的完整路径
   - 例如：`/厂区1/车间1/产线1`
   - 用于快速查询和组织结构展示

4. **唯一性约束**：
   - `unit_code` 在租户内唯一

5. **软删除**：
   - 删除组织单元时检查是否有子单元或设备
   - 有子单元或设备时不允许删除，只能禁用

**接口设计**：

- `POST /api/device-base/v1/organization-units`：创建组织单元
- `GET /api/device-base/v1/organization-units/{id}`：查询组织单元详情
- `PUT /api/device-base/v1/organization-units/{id}`：更新组织单元
- `DELETE /api/device-base/v1/organization-units/{id}`：删除组织单元
- `POST /api/device-base/v1/organization-units/page`：分页查询组织单元

**查询条件支持**：
- 组织单元编码模糊查询（unitCodeLike）
- 组织单元名称模糊查询（unitNameLike）
- 单元类型筛选（unitTypes）
- 父组织单元筛选（parentId）
- 层级筛选（level）
- 是否启用筛选（isActive）

##### 3.1.5.6 设备关系（DeviceRelation）

**实体说明**：
设备关系用于定义设备之间的关联关系，如父子关系、依赖关系等。

**核心字段**：

| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | VARCHAR(36) | 主键（UUID） | PRIMARY KEY |
| tenant_id | VARCHAR(36) | 租户ID | NOT NULL, INDEX |
| from_device_id | VARCHAR(36) | 源设备ID | NOT NULL, FK |
| from_device_code | VARCHAR(50) | 源设备编号（冗余） | - |
| from_device_name | VARCHAR(100) | 源设备名称（冗余） | - |
| to_device_id | VARCHAR(36) | 目标设备ID | NOT NULL, FK |
| to_device_code | VARCHAR(50) | 目标设备编号（冗余） | - |
| to_device_name | VARCHAR(100) | 目标设备名称（冗余） | - |
| relation_type | VARCHAR(50) | 关系类型 | NOT NULL |
| relation_name | VARCHAR(100) | 关系名称 | - |
| description | TEXT | 关系描述 | - |
| properties | JSON | 关系属性 | - |
| is_active | BOOLEAN | 是否启用 | DEFAULT true |

**业务规则**：

1. **关系类型**：
   - PARENT_CHILD：父子关系（如：生产线与设备）
   - PEER：同级关系（如：并行设备）
   - DEPENDENCY：依赖关系（如：设备A依赖设备B）
   - ASSOCIATION：关联关系（如：设备A与设备B关联）

2. **唯一性约束**：
   - 同一对设备不能有重复的关系类型
   - 即：`(from_device_id, to_device_id, relation_type)` 唯一

3. **自反性**：
   - 关系可以是单向的（from → to）
   - 如果需要双向关系，需要创建两条记录

4. **关系属性**：
   - `properties` 为JSON格式，存储关系的扩展属性
   - 例如：`{"priority": "high", "weight": 1.0}`

**接口设计**：

- `POST /api/device-base/v1/device-relations`：创建设备关系
- `GET /api/device-base/v1/device-relations/{id}`：查询设备关系详情
- `PUT /api/device-base/v1/device-relations/{id}`：更新设备关系
- `DELETE /api/device-base/v1/device-relations/{id}`：删除设备关系
- `POST /api/device-base/v1/device-relations/page`：分页查询设备关系

**查询条件支持**：
- 源设备筛选（fromDeviceId）
- 目标设备筛选（toDeviceId）
- 关系类型筛选（relationTypes）
- 是否启用筛选（isActive）

#### 3.1.6 公共接口（DeviceBaseDataApi）

**接口说明**：
`DeviceBaseDataApi` 是设备基础数据的公共接口，供其他业务模块（device-mgmt、alarm-mgmt等）调用，实现模块间解耦。

**接口方法**：

1. **getDeviceById**：根据设备ID查询设备信息
   - 参数：tenantId、factoryId、deviceId
   - 返回：DeviceBaseInfoVO
   - 说明：包含工厂数据隔离验证

2. **getDeviceByCode**：根据设备编号查询设备信息
   - 参数：tenantId、factoryId、deviceCode
   - 返回：DeviceBaseInfoVO
   - 说明：包含工厂数据隔离验证

3. **getDevicesByIds**：批量查询设备信息
   - 参数：tenantId、factoryId、deviceIds
   - 返回：List<DeviceBaseInfoVO>
   - 说明：支持批量查询，提升性能

4. **getDevicesByFactory**：根据工厂/车间查询设备列表
   - 参数：tenantId、factoryId、workshopId（可选）
   - 返回：List<DeviceBaseInfoVO>
   - 说明：用于按组织单元查询设备

5. **getDeviceByTbDeviceId**：根据TB设备ID查询设备信息
   - 参数：tenantId、tbDeviceId
   - 返回：DeviceBaseInfoVO
   - 说明：用于数据同步场景

**使用场景**：

- **设备管理模块**：查询设备基础信息用于业务处理
- **报警管理模块**：根据设备ID查询设备信息用于报警展示
- **效率管理模块**：根据设备ID查询设备信息用于指标计算
- **数据同步模块**：根据TB设备ID查询设备信息用于数据关联

**实现位置**：
- 接口定义：`iot-business/common/common-api`
- 接口实现：`iot-business/device-base/device-base-service`

#### 3.1.7 模块依赖关系

**依赖结构**：

```
device-base 模块
├── 依赖 common 模块
│   ├── common-api（提供 DeviceBaseDataApi 接口定义）
│   └── common-domain（提供公共 VO）
├── 被其他业务模块依赖
│   ├── device-mgmt（通过 DeviceBaseDataApi 调用）
│   ├── alarm-mgmt（通过 DeviceBaseDataApi 调用）
│   ├── efficiency-mgmt（通过 DeviceBaseDataApi 调用）
│   └── digital-screen（通过 DeviceBaseDataApi 调用）
└── 不依赖其他业务模块
```

**依赖原则**：

1. **单向依赖**：
   - device-base 模块不依赖其他业务模块
   - 其他业务模块通过公共接口（DeviceBaseDataApi）调用 device-base 功能

2. **接口隔离**：
   - 其他模块只能通过 DeviceBaseDataApi 接口访问设备基础数据
   - 不能直接访问 device-base 的内部实现类

3. **数据隔离**：
   - 所有查询必须包含 tenantId 和 factoryId
   - 确保数据访问的安全性

#### 3.1.8 数据同步机制

**同步场景**：

1. **TB Webhook 同步**：
   - TB 设备创建/更新事件 → Portal 设备基础信息同步
   - 同步字段：设备编号、设备名称、TB设备ID等

2. **手动创建**：
   - 用户通过 Portal 界面手动创建设备
   - 支持批量导入

3. **数据补偿**：
   - 定时任务拉取 TB 设备数据
   - 对比 Portal 数据，补充缺失设备

**同步流程**：

1. **接收 Webhook**：
   - WebhookController 接收 TB 事件
   - 快速 ACK（200 响应）
   - 写入收件箱表

2. **异步处理**：
   - 从收件箱读取事件
   - 解析事件类型（DEVICE_CREATED/DEVICE_UPDATED）
   - 调用 DeviceBaseInfoService 创建/更新设备

3. **数据转换**：
   - TB 设备数据 → Portal 设备基础信息
   - 填充关联字段（设备类型、组织单元等）

#### 3.1.9 缓存策略

**缓存场景**：

1. **设备基础信息缓存**：
   - Key：`device:base:info:{tenantId}:{deviceId}`
   - TTL：30 分钟
   - 更新策略：Cache-Aside（先查缓存，未命中查数据库，写入缓存）

2. **设备类型树缓存**：
   - Key：`device:type:tree:{tenantId}`
   - TTL：1 小时
   - 更新策略：设备类型变更时清除缓存

3. **组织单元树缓存**：
   - Key：`org:unit:tree:{tenantId}`
   - TTL：1 小时
   - 更新策略：组织单元变更时清除缓存

4. **设备编号映射缓存**：
   - Key：`device:code:map:{tenantId}:{deviceCode}`
   - TTL：30 分钟
   - 用途：快速根据设备编号查询设备ID

**缓存更新策略**：

- **写操作**：先更新数据库，再删除相关缓存
- **读操作**：先查缓存，未命中查数据库并写入缓存
- **批量操作**：批量更新时，批量清除相关缓存

#### 3.1.10 性能优化建议

**数据库优化**：

1. **索引设计**：
   - 主键索引：id
   - 唯一索引：device_code（租户内唯一）、tb_device_id（全局唯一）
   - 普通索引：tenant_id、device_type_id、factory_id、workshop_id
   - 联合索引：`(tenant_id, factory_id, device_code)`、`(tenant_id, device_type_id)`

2. **查询优化**：
   - 避免全表扫描，所有查询必须带 tenant_id
   - 分页查询限制最大 pageSize = 200
   - 复杂查询使用覆盖索引

3. **数据冗余**：
   - 冗余字段减少关联查询（device_type_name、factory_name 等）
   - 定期校验冗余字段一致性

**接口优化**：

1. **批量查询**：
   - 提供批量查询接口（getDevicesByIds）
   - 减少数据库交互次数

2. **分页优化**：
   - 使用游标分页（适用于大数据量场景）
   - 限制单次查询数据量

3. **字段筛选**：
   - 提供简化字段接口（list 接口）
   - 减少数据传输量

#### 3.1.11 数据一致性保障

**冗余字段一致性**：

1. **更新策略**：
   - 更新关联数据时，同步更新冗余字段
   - 例如：更新设备类型名称时，同步更新所有关联设备的 device_type_name

2. **定时校验**：
   - 定时任务校验冗余字段一致性
   - 发现不一致时自动修复

3. **事务保障**：
   - 关联数据更新使用事务
   - 确保数据一致性

**软删除一致性**：

1. **级联检查**：
   - 删除设备类型时，检查是否有设备使用
   - 删除组织单元时，检查是否有子单元或设备

2. **级联禁用**：
   - 删除父节点时，级联禁用子节点
   - 保持数据完整性

### 3.2 设备管理模块（device-mgmt）

#### 3.2.1 模块职责

- 设备列表查询（含状态、报警信息）
- 设备详情查询
- 设备指标查询（OEE、时间开动率等）
- 实时曲线查询（主轴转速、主轴负载、进给率）
- 报警历史查询
- 状态统计查询
- 状态时间线查询（甘特图）
- 产量统计查询
- 刀具信息查询
- 程序信息查询
- 轴坐标查询

#### 3.2.2 核心实体

- **DeviceMetric**：设备指标
- **DeviceState**：设备状态
- **DeviceAlarm**：设备报警
- **DeviceProduction**：设备产量
- **DeviceTool**：设备刀具
- **DeviceProgram**：设备程序
- **DeviceAxis**：设备轴坐标

#### 3.2.3 接口设计

- `/api/device-mgmt/v1/devices/list`：设备列表
- `/api/device-mgmt/v1/devices/{id}`：设备详情
- `/api/device-mgmt/v1/devices/{id}/metrics`：设备指标
- `/api/device-mgmt/v1/devices/{id}/realtime-curve`：实时曲线
- `/api/device-mgmt/v1/devices/{id}/alarm-history`：报警历史
- `/api/device-mgmt/v1/devices/{id}/state-stats`：状态统计
- `/api/device-mgmt/v1/devices/{id}/state-gantt`：状态甘特图
- `/api/device-mgmt/v1/devices/{id}/production-history`：产量历史
- `/api/device-mgmt/v1/devices/{id}/tool-info`：刀具信息
- `/api/device-mgmt/v1/devices/{id}/program-info`：程序信息
- `/api/device-mgmt/v1/devices/{id}/axis-coordinates`：轴坐标

#### 3.2.4 实现要点

- 数据聚合计算（指标计算、状态统计）
- 时间范围查询优化（索引设计）
- 实时数据查询（缓存策略）
- 分页查询优化（大数据量处理）

#### 3.2.5 详细设计

##### 3.2.5.1 设备列表查询

**功能说明**：
设备列表查询是设备管理模块的核心功能，支持多条件筛选、分页查询，返回设备的基础信息、当前状态、报警信息等。

**查询条件**：
- 设备编号/名称模糊查询
- 设备类型/型号筛选
- 厂区/车间/产线筛选
- 设备状态筛选（加工中/待机/故障/关机）
- 是否监控筛选
- 是否有报警筛选

**返回字段**：
- 设备基础信息（编号、名称、类型、型号）
- 组织信息（厂区、车间、产线）
- 当前状态（加工中/待机/故障/关机）
- 报警信息（是否有报警）

**实现要点**：
- 通过 `DeviceBaseDataApi` 查询设备基础信息
- 关联查询设备状态表获取当前状态
- 关联查询报警记录表判断是否有报警
- 使用 LEFT JOIN 避免数据丢失
- 支持排序（按设备编号、创建时间等）

##### 3.2.5.2 设备指标查询

**功能说明**：
查询设备的效率指标，包括 OEE、时间开动率、性能开动率、设备开动率、停机率等。

**指标类型**：
- **OEE**：综合设备效率（Overall Equipment Effectiveness）
- **时间开动率**：实际开动时间 / 计划开动时间
- **性能开动率**：实际产量 / 理论产量
- **设备开动率**：合格品数量 / 实际产量
- **停机率**：停机时间 / 总时间

**查询维度**：
- 按设备查询：查询单个设备的指标
- 按班次查询：查询指定班次的指标
- 按时间范围查询：查询历史指标趋势

**数据来源**：
- 设备指标表（device_metrics）：存储已计算的指标数据
- 实时计算：当前班次的指标实时计算

**实现要点**：
- 历史指标从指标表查询（已计算完成）
- 当前班次指标实时计算（基于设备状态数据）
- 指标计算按班次进行，班次结束后最终确定
- 支持指标趋势查询（按时间序列）

##### 3.2.5.3 实时曲线查询

**功能说明**：
查询设备的实时运行曲线数据，用于展示设备运行状态，如主轴转速、主轴负载、进给率等。

**曲线类型**：
- **主轴转速**：主轴转速曲线（rpm）
- **主轴负载**：主轴负载曲线（%）
- **进给率**：进给率曲线（mm/min）
- **其他指标**：根据设备类型支持其他指标

**查询参数**：
- 设备ID（必填）
- 指标名称（必填）
- 时间范围（可选，默认最近1小时）
- 采样间隔（可选，默认1分钟）

**数据来源**：
- TB 遥测数据（通过 Webhook 同步）
- 实时查询 TB API（可选）

**返回格式**：
- 时间序列数据点（时间戳 + 指标值）
- 支持前端图表展示（折线图、曲线图）

**实现要点**：
- 数据点按时间排序
- 支持数据采样（降低数据量）
- 实时数据缓存（TTL = 10秒）
- 历史数据从数据库查询

##### 3.2.5.4 报警历史查询

**功能说明**：
查询设备的历史报警记录，支持时间范围筛选、报警级别筛选等。

**查询条件**：
- 设备ID（必填）
- 时间范围（可选）
- 报警级别（可选：INFO/WARNING/ERROR/CRITICAL）
- 是否报警中（可选）

**返回字段**：
- 报警号、报警内容
- 报警级别
- 开始时间、结束时间
- 持续时间
- 班次信息

**实现要点**：
- 从报警记录表查询
- 支持分页查询
- 进行中的报警：结束时间为 NULL，持续时间实时计算
- 已结束的报警：显示完整的开始和结束时间

##### 3.2.5.5 状态统计查询

**功能说明**：
统计设备在指定时间范围内的状态分布和时长统计。

**统计维度**：
- **状态分布**：各状态的设备数量
- **状态时长**：各状态的累计时长
- **状态占比**：各状态时长占总时长的比例

**状态类型**：
- 加工中（RUNNING）
- 待机（STANDBY）
- 故障（FAULT）
- 关机（SHUTDOWN）
- 未知（UNKNOWN）

**查询参数**：
- 设备ID（必填）
- 时间范围（必填）
- 统计粒度（可选：按小时/按天）

**实现要点**：
- 从设备状态表查询状态记录
- 按状态分组统计
- 计算各状态的累计时长
- 计算各状态的占比

##### 3.2.5.6 状态时间线查询（甘特图）

**功能说明**：
查询设备的状态时间线，用于甘特图展示，显示设备在不同时间段的状态。

**返回数据**：
- 状态记录列表
- 每条记录包含：状态代码、状态名称、开始时间、结束时间、持续时间

**查询参数**：
- 设备ID（必填）
- 时间范围（必填）
- 状态筛选（可选）

**实现要点**：
- 从设备状态表查询
- 按开始时间排序
- 支持状态筛选
- 返回格式便于前端甘特图组件使用

##### 3.2.5.7 产量统计查询

**功能说明**：
查询设备在指定时间范围内的产量统计信息。

**统计维度**：
- 总产量
- 合格品数量
- 不合格品数量
- 合格率

**查询参数**：
- 设备ID（必填）
- 时间范围（必填）
- 统计粒度（可选：按小时/按天/按班次）

**数据来源**：
- 设备产量表（device_productions）
- TB 遥测数据（通过 Webhook 同步）

**实现要点**：
- 按时间范围聚合统计
- 支持多维度统计（按班次、按天等）
- 计算合格率等衍生指标

##### 3.2.5.8 刀具信息查询

**功能说明**：
查询设备的刀具相关信息，包括当前刀具信息、刀具使用历史、刀具补偿信息等。

**查询类型**：
- **当前刀具信息**：当前正在使用的刀具信息
- **刀具使用历史**：刀具的使用记录（开始时间、结束时间、持续时长）
- **刀具补偿信息**：刀具的长度补偿、半径补偿、磨损补偿

**数据来源**：
- TB 设备属性（通过 Webhook 同步）
- 实时查询 TB API（可选）

**返回字段**：
- 刀具号、刀套号
- 长度补偿、半径补偿
- 长度磨损、半径磨损
- 使用时间戳

**实现要点**：
- 实时数据从 TB 查询或缓存
- 历史数据从数据库查询
- 支持刀具使用记录的查询和统计

##### 3.2.5.9 程序信息查询

**功能说明**：
查询设备的程序信息，包括当前执行的程序、程序代码等。

**查询类型**：
- **程序信息**：当前执行的程序名称、路径、当前代码行
- **程序代码**：程序的完整代码（G代码、M代码）

**数据来源**：
- TB 设备属性（通过 Webhook 同步）
- 实时查询 TB API（可选）

**返回字段**：
- 程序名、程序路径
- 当前执行代码行
- 程序代码列表（G代码、M代码）

**实现要点**：
- 实时数据从 TB 查询或缓存
- 程序代码可能较大，需要分页或分段返回
- 支持代码类型筛选（G代码、M代码）

##### 3.2.5.10 轴坐标查询

**功能说明**：
查询设备的轴坐标信息，包括各轴的绝对坐标、相对坐标、机械坐标、剩余坐标等。

**轴类型**：
- X轴、Y轴、Z轴
- 其他轴（根据设备类型）

**坐标类型**：
- **绝对坐标**：相对于工件原点的坐标
- **相对坐标**：相对于程序原点的坐标
- **机械坐标**：相对于机床原点的坐标
- **剩余坐标**：剩余行程坐标

**数据来源**：
- TB 设备属性（通过 Webhook 同步）
- 实时查询 TB API（可选）

**返回格式**：
- 轴坐标列表
- 每条记录包含：轴名称、各类型坐标值

**实现要点**：
- 实时数据从 TB 查询或缓存
- 支持多轴设备（3轴、4轴、5轴等）
- 坐标值精度要求高（保留小数点后3位）

#### 3.2.6 模块依赖关系

**依赖结构**：

```
device-mgmt 模块
├── 依赖 device-base 模块
│   └── 通过 DeviceBaseDataApi 查询设备基础信息
├── 依赖 common 模块
│   ├── common-api（公共接口）
│   └── common-domain（公共领域模型）
└── 不依赖其他业务模块
```

**依赖原则**：

1. **通过接口依赖**：
   - 通过 `DeviceBaseDataApi` 接口查询设备基础信息
   - 不直接访问 device-base 模块的内部实现

2. **数据隔离**：
   - 所有查询必须包含 tenantId 和 factoryId
   - 通过接口自动验证数据隔离

#### 3.2.7 数据来源

**数据来源分类**：

1. **设备基础数据**：
   - 来源：device-base 模块
   - 获取方式：通过 `DeviceBaseDataApi` 接口

2. **设备状态数据**：
   - 来源：TB Webhook 同步
   - 存储：设备状态表（device_states）

3. **设备指标数据**：
   - 来源：基于设备状态数据计算
   - 存储：设备指标表（device_metrics）

4. **报警数据**：
   - 来源：TB Webhook 同步
   - 存储：报警记录表（alarm_records）

5. **实时数据**（刀具、程序、轴坐标）：
   - 来源：TB 设备属性（实时查询或缓存）
   - 存储：实时数据缓存（Redis）

### 3.3 报警管理模块（alarm-mgmt）

#### 3.3.1 模块职责

- 当前报警设备数量统计
- 当前报警设备列表查询
- 报警列表查询（历史报警）
- 报警实时推送（WebSocket）

#### 3.3.2 核心实体

- **AlarmRecord**：报警记录
- **AlarmStatistics**：报警统计

#### 3.3.3 接口设计

- `/api/v1/alarm/statistics/current-count`：当前报警设备数量
- `/api/v1/alarm/statistics/current-devices`：当前报警设备列表
- `/api/v1/alarm/list/current`：当前报警列表
- `/api/v1/alarm/list/query`：报警历史查询
- `/ws/alarm`：报警实时推送（WebSocket）

#### 3.3.4 实现要点

- 报警状态管理（isActive）
- 报警去重（设备级别）
- 实时推送机制（WebSocket 连接管理）
- 报警级别分类（INFO、WARNING、ERROR、CRITICAL）

#### 3.3.5 详细设计

##### 3.3.5.1 当前报警设备数量统计

**功能说明**：
统计指定工厂（或车间）内所有正在报警的设备个数。

**统计规则**：
- 一台设备可能有多个报警，但只统计设备个数（去重）
- 只统计 `is_active = true` 的报警记录
- 实时性：10秒刷新

**查询参数**：
- tenantId（必填）
- factoryId（必填）
- workshopId（可选，不填则查询该工厂下所有车间的报警设备）

**返回数据**：
- 工厂ID、工厂名称
- 车间ID、车间名称（如果按车间查询）
- 当前报警设备数量（去重后的设备个数）

**实现要点**：
- 从报警记录表查询 `is_active = true` 的记录
- 按设备ID去重统计
- 支持工厂/车间筛选
- 结果缓存（TTL = 10秒）

##### 3.3.5.2 当前报警设备列表查询

**功能说明**：
查询正在报警的设备列表，每个设备显示其报警数量。

**返回数据**：
- 设备ID、设备编号、设备名称
- 设备类型、设备子类型
- 车间名称
- 当前报警数量（该设备正在报警的个数）
- 最新报警开始时间

**查询条件**：
- tenantId（必填）
- factoryId（必填）
- workshopId（可选）
- 分页参数（pageNo、pageSize）

**实现要点**：
- 从报警记录表查询 `is_active = true` 的记录
- 按设备ID分组统计报警数量
- 关联设备基础信息表获取设备信息
- 支持分页查询
- 支持排序（按报警数量、最新报警时间）

##### 3.3.5.3 报警列表查询

**功能说明**：
查询报警详情列表，支持多条件筛选和分页查询。

**查询类型**：
- **当前报警列表**：查询所有正在报警的记录（`is_active = true`）
- **历史报警列表**：查询历史报警记录（支持时间范围筛选）

**查询条件**：
- tenantId（必填）
- factoryId（必填）
- workshopId（可选）
- deviceCodes（可选，支持多个，逗号分隔）
- startTime/endTime（可选，时间范围）
- isActive（可选，true-仅显示未结束，false-仅显示已结束，null-显示所有）
- alarmLevels（可选，支持多个，逗号分隔：INFO/WARNING/ERROR/CRITICAL）
- 分页参数（pageNo、pageSize）

**返回字段**：
- 报警ID、设备ID、设备编号、设备名称
- 设备类型、设备子类型
- 报警号、报警内容
- 报警级别
- 开始时间、结束时间（进行中的报警为null）
- 持续时间（进行中的报警实时计算）
- 是否报警中（isActive）
- 班次信息（开始班次、结束班次）

**实现要点**：
- 从报警记录表查询
- 支持多条件组合筛选
- 进行中的报警：结束时间为 NULL，持续时间实时计算
- 已结束的报警：显示完整的开始和结束时间
- 支持分页和排序

##### 3.3.5.4 报警实时推送（WebSocket）

**功能说明**：
通过 WebSocket 实时推送报警事件，前端接收后实时更新报警列表。

**推送场景**：
- 报警创建：新报警产生时推送
- 报警结束：报警结束时推送
- 报警更新：报警信息更新时推送

**推送内容**：
- 事件类型（ALARM_CREATED/ALARM_ENDED/ALARM_UPDATED）
- 报警信息（报警ID、设备ID、报警内容等）

**连接管理**：
- 连接建立：用户连接 WebSocket
- 连接保持：心跳机制保持连接
- 连接断开：清理连接资源

**订阅管理**：
- 按工厂订阅：订阅指定工厂的报警
- 按车间订阅：订阅指定车间的报警
- 按设备订阅：订阅指定设备的报警

**实现要点**：
- 使用 Spring WebSocket 实现
- 连接信息存储在 Redis 或内存中
- 报警事件触发时，查询订阅用户并推送
- 支持单播、组播推送

#### 3.3.6 模块依赖关系

**依赖结构**：

```
alarm-mgmt 模块
├── 依赖 device-base 模块
│   └── 通过 DeviceBaseDataApi 查询设备基础信息
├── 依赖 common 模块
│   ├── common-api（公共接口）
│   └── common-domain（公共领域模型）
└── 不依赖其他业务模块
```

**依赖原则**：
- 通过 `DeviceBaseDataApi` 接口查询设备基础信息
- 不直接访问 device-base 模块的内部实现

#### 3.3.7 数据来源

**数据来源**：
- **报警数据**：TB Webhook 同步（ALARM_CREATED/ALARM_UPDATED 事件）
- **设备基础信息**：通过 `DeviceBaseDataApi` 接口查询
- **实时推送**：报警事件触发时实时推送

### 3.4 效率管理模块（efficiency-mgmt）

#### 3.4.1 模块职责

- 效率指标查询（OEE、时间开动率、性能开动率、设备开动率、停机率）
- 效率指标排行（Top N）
- 班次指标查询

#### 3.4.2 核心实体

- **DeviceEfficiencyMetric**：设备效率指标
- **ShiftMetric**：班次指标

#### 3.4.3 接口设计

- `/api/v1/efficiency/metrics/query`：效率指标查询
- `/api/v1/efficiency/metrics/current`：当前班次指标查询

#### 3.4.4 实现要点

- 指标计算公式（OEE = 时间开动率 × 性能开动率 × 设备开动率）
- 班次时间计算（2 班制：08:00-20:00、20:00-次日 08:00）
- 指标排序（升序/降序）
- 数据聚合（工厂级、车间级）

#### 3.4.5 详细设计

##### 3.4.5.1 效率指标查询

**功能说明**：
查询指定指标在指定班次时，所有设备的指标值，支持排序和分页。

**支持的指标**：
- **oee**：OEE（综合设备效率）
- **timeAvailability**：时间开动率
- **performanceRate**：性能开动率
- **equipmentAvailability**：设备开动率
- **downtimeRate**：停机率

**查询参数**：
- tenantId（必填）
- factoryId（必填）
- workshopId（可选）
- metricCode（必填，指标代码）
- shiftDate（可选，班次日期，格式：yyyy-MM-dd）
- shiftCode（可选，班次编码：SHIFT_1/SHIFT_2/SHIFT_3）
- sortBy（可选，排序字段：metricValue/deviceCode，默认 metricValue）
- sortDirection（可选，排序方向：ASC/DESC）
- 分页参数（pageNo、pageSize）

**班次说明**：

- 两班制
  - **SHIFT_1**：早班（08:00-20:00）
  - **SHIFT_2**：晚班（20:00-次日08:00）
- 三班制
  - **SHIFT_1**：早班（08:00-16:00）
  - **SHIFT_2**：中班（16:00-00:00）
  - **SHIFT_3**：晚班（00:00-次日08:00）

- 如果不传 shiftDate 和 shiftCode，则自动使用当前班次（实时查询）

**排序规则**：
- 停机率默认升序（越低越好）
- 其他指标默认降序（越高越好）
- 可通过 sortDirection 参数切换排序方向

**返回数据**：
- 设备ID、设备编号、设备名称
- 设备类型、设备子类型
- 车间ID、车间名称
- 班次日期、班次编码
- 指标代码、指标名称
- 指标值（百分比，如：85.5 表示 85.5%）
- 指标单位（%）
- 是否已最终确定（isFinalized，班次结束后为 true）

**实现要点**：
- 从设备指标表查询指定班次的指标数据
- 如果班次未结束，实时计算指标值
- 支持按指标值或设备编号排序
- 支持分页查询
- 结果缓存（TTL = 30秒，实时数据）

##### 3.4.5.2 当前班次实时指标查询

**功能说明**：
查询指定指标在当前班次时，所有设备的实时指标值。这是查询当前班次实时指标的便捷接口。

**查询参数**：
- 与效率指标查询接口相同
- 不传 shiftDate 和 shiftCode 时，自动使用当前班次

**实现要点**：
- 自动计算当前班次（根据当前时间）
- 实时计算指标值（基于设备状态数据）
- 支持排序和分页
- 结果缓存（TTL = 10秒）

##### 3.4.5.3 指标计算公式

**OEE 计算公式**：
```
OEE = 时间开动率 × 性能开动率 × 设备开动率
```

**时间开动率**：
```
时间开动率 = 实际开动时间 / 计划开动时间 × 100%
实际开动时间 = 班次总时间 - 停机时间 - 待机时间
计划开动时间 = 班次总时间
```

**性能开动率**：
```
性能开动率 = 实际产量 / 理论产量 × 100%
理论产量 = 实际开动时间 × 理论生产速度
```

**设备开动率**：
```
设备开动率 = 合格品数量 / 实际产量 × 100%
```

**停机率**：
```
停机率 = 停机时间 / 班次总时间 × 100%
```

**计算时机**：
- 班次进行中：实时计算（基于当前时间点的数据）
- 班次结束后：最终计算（基于完整班次数据）
- 最终计算结果写入设备指标表，标记为已最终确定（isFinalized = true）

#### 3.4.6 模块依赖关系

**依赖结构**：

```
efficiency-mgmt 模块
├── 依赖 device-base 模块
│   └── 通过 DeviceBaseDataApi 查询设备基础信息
├── 依赖 device-mgmt 模块（可选）
│   └── 查询设备指标数据
├── 依赖 common 模块
│   ├── common-api（公共接口）
│   └── common-domain（公共领域模型）
└── 不依赖其他业务模块
```

**依赖原则**：
- 通过 `DeviceBaseDataApi` 接口查询设备基础信息
- 设备指标数据从设备指标表查询

#### 3.4.7 数据来源

**数据来源**：
- **设备基础信息**：通过 `DeviceBaseDataApi` 接口查询
- **设备指标数据**：从设备指标表（device_metrics）查询
- **实时指标计算**：基于设备状态数据实时计算

### 3.5 数字大屏模块（digital-screen）

#### 3.5.1 模块职责

- 厂区布局图数据查询
- 设备状态统计查询
- 报警时长排行查询
- 工厂级效率指标查询

#### 3.5.2 核心实体

- **FactoryLayout**：厂区布局
- **FactoryStatusSummary**：工厂状态统计
- **AlarmRanking**：报警排行
- **FactoryMetrics**：工厂效率指标

#### 3.5.3 接口设计

- `/api/digital-screen/v1/factories/{factoryId}/layout`：厂区布局图
- `/api/digital-screen/v1/factories/{factoryId}/status-summary`：状态统计
- `/api/digital-screen/v1/factories/{factoryId}/alarm-ranking`：报警排行
- `/api/digital-screen/v1/factories/{factoryId}/metrics`：工厂效率指标

#### 3.5.4 实现要点

- 数据聚合查询（工厂级汇总）
- 实时数据查询（缓存优化）
- Top N 查询优化
- 历史趋势数据查询

#### 3.5.5 详细设计

##### 3.5.5.1 厂区布局图数据查询

**功能说明**：
返回指定厂区下所有设备的布局展示信息，用于前端绘制厂区布局图和跳转设备详情。

**查询参数**：
- factoryId（必填，路径参数）

**返回数据**：
- 工厂ID、工厂名称
- 设备列表（每个设备包含）：
  - 设备ID、设备编号、设备名称
  - 设备类型名称、设备子类型名称
  - 规格型号
  - 当前状态（加工中/待机/故障/关机/未知）

**实现要点**：
- 通过 `DeviceBaseDataApi` 查询工厂下所有设备
- 关联查询设备状态表获取当前状态
- 返回设备列表，前端根据设备配置表的坐标信息绘制布局图
- 结果缓存（TTL = 30秒）

##### 3.5.5.2 设备状态统计查询

**功能说明**：
聚合指定厂区内设备的加工中、待机、故障、关机数量及占比，用于状态监测圆环图展示。

**查询参数**：
- factoryId（必填，路径参数）

**返回数据**：
- 工厂ID、工厂名称
- 设备总数（totalCount）
- 加工中数量（runningCount）
- 待机数量（standbyCount）
- 故障数量（faultCount）
- 关机数量（shutdownCount）
- 各状态占比（runningRatio、standbyRatio、faultRatio、shutdownRatio）

**统计规则**：
- 从设备状态表查询当前状态（最新的状态记录）
- 按状态代码分组统计
- 计算各状态占比（状态数量 / 设备总数）

**实现要点**：
- 查询工厂下所有设备
- 关联查询设备状态表获取当前状态
- 按状态分组统计
- 计算占比
- 结果缓存（TTL = 10秒）

##### 3.5.5.3 报警时长排行查询

**功能说明**：
返回指定厂区当前正在报警且持续时长最长的若干条记录，默认返回 Top5。

**查询参数**：
- factoryId（必填，路径参数）
- limit（可选，返回的排行数量，默认5条）

**返回数据**：
- 设备编号、设备类型、设备子类型
- 报警内容
- 报警持续时长（durationMs，毫秒）

**排序规则**：
- 按报警持续时长降序排序
- 只返回正在报警的记录（is_active = true）

**实现要点**：
- 从报警记录表查询 `is_active = true` 的记录
- 关联设备基础信息表获取设备信息
- 按持续时长降序排序
- 限制返回数量（Top N）
- 结果缓存（TTL = 10秒）

##### 3.5.5.4 工厂级效率指标查询

**功能说明**：
返回指定厂区的实时平均 OEE、平均设备利用率，以及最近 N 天（含当日）按班次的历史趋势数据。

**查询参数**：
- factoryId（必填，路径参数）
- days（可选，历史趋势展示天数，默认7天）

**返回数据**：
- 工厂ID、工厂名称
- 当前平均 OEE（currentAverageOee，0-100，百分比）
- 当前平均设备利用率（currentAverageUtilizationRate，0-100，百分比）
- 历史趋势数据（historyTrend）：
  - 班次日期（shiftDate）
  - 班次编码（shiftCode）
  - 班次名称（shiftName）
  - 平均 OEE（averageOee）
  - 平均设备利用率（averageUtilizationRate）

**计算规则**：
- **当前平均 OEE**：所有设备当前班次的 OEE 平均值
- **当前平均设备利用率**：所有设备当前班次的设备利用率平均值
- **历史趋势**：按班次聚合，计算每个班次的平均指标

**实现要点**：
- 从设备指标表查询所有设备的指标数据
- 按班次聚合计算平均值
- 支持指定历史天数（按班次计算）
- 如果历史数据不足 N 天，返回现有数据
- 结果缓存（TTL = 30秒，实时数据）

#### 3.5.6 模块依赖关系

**依赖结构**：

```
digital-screen 模块
├── 依赖 device-base 模块
│   └── 通过 DeviceBaseDataApi 查询设备基础信息
├── 依赖 device-mgmt 模块（可选）
│   └── 查询设备状态、指标数据
├── 依赖 alarm-mgmt 模块（可选）
│   └── 查询报警数据
├── 依赖 efficiency-mgmt 模块（可选）
│   └── 查询效率指标数据
├── 依赖 common 模块
│   ├── common-api（公共接口）
│   └── common-domain（公共领域模型）
└── 不依赖其他业务模块
```

**依赖原则**：
- 通过公共接口查询各模块数据
- 不直接访问其他模块的内部实现
- 数据聚合在 digital-screen 模块内完成

#### 3.5.7 数据来源

**数据来源**：
- **设备基础信息**：通过 `DeviceBaseDataApi` 接口查询
- **设备状态数据**：从设备状态表查询
- **报警数据**：从报警记录表查询
- **效率指标数据**：从设备指标表查询

### 3.6 数据同步模块（data-sync）

#### 3.6.1 模块职责

- 接收 TB Webhook 数据
- 数据转换和存储
- 数据补偿（定时任务）
- 数据对账（定时任务）

#### 3.6.2 核心组件

- **WebhookController**：接收 Webhook 请求
- **DataSyncConsumer**：数据同步消费者
- **DataSyncScheduler**：数据同步调度器
- **DataReconciliationTask**：数据对账任务

#### 3.6.3 实现要点

- Webhook 可靠性保障（重试、幂等）
- 数据缓冲机制（失败重试）
- 数据对账机制（TB 与 Portal 数据一致性）
- 数据补偿机制（定时拉取 TB 数据）

#### 3.6.4 详细设计

##### 3.6.4.1 Webhook 接收处理

**功能说明**：
接收来自 TB 的 Webhook 请求，快速响应并异步处理。

**接收流程**：
1. **接收请求**：WebhookController 接收 HTTP POST 请求
2. **参数解析**：解析请求头和请求体
3. **幂等性检查**：基于 messageId 检查是否已处理
4. **快速 ACK**：立即返回 200 响应
5. **写入收件箱**：将消息写入收件箱表（portal_webhook_inbox）
6. **异步处理**：触发异步处理任务

**请求格式**：
- **请求头**：`X-Message-Id`（消息ID，用于幂等性）
- **请求体**：JSON 格式，包含事件类型和事件数据

**事件类型**：
- `DEVICE_CREATED`：设备创建事件
- `DEVICE_UPDATED`：设备更新事件
- `DEVICE_DELETED`：设备删除事件
- `ALARM_CREATED`：报警创建事件
- `ALARM_UPDATED`：报警更新事件
- `STATE_CHANGED`：设备状态变更事件
- `ATTRIBUTES_UPDATED`：设备属性更新事件

**实现要点**：
- 幂等性保障：基于 messageId 去重
- 快速响应：收到请求立即返回 200
- 异步处理：避免阻塞 Webhook 响应
- 错误处理：处理失败时记录错误信息

##### 3.6.4.2 数据转换处理

**功能说明**：
将 TB 事件数据转换为 Portal 业务数据。

**转换规则**：

1. **设备事件转换**：
   - TB 设备数据 → Portal 设备基础信息
   - 字段映射：设备名称、设备类型、设备属性等
   - 关联数据查询：根据 TB 设备类型查询 Portal 设备类型

2. **报警事件转换**：
   - TB 报警数据 → Portal 报警记录
   - 字段映射：报警号、报警内容、报警级别等
   - 关联设备：根据 TB 设备ID查询 Portal 设备ID

3. **状态事件转换**：
   - TB 状态数据 → Portal 设备状态
   - 字段映射：状态代码、状态名称、开始时间等
   - 状态计算：计算状态持续时长

4. **属性事件转换**：
   - TB 属性数据 → Portal 设备配置
   - 字段映射：IP地址、位置信息等

**实现要点**：
- 数据映射：TB 字段 → Portal 字段
- 数据格式转换：时间格式、枚举值转换
- 关联数据查询：查询关联的 Portal 数据
- 数据校验：校验数据完整性和有效性

##### 3.6.4.3 数据补偿任务

**功能说明**：
定时任务拉取 TB 数据，对比 Portal 数据，补充缺失数据。

**补偿场景**：
- Webhook 丢失：网络故障导致 Webhook 未送达
- Portal 处理失败：处理过程中发生异常
- 数据对账发现差异：定时对账发现数据不一致

**补偿流程**：
1. **定时触发**：XXL-Job 定时任务触发（每小时一次）
2. **查询 TB 数据**：调用 TB REST API 查询设备列表
3. **对比 Portal 数据**：对比 TB 和 Portal 的设备列表
4. **发现差异**：找出 TB 中存在但 Portal 中不存在的设备
5. **补充数据**：创建缺失的设备基础信息

**补偿范围**：
- 设备基础信息
- 设备配置信息
- 设备状态数据（可选）

**实现要点**：
- 按租户补偿：遍历所有租户
- 增量补偿：只补偿缺失的数据
- 批量处理：批量查询和批量创建，提升性能
- 错误处理：补偿失败时记录日志

##### 3.6.4.4 数据对账任务

**功能说明**：
定时对账 TB 和 Portal 的数据，发现数据不一致并记录。

**对账策略**：
- **定时对账**：每天凌晨对账前一天数据
- **对账范围**：设备基础信息、设备状态、报警记录
- **差异处理**：发现差异后记录到对账日志表

**对账流程**：
1. **定时触发**：XXL-Job 定时任务触发（每天凌晨）
2. **查询 TB 数据**：调用 TB REST API 查询指定时间范围的数据
3. **查询 Portal 数据**：从 Portal 数据库查询相同时间范围的数据
4. **对比差异**：对比 TB 和 Portal 的数据
5. **记录差异**：将差异记录到对账日志表
6. **触发补偿**：发现差异后自动触发补偿任务

**对账维度**：
- **设备级别**：对比设备数量、设备信息
- **时间级别**：对比指定时间范围内的数据
- **事件级别**：对比事件数量、事件内容

**实现要点**：
- 按时间范围对账：对账前一天的数据
- 按设备对账：逐个设备对比
- 差异记录：记录差异类型、差异内容
- 自动补偿：发现差异后自动触发补偿

#### 3.6.5 模块依赖关系

**依赖结构**：

```
data-sync 模块
├── 依赖 device-base 模块
│   └── 通过 DeviceBaseDataApi 查询/创建设备基础信息
├── 依赖 device-mgmt 模块（可选）
│   └── 同步设备状态、指标数据
├── 依赖 alarm-mgmt 模块（可选）
│   └── 同步报警数据
├── 依赖 common 模块
│   ├── common-api（公共接口）
│   └── common-domain（公共领域模型）
└── 依赖外部系统
    └── TB REST API（查询 TB 数据）
```

**依赖原则**：
- 通过公共接口操作各业务模块
- 不直接访问业务模块的内部实现
- 数据同步逻辑统一在 data-sync 模块

#### 3.6.6 数据来源

**数据来源**：
- **TB Webhook**：接收 TB 推送的事件数据
- **TB REST API**：主动拉取 TB 数据（补偿、对账场景）
- **Portal 数据库**：查询 Portal 现有数据（对比、校验）

---

## 4. 数据库设计

### 4.1 数据库选型

- **主数据库**：MySQL 8.0+
- **缓存数据库**：Redis 6.0+
- **设计原则**：第三范式、适度冗余、索引优化

### 4.2 命名规范

- **表名**：小写字母 + 下划线，复数形式（如：`device_base_infos`）
- **字段名**：小写字母 + 下划线（如：`device_code`）
- **索引名**：`idx_` + 表名 + 字段名（如：`idx_device_base_infos_device_code`）
- **主键**：统一使用 `id`（UUID 或自增 ID）

### 4.3 核心表设计

#### 4.3.1 设备基础数据表

**设备基础信息表（device_base_infos）**

| 字段名 | 类型 | 说明 | 索引 |
|--------|------|------|------|
| id | VARCHAR(36) | 主键（UUID） | PRIMARY |
| tenant_id | VARCHAR(36) | 租户ID | INDEX |
| tb_device_id | VARCHAR(36) | TB设备ID | UNIQUE |
| device_code | VARCHAR(50) | 设备编号 | UNIQUE |
| device_name | VARCHAR(100) | 设备名称 | INDEX |
| device_type_id | VARCHAR(36) | 设备类型ID | INDEX |
| device_model_id | VARCHAR(36) | 设备型号ID | INDEX |
| organization_unit_id | VARCHAR(36) | 组织单元ID | INDEX |
| device_status | VARCHAR(20) | 设备状态 | INDEX |
| is_monitored | TINYINT(1) | 是否监控 | INDEX |
| created_time | BIGINT | 创建时间（毫秒） | INDEX |
| updated_time | BIGINT | 更新时间（毫秒） | |
| created_by | VARCHAR(50) | 创建人 | |
| updated_by | VARCHAR(50) | 更新人 | |
| remarks | TEXT | 备注 | |
| extra_properties | JSON | 扩展属性 | |

**设备类型表（device_types）**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | VARCHAR(36) | 主键 |
| tenant_id | VARCHAR(36) | 租户ID |
| type_code | VARCHAR(50) | 类型编码 |
| type_name | VARCHAR(100) | 类型名称 |
| parent_type_id | VARCHAR(36) | 父类型ID |
| level | INT | 层级（1-主类型，2-子类型） |
| category | VARCHAR(50) | 分类 |
| description | TEXT | 描述 |
| icon | VARCHAR(200) | 图标 |
| is_active | TINYINT(1) | 是否启用 |
| sort_order | INT | 排序顺序 |

**设备型号表（device_models）**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | VARCHAR(36) | 主键 |
| tenant_id | VARCHAR(36) | 租户ID |
| model_code | VARCHAR(50) | 型号编码 |
| model_name | VARCHAR(100) | 型号名称 |
| device_type_id | VARCHAR(36) | 设备类型ID |
| manufacturer | VARCHAR(100) | 制造商 |
| specifications | JSON | 规格参数 |
| type_specific_attrs | JSON | 类型特定属性 |
| is_active | TINYINT(1) | 是否启用 |

**组织单元表（organization_units）**

| 字段名 | 类型 | 说明 | 索引 |
|--------|------|------|------|
| id | VARCHAR(36) | 主键 | PRIMARY |
| tenant_id | VARCHAR(36) | 租户ID | INDEX |
| unit_code | VARCHAR(50) | 组织单元编码 | UNIQUE |
| unit_name | VARCHAR(100) | 组织单元名称 | INDEX |
| unit_type | VARCHAR(20) | 类型（FACTORY/WORKSHOP/LINE） | INDEX |
| parent_id | VARCHAR(36) | 父组织单元ID | INDEX |
| parent_name | VARCHAR(100) | 父组织单元名称（冗余） | - |
| level | INT | 层级（1-厂区，2-车间，3-产线） | INDEX |
| path | VARCHAR(500) | 路径 | - |
| description | TEXT | 描述 | - |
| location | VARCHAR(200) | 位置 | - |
| is_active | TINYINT(1) | 是否启用 | INDEX |
| sort_order | INT | 排序顺序 | - |
| created_time | BIGINT | 创建时间（毫秒） | INDEX |
| updated_time | BIGINT | 更新时间（毫秒） | - |
| created_by | VARCHAR(50) | 创建人 | - |
| updated_by | VARCHAR(50) | 更新人 | - |

**设备配置表（device_configurations）**

| 字段名 | 类型 | 说明 | 索引 |
|--------|------|------|------|
| id | VARCHAR(36) | 主键 | PRIMARY |
| device_id | VARCHAR(36) | 设备ID | UNIQUE, FK |
| device_code | VARCHAR(50) | 设备编号（冗余） | - |
| device_name | VARCHAR(100) | 设备名称（冗余） | - |
| ip_address | VARCHAR(50) | IP地址 | INDEX |
| port | INT | 端口号 | - |
| mac_address | VARCHAR(50) | MAC地址 | - |
| gateway | VARCHAR(50) | 网关地址 | - |
| subnet_mask | VARCHAR(50) | 子网掩码 | - |
| protocol | VARCHAR(50) | 通信协议 | INDEX |
| connection_params | JSON | 连接参数 | - |
| location_code | VARCHAR(50) | 位置编码 | INDEX |
| location_description | VARCHAR(200) | 位置描述 | - |
| coordinates | JSON | 坐标信息 | - |
| created_time | BIGINT | 创建时间（毫秒） | - |
| updated_time | BIGINT | 更新时间（毫秒） | - |
| updated_by | VARCHAR(50) | 更新人 | - |

**设备关系表（device_relations）**

| 字段名 | 类型 | 说明 | 索引 |
|--------|------|------|------|
| id | VARCHAR(36) | 主键 | PRIMARY |
| tenant_id | VARCHAR(36) | 租户ID | INDEX |
| from_device_id | VARCHAR(36) | 源设备ID | INDEX, FK |
| from_device_code | VARCHAR(50) | 源设备编号（冗余） | - |
| from_device_name | VARCHAR(100) | 源设备名称（冗余） | - |
| to_device_id | VARCHAR(36) | 目标设备ID | INDEX, FK |
| to_device_code | VARCHAR(50) | 目标设备编号（冗余） | - |
| to_device_name | VARCHAR(100) | 目标设备名称（冗余） | - |
| relation_type | VARCHAR(50) | 关系类型 | INDEX |
| relation_name | VARCHAR(100) | 关系名称 | - |
| description | TEXT | 关系描述 | - |
| properties | JSON | 关系属性 | - |
| is_active | TINYINT(1) | 是否启用 | INDEX |
| created_time | BIGINT | 创建时间（毫秒） | - |
| updated_time | BIGINT | 更新时间（毫秒） | - |
| created_by | VARCHAR(50) | 创建人 | - |
| updated_by | VARCHAR(50) | 更新人 | - |

**索引设计说明**：

1. **唯一索引**：
   - `device_base_infos.device_code`：设备编号在租户内唯一
   - `device_base_infos.tb_device_id`：TB设备ID全局唯一
   - `device_types.type_code`：类型编码在租户内唯一
   - `device_models.model_code`：型号编码在租户内唯一
   - `organization_units.unit_code`：组织单元编码在租户内唯一
   - `device_configurations.device_id`：设备配置与设备一对一关系
   - `device_relations(from_device_id, to_device_id, relation_type)`：设备关系唯一性

2. **联合索引**：
   - `(tenant_id, factory_id, device_code)`：设备查询优化
   - `(tenant_id, device_type_id)`：按类型查询优化
   - `(tenant_id, organization_unit_id)`：按组织单元查询优化
   - `(tenant_id, parent_id, level)`：树形结构查询优化

3. **普通索引**：
   - 所有 `tenant_id` 字段：多租户数据隔离
   - 所有外键字段：关联查询优化
   - 时间字段：时间范围查询优化
   - 状态字段：状态筛选优化

#### 4.3.2 设备管理业务表

**设备指标表（device_metrics）**

| 字段名 | 类型 | 说明 | 索引 |
|--------|------|------|------|
| id | VARCHAR(36) | 主键 | PRIMARY |
| tenant_id | VARCHAR(36) | 租户ID | INDEX |
| device_id | VARCHAR(36) | 设备ID | INDEX |
| shift_date | DATE | 班次日期 | INDEX |
| shift_code | VARCHAR(20) | 班次编码 | INDEX |
| metric_code | VARCHAR(50) | 指标代码 | INDEX |
| metric_value | DECIMAL(10,2) | 指标值 | |
| is_finalized | TINYINT(1) | 是否已最终确定 | |
| calculated_time | BIGINT | 计算时间（毫秒） | |
| created_time | BIGINT | 创建时间（毫秒） | |

**设备状态表（device_states）**

| 字段名 | 类型 | 说明 | 索引 |
|--------|------|------|------|
| id | VARCHAR(36) | 主键 | PRIMARY |
| tenant_id | VARCHAR(36) | 租户ID | INDEX |
| device_id | VARCHAR(36) | 设备ID | INDEX |
| state_code | VARCHAR(50) | 状态代码 | INDEX |
| state_name | VARCHAR(100) | 状态名称 | |
| start_time | BIGINT | 开始时间（毫秒） | INDEX |
| end_time | BIGINT | 结束时间（毫秒） | INDEX |
| duration_ms | BIGINT | 持续时间（毫秒） | |

**报警记录表（alarm_records）**

| 字段名 | 类型 | 说明 | 索引 |
|--------|------|------|------|
| id | VARCHAR(36) | 主键 | PRIMARY |
| tenant_id | VARCHAR(36) | 租户ID | INDEX |
| device_id | VARCHAR(36) | 设备ID | INDEX |
| alarm_code | VARCHAR(50) | 报警号 | INDEX |
| alarm_text | VARCHAR(500) | 报警内容 | |
| alarm_level | VARCHAR(20) | 报警级别 | INDEX |
| start_time | BIGINT | 开始时间（毫秒） | INDEX |
| end_time | BIGINT | 结束时间（毫秒） | INDEX |
| duration_ms | BIGINT | 持续时间（毫秒） | |
| is_active | TINYINT(1) | 是否报警中 | INDEX |
| start_shift_date | DATE | 开始班次日期 | |
| start_shift_code | VARCHAR(20) | 开始班次编码 | |
| end_shift_date | DATE | 结束班次日期 | |
| end_shift_code | VARCHAR(20) | 结束班次编码 | |

#### 4.3.3 数据同步表

**Webhook 收件箱表（portal_webhook_inbox）**

| 字段名 | 类型 | 说明 | 索引 |
|--------|------|------|------|
| id | VARCHAR(36) | 主键 | PRIMARY |
| message_id | VARCHAR(100) | 消息ID（幂等性） | UNIQUE |
| tenant_id | VARCHAR(36) | 租户ID | INDEX |
| event_type | VARCHAR(50) | 事件类型 | INDEX |
| payload | JSON | 消息体 | |
| status | VARCHAR(20) | 状态（PENDING/PROCESSED/FAILED） | INDEX |
| process_time | BIGINT | 处理时间（毫秒） | |
| error_message | TEXT | 错误信息 | |
| created_time | BIGINT | 创建时间（毫秒） | INDEX |

### 4.4 索引设计原则

- **主键索引**：所有表必须有主键
- **唯一索引**：业务唯一字段（device_code、message_id）
- **普通索引**：查询频繁字段（tenant_id、device_id、时间字段）
- **联合索引**：多字段查询（tenant_id + device_id + start_time）
- **索引优化**：避免过度索引，定期分析慢查询

### 4.5 分库分表策略

- **当前阶段**：单库单表
- **未来扩展**：按租户分库、按时间分表（历史数据）

---

## 5. 接口设计

### 5.1 RESTful API 规范

#### 5.1.1 URL 设计

- **基础路径**：`/api/{module}/v{version}`
- **资源路径**：使用名词复数形式（如：`/devices`、`/alarms`）
- **操作路径**：使用动词（如：`/query`、`/page`、`/statistics`）

**示例**：
```
GET    /api/device-mgmt/v1/devices/{id}           # 查询设备详情
POST   /api/device-mgmt/v1/devices/page          # 分页查询设备
GET    /api/device-mgmt/v1/devices/{id}/metrics  # 查询设备指标
```

#### 5.1.2 HTTP 方法

| 方法 | 用途 | 示例 |
|------|------|------|
| GET | 查询资源 | `GET /api/devices/{id}` |
| POST | 创建资源、复杂查询 | `POST /api/devices`、`POST /api/devices/page` |
| PUT | 更新资源 | `PUT /api/devices/{id}` |
| DELETE | 删除资源 | `DELETE /api/devices/{id}` |

#### 5.1.3 请求参数

**Query 参数**：
- 简单查询参数（如：`?tenantId=xxx&factoryId=xxx`）
- 分页参数（如：`?pageNo=1&pageSize=10`）

**Path 参数**：
- 资源ID（如：`/devices/{id}`）

**Body 参数**：
- 复杂查询条件（POST 请求）
- 创建/更新数据（POST/PUT 请求）

#### 5.1.4 响应格式

**统一响应结构**：
```json
{
  "code": 200,
  "message": "success",
  "timestamp": 1705488000000,
  "data": {
    // 业务数据
  }
}
```

**分页响应结构**：
```json
{
  "code": 200,
  "message": "success",
  "timestamp": 1705488000000,
  "data": {
    "total": 100,
    "pageNo": 1,
    "pageSize": 10,
    "totalPages": 10,
    "records": [
      // 数据列表
    ]
  }
}
```

### 5.2 接口文档

- **文档格式**：OpenAPI 3.0（YAML）
- **文档位置**：`api-docs/openapi-{module}-v{version}.yaml`
- **文档生成**：Swagger UI、Postman

### 5.3 接口版本管理

- **版本号**：URL 路径中体现（如：`/api/v1/`、`/api/v2/`）
- **版本兼容**：向后兼容，新版本不破坏旧版本接口
- **版本废弃**：提前通知，逐步迁移

---

## 6. 核心功能实现

### 6.1 设备基础数据管理

#### 6.1.1 树形结构处理

**设备类型树形结构**：

**实现思路**：
1. **层级限制**：最多支持2层（主类型、子类型）
2. **父节点查询**：通过 `parent_type_id` 关联父类型
3. **子节点查询**：通过 `parent_type_id` 查询所有子类型
4. **树形构建**：递归构建树形结构，支持前端树形组件展示
5. **路径维护**：维护从根到当前节点的路径（可选）

**关键要点**：
- 主类型的 `parent_type_id` 为 NULL
- 子类型必须关联父类型
- 删除父类型时，检查是否有子类型或设备使用

**组织单元树形结构**：

**实现思路**：
1. **三级结构**：厂区（level=1）→ 车间（level=2）→ 产线（level=3）
2. **路径字段**：维护 `path` 字段，存储完整路径（如：`/厂区1/车间1/产线1`）
3. **路径更新**：父节点路径变更时，级联更新所有子节点路径
4. **快速查询**：通过 `path` 字段快速查询某个节点下的所有子节点

**关键要点**：
- 路径字段便于快速查询和组织结构展示
- 路径更新需要事务保障，确保一致性
- 删除节点时，检查是否有子节点或设备

#### 6.1.2 冗余字段维护

**冗余字段设计目的**：
- 减少关联查询，提升查询性能
- 支持历史数据查询（即使关联数据被删除，仍可查看历史信息）
- 简化前端展示逻辑

**冗余字段列表**：
- 设备基础信息：`device_type_name`、`model_name`、`factory_name`、`workshop_name`、`production_line_name`
- 设备类型：`parent_type_name`
- 组织单元：`parent_name`
- 设备关系：`from_device_code`、`from_device_name`、`to_device_code`、`to_device_name`

**维护策略**：

1. **创建时填充**：
   - 创建设备时，从关联表查询并填充冗余字段
   - 例如：创建设备时，从设备类型表查询 `type_name` 填充到 `device_type_name`

2. **更新时同步**：
   - 更新关联数据时，同步更新所有关联记录的冗余字段
   - 例如：更新设备类型名称时，同步更新所有使用该类型的设备的 `device_type_name`

3. **定时校验**：
   - 定时任务校验冗余字段一致性
   - 发现不一致时自动修复
   - 记录不一致日志，便于问题排查

**实现要点**：
- 冗余字段更新使用事务保障
- 批量更新时使用批量SQL，提升性能
- 校验任务在业务低峰期执行

#### 6.1.3 软删除实现

**软删除设计**：
- 使用 `is_active` 字段标识记录是否启用
- `is_active = true`：启用状态
- `is_active = false`：禁用状态（软删除）

**软删除规则**：

1. **设备类型软删除**：
   - 删除前检查：是否有设备使用该类型
   - 有设备使用时：不允许删除，只能禁用
   - 无设备使用时：可以删除（物理删除或软删除）

2. **组织单元软删除**：
   - 删除前检查：是否有子单元或设备
   - 有子单元或设备时：不允许删除，只能禁用
   - 无子单元和设备时：可以删除

3. **设备软删除**：
   - 删除设备时，检查是否有业务数据关联
   - 有业务数据时：软删除（is_active = false）
   - 无业务数据时：可以物理删除

**查询过滤**：
- 所有查询默认过滤 `is_active = true` 的记录
- 需要查询已删除数据时，显式指定 `is_active = false`

#### 6.1.4 数据校验和约束检查

**唯一性校验**：

1. **设备编号唯一性**：
   - 在租户内唯一（tenant_id + device_code）
   - 创建/更新时校验
   - 使用数据库唯一索引保障

2. **类型编码唯一性**：
   - 在租户内唯一（tenant_id + type_code）
   - 创建/更新时校验

3. **型号编码唯一性**：
   - 在租户内唯一（tenant_id + model_code）
   - 创建/更新时校验

4. **组织单元编码唯一性**：
   - 在租户内唯一（tenant_id + unit_code）
   - 创建/更新时校验

**关联关系校验**：

1. **外键存在性校验**：
   - 设备类型ID必须存在
   - 设备型号ID必须存在
   - 组织单元ID必须存在
   - 创建/更新时校验

2. **层级关系校验**：
   - 设备类型的层级关系（子类型必须关联父类型）
   - 组织单元的层级关系（车间必须关联厂区，产线必须关联车间）
   - 创建/更新时校验

3. **循环引用校验**：
   - 树形结构中不能出现循环引用
   - 例如：A的父节点是B，B的父节点不能是A

**业务规则校验**：

1. **必填字段校验**：
   - 设备编号、设备名称、设备类型、设备型号、组织单元为必填
   - 使用 `@NotNull`、`@NotBlank` 注解校验

2. **数据格式校验**：
   - 设备编号格式校验（字母数字下划线）
   - IP地址格式校验
   - 时间格式校验

3. **数据范围校验**：
   - 层级范围（1-3）
   - 状态枚举值校验

### 6.2 设备数据同步

#### 6.2.1 Webhook 接收

**实现流程**：
1. TB Rule Engine 触发 Webhook
2. Portal WebhookController 接收请求
3. 快速 ACK（200 响应）
4. 异步处理（写入收件箱）
5. 数据转换和存储

**关键要点**：
- 幂等性检查：基于 messageId 去重
- 快速响应：收到请求立即返回 200，避免超时
- 异步处理：写入收件箱后异步处理，提升响应速度
- 错误处理：处理失败时记录错误信息，支持重试

#### 6.2.2 数据转换

**转换规则**：
- TB 设备事件 → Portal 设备基础信息
- TB 设备属性变更 → Portal 设备配置更新
- TB 设备关系 → Portal 设备关系

**转换要点**：
- 字段映射：TB 字段 → Portal 字段
- 数据格式转换：时间格式、枚举值转换
- 关联数据查询：根据 TB 设备ID查询 Portal 设备，如果不存在则创建

#### 6.2.3 数据补偿

**补偿机制**：
- 定时任务（XXL-Job）拉取 TB 数据
- 对比 Portal 数据，发现缺失
- 补充缺失数据

**补偿场景**：
- Webhook 丢失：网络故障导致 Webhook 未送达
- Portal 处理失败：处理过程中发生异常
- 数据对账发现差异：定时对账发现数据不一致

### 6.3 设备指标计算

#### 6.2.1 OEE 计算

**计算公式**：
```
OEE = 时间开动率 × 性能开动率 × 设备开动率

时间开动率 = 实际开动时间 / 计划开动时间
性能开动率 = 实际产量 / 理论产量
设备开动率 = 合格品数量 / 实际产量
```

**实现要点**：
- 按班次计算
- 实时计算（当前班次）
- 历史计算（已结束班次）

#### 6.2.2 状态统计

**统计维度**：
- 设备状态分布（加工中、待机、故障、关机）
- 状态时长统计
- 状态占比计算

### 6.4 实时数据推送

#### 6.3.1 WebSocket 连接管理

**连接管理**：
- 连接建立（握手）
- 连接保持（心跳）
- 连接断开（清理）

**推送策略**：
- 单播推送（指定设备）
- 广播推送（所有设备）
- 组播推送（工厂/车间）

#### 6.3.2 报警实时推送

**推送流程**：
1. 报警事件触发
2. 查询订阅用户
3. WebSocket 推送消息
4. 前端接收并展示

### 6.5 数据查询优化

#### 6.4.1 分页查询优化

**优化策略**：
- 索引优化（联合索引）
- 分页参数限制（max pageSize = 200）
- 深度分页优化（游标分页）

#### 6.4.2 时间范围查询优化

**优化策略**：
- 时间字段索引
- 分区表（按时间分区）
- 查询条件优化（避免全表扫描）

#### 6.4.3 缓存策略

**缓存场景**：
- 设备基础信息（Redis 缓存）
- 组织单元树（Redis 缓存）
- 实时指标（Redis 缓存，TTL = 10s）

---

## 7. 数据同步方案

### 7.1 Webhook 可靠性保障

#### 7.1.1 TB 端保障

- **本地缓冲表**：Webhook 发送失败时写入缓冲表
- **重试机制**：指数退避重试（maxAttempts = 3）
- **死信处理**：超过重试次数进入死信表

#### 7.1.2 Portal 端保障

- **快速 ACK**：收到请求立即返回 200
- **幂等性控制**：基于 messageId 去重
- **异步处理**：写入收件箱后异步处理
- **失败重试**：处理失败后定时重试

### 7.2 数据对账机制

#### 7.2.1 对账策略

- **定时对账**：每天凌晨对账前一天数据
- **对账范围**：设备状态、报警记录、指标数据
- **差异处理**：发现差异后自动补偿

#### 7.2.2 对账实现

**对账流程**：
1. 查询 TB 数据（指定时间范围）
2. 查询 Portal 数据（相同时间范围）
3. 对比差异（设备级别、时间级别）
4. 补偿缺失数据

### 7.3 数据补偿机制

#### 7.3.1 补偿场景

- Webhook 丢失：网络故障导致 Webhook 未送达
- Portal 处理失败：处理过程中发生异常
- 数据对账发现差异：定时对账发现数据不一致

#### 7.3.2 补偿实现

**补偿流程**：
1. 定时任务触发（XXL-Job）
2. 查询 TB API（拉取数据）
3. 对比 Portal 数据
4. 补充缺失数据

**设备基础数据补偿**：

1. **补偿范围**：
   - 设备基础信息（根据 TB 设备ID对比）
   - 设备配置信息（根据设备ID对比）

2. **补偿策略**：
   - 按租户补偿：遍历所有租户
   - 按时间范围补偿：补偿指定时间范围内的数据
   - 增量补偿：只补偿缺失的数据

3. **补偿频率**：
   - 每小时补偿一次（增量补偿）
   - 每天凌晨全量对账（全量补偿）

### 7.4 设备基础数据同步详细方案

#### 7.4.1 TB 设备创建事件同步

**事件类型**：`DEVICE_CREATED`

**同步流程**：
1. TB Rule Engine 检测到设备创建事件
2. 触发 Webhook，发送设备信息到 Portal
3. Portal 接收 Webhook，解析设备信息
4. 检查设备是否已存在（根据 tb_device_id）
5. 如果不存在，创建设备基础信息
6. 填充关联字段（设备类型、组织单元等）
7. 保存到数据库

**同步字段映射**：
- TB 设备名称 → Portal 设备名称
- TB 设备类型 → Portal 设备类型（需要映射）
- TB 设备属性 → Portal 设备配置
- TB 租户ID → Portal 租户ID

#### 7.4.2 TB 设备更新事件同步

**事件类型**：`DEVICE_UPDATED`

**同步流程**：
1. TB Rule Engine 检测到设备更新事件
2. 触发 Webhook，发送更新后的设备信息
3. Portal 接收 Webhook，解析设备信息
4. 根据 tb_device_id 查询 Portal 设备
5. 更新设备基础信息
6. 同步更新冗余字段
7. 保存到数据库

**更新策略**：
- 全量更新：使用 TB 数据覆盖 Portal 数据
- 增量更新：只更新变更的字段（需要对比）

#### 7.4.3 TB 设备删除事件同步

**事件类型**：`DEVICE_DELETED`

**同步流程**：
1. TB Rule Engine 检测到设备删除事件
2. 触发 Webhook，发送设备ID
3. Portal 接收 Webhook，解析设备ID
4. 根据 tb_device_id 查询 Portal 设备
5. 软删除设备（is_active = false）
6. 保留历史数据，便于查询

**删除策略**：
- 软删除：保留数据，只标记为禁用
- 保留关联数据：保留设备的历史业务数据（指标、报警等）

#### 7.4.4 设备属性同步

**同步场景**：
- TB 设备属性变更 → Portal 设备配置更新
- 例如：IP地址变更、位置信息变更

**同步流程**：
1. TB Rule Engine 检测到设备属性变更
2. 触发 Webhook，发送属性变更信息
3. Portal 接收 Webhook，解析属性信息
4. 更新设备配置表
5. 如果设备配置不存在，则创建

#### 7.4.5 同步失败处理

**失败场景**：
- Webhook 接收失败（网络超时、服务不可用）
- 数据转换失败（字段映射错误、数据格式错误）
- 数据保存失败（数据库异常、约束冲突）

**处理策略**：

1. **重试机制**：
   - TB 端：指数退避重试（maxAttempts = 3）
   - Portal 端：定时任务重试失败的消息

2. **错误记录**：
   - 记录失败原因到错误日志表
   - 记录失败时间、重试次数等信息

3. **告警通知**：
   - 连续失败超过阈值时发送告警
   - 通知运维人员处理

4. **手动补偿**：
   - 提供手动补偿接口
   - 支持按设备ID、时间范围手动触发补偿

---

## 8. 安全与权限

### 8.1 认证授权

#### 8.1.1 JWT Token 认证

- **Token 生成**：登录成功后生成 JWT Token
- **Token 验证**：每个请求验证 Token
- **Token 刷新**：Token 过期前刷新

#### 8.1.2 权限控制

- **角色权限**：基于角色的访问控制（RBAC）
- **接口权限**：接口级别的权限控制
- **数据权限**：租户级别的数据隔离

### 8.2 数据安全

#### 8.2.1 数据隔离

- **租户隔离**：所有查询必须带 tenantId
- **数据加密**：敏感数据加密存储
- **SQL 注入防护**：使用参数化查询

#### 8.2.2 接口安全

- **参数校验**：请求参数校验（@Valid）
- **频率限制**：接口调用频率限制
- **HTTPS**：生产环境使用 HTTPS

---

## 9. 性能优化

### 9.1 数据库优化

- **索引优化**：合理设计索引，避免过度索引
- **SQL 优化**：避免全表扫描，使用 EXPLAIN 分析
- **连接池优化**：合理配置连接池参数

### 9.2 缓存优化

- **缓存策略**：Cache-Aside、Write-Through
- **缓存失效**：合理设置 TTL，及时失效
- **缓存穿透**：空值缓存，防止穿透

### 9.3 接口优化

- **异步处理**：耗时操作异步处理
- **批量操作**：批量查询、批量更新
- **分页限制**：限制单次查询数据量

### 9.4 设备基础数据模块优化

#### 9.4.1 树形结构查询优化

**优化策略**：

1. **路径字段优化**：
   - 组织单元表维护 `path` 字段
   - 通过 `path LIKE '/厂区1/%'` 快速查询所有子节点
   - 避免递归查询，提升性能

2. **层级索引**：
   - 在 `(tenant_id, parent_id, level)` 上建立联合索引
   - 支持按层级快速查询

3. **树形缓存**：
   - 设备类型树缓存到 Redis
   - 组织单元树缓存到 Redis
   - TTL = 1 小时，变更时清除缓存

#### 9.4.2 冗余字段更新优化

**优化策略**：

1. **批量更新**：
   - 更新关联数据时，批量更新所有冗余字段
   - 使用 `UPDATE ... WHERE ... IN (...)` 批量SQL
   - 避免逐条更新，提升性能

2. **异步更新**：
   - 冗余字段更新可以异步执行
   - 先更新主数据，再异步更新冗余字段
   - 保证最终一致性

3. **增量更新**：
   - 只更新变更的冗余字段
   - 对比新旧值，只更新有变化的字段

#### 9.4.3 唯一性校验优化

**优化策略**：

1. **数据库唯一索引**：
   - 在数据库层面建立唯一索引
   - 利用数据库的约束保障唯一性
   - 减少应用层校验，提升性能

2. **缓存预校验**：
   - 使用 Redis Set 缓存已存在的编码
   - 先查缓存，未命中再查数据库
   - 减少数据库查询次数

3. **批量校验**：
   - 批量创建时，批量查询已存在的编码
   - 一次性校验所有编码，减少数据库交互

#### 9.4.4 关联查询优化

**优化策略**：

1. **冗余字段减少关联**：
   - 通过冗余字段减少关联查询
   - 例如：设备列表查询不需要关联设备类型表

2. **批量关联查询**：
   - 批量查询时，先收集所有关联ID
   - 批量查询关联数据，减少查询次数
   - 在内存中组装数据

3. **延迟加载**：
   - 非必要字段延迟加载
   - 例如：设备详情才加载完整配置信息
   - 列表查询只加载必要字段

---

## 10. 部署运维

### 10.1 部署架构

#### 10.1.1 部署环境

- **开发环境**：本地开发
- **测试环境**：集成测试
- **预生产环境**：生产验证
- **生产环境**：正式运行

#### 10.1.2 部署方式

- **容器化部署**：Docker + Kubernetes
- **传统部署**：JAR 包 + Systemd
- **负载均衡**：Nginx / HAProxy

### 10.2 监控告警

#### 10.2.1 应用监控

- **JVM 监控**：内存、GC、线程
- **接口监控**：响应时间、错误率
- **业务监控**：数据同步量、处理延迟

#### 10.2.2 告警规则

- **错误率告警**：接口错误率 > 1%
- **响应时间告警**：P99 响应时间 > 1s
- **数据同步告警**：数据同步延迟 > 5min

### 10.3 日志管理

- **日志级别**：DEBUG、INFO、WARN、ERROR
- **日志格式**：JSON 格式，便于解析
- **日志收集**：ELK / Loki + Grafana

---

## 11. 开发规范

### 11.1 代码规范

#### 11.1.1 命名规范

- **类名**：大驼峰（如：`DeviceBaseInfoService`）
- **方法名**：小驼峰（如：`getDeviceById`）
- **常量名**：大写下划线（如：`MAX_PAGE_SIZE`）
- **包名**：小写字母（如：`com.weili.iot_portal`）

#### 11.1.2 代码风格

- **缩进**：4 个空格
- **行长度**：不超过 120 字符
- **注释**：类、方法必须有注释
- **异常处理**：统一异常处理，不吞异常

### 11.2 Git 规范

#### 11.2.1 分支管理

- **主分支**：`main`（生产环境）
- **开发分支**：`develop`（开发环境）
- **功能分支**：`feature/xxx`（功能开发）
- **修复分支**：`hotfix/xxx`（紧急修复）

#### 11.2.2 提交规范

**提交格式**：
```
<type>(<scope>): <subject>

<body>

<footer>
```

**类型说明**：
- `feat`：新功能
- `fix`：修复 Bug
- `docs`：文档更新
- `style`：代码格式
- `refactor`：重构
- `test`：测试
- `chore`：构建/工具

### 11.3 接口规范

- **统一响应格式**：CommonResult
- **统一异常处理**：GlobalExceptionHandler
- **参数校验**：使用 @Valid、@Validated
- **接口文档**：OpenAPI 3.0 规范

### 11.4 设备基础数据模块开发规范

#### 11.4.1 数据访问规范

1. **租户隔离**：
   - 所有查询必须包含 `tenant_id` 条件
   - 使用 MyBatis Plus 的 `LambdaQueryWrapper` 构建查询条件
   - 禁止跨租户查询

2. **工厂数据隔离**：
   - 业务查询必须包含 `factory_id` 条件
   - 通过 `DeviceBaseDataApi` 接口自动验证工厂数据隔离
   - 禁止跨工厂查询

3. **软删除过滤**：
   - 默认查询只返回 `is_active = true` 的记录
   - 需要查询已删除数据时，显式指定 `is_active = false`

#### 11.4.2 事务管理规范

1. **事务范围**：
   - 创建/更新操作使用 `@Transactional`
   - 查询操作不使用事务（提升性能）
   - 批量操作使用事务保障一致性

2. **事务传播**：
   - 默认使用 `REQUIRED` 传播级别
   - 嵌套调用时，使用同一事务

3. **异常回滚**：
   - 使用 `rollbackFor = Exception.class`
   - 所有异常都回滚事务

#### 11.4.3 数据转换规范

1. **DO → VO 转换**：
   - 使用 Assembler 类进行转换
   - 每个实体都有对应的 Assembler
   - 转换逻辑统一在 Assembler 中

2. **冗余字段填充**：
   - 创建/更新时，自动填充冗余字段
   - 冗余字段填充逻辑统一在 Service 层
   - 避免在 Controller 层处理

3. **数据校验**：
   - 使用 Bean Validation 注解校验
   - 自定义校验逻辑在 Service 层实现
   - 校验失败抛出 `BusinessException`

#### 11.4.4 异常处理规范

1. **业务异常**：
   - 使用 `BusinessException` 抛出业务异常
   - 异常消息清晰明确，便于前端展示
   - 异常码统一管理

2. **数据不存在异常**：
   - 查询数据不存在时，抛出 `ResourceNotFoundException`
   - 统一异常处理，返回 404 状态码

3. **数据冲突异常**：
   - 唯一性冲突时，抛出 `DataConflictException`
   - 返回 409 状态码

#### 11.4.5 日志记录规范

1. **操作日志**：
   - 重要操作记录操作日志（创建、更新、删除）
   - 日志包含：操作人、操作时间、操作内容、操作结果

2. **错误日志**：
   - 异常发生时记录错误日志
   - 日志包含：异常堆栈、请求参数、用户信息

3. **性能日志**：
   - 慢查询记录性能日志
   - 日志包含：SQL语句、执行时间、参数信息

---

## 12. 附录

### 12.1 术语表

| 术语 | 说明 |
|------|------|
| **TB** | ThingsBoard，设备接入平台 |
| **Portal** | Weili-IoT-Portal，业务中台 |
| **OEE** | Overall Equipment Effectiveness，综合设备效率 |
| **Webhook** | HTTP 回调，用于事件通知 |
| **Tenant** | 租户，多租户隔离单位 |

### 12.2 参考文档

- [TB与Portal技术架构方案](./thingsboard/TB与Portal技术架构方案.md)
- [业务模块迁移方案](./thingsboard/业务模块迁移方案.md)
- [API接口汇总文档](./api-docs/API接口汇总文档.md)
- [OpenAPI接口文档](./api-docs/)

### 12.3 联系方式

- **项目负责人**：[待补充]
- **技术支持**：[待补充]
- **文档维护**：[待补充]

---

## 文档更新记录

| 版本 | 日期 | 作者 | 更新内容 |
|------|------|------|---------|
| v1.0 | 2025-11-21 | - |  |

---

**文档状态**：✅ 核心模块设计已完成

**已完成内容**：
1. ✅ 设备基础数据模块（device-base）完整设计
2. ✅ 设备管理模块（device-mgmt）详细设计
3. ✅ 报警管理模块（alarm-mgmt）详细设计
4. ✅ 效率管理模块（efficiency-mgmt）详细设计
5. ✅ 数字大屏模块（digital-screen）详细设计
6. ✅ 数据同步模块（data-sync）详细设计
7. ✅ 数据库表结构设计
8. ✅ 核心功能实现思路
9. ✅ 数据同步方案详细设计
10. ✅ 性能优化建议
11. ✅ 开发规范说明

**待完善内容**：
1. 补充部署运维详细配置
2. 补充监控告警详细规则
3. 补充故障处理流程
4. 补充测试方案

