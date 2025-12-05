-- ============================================================
-- 威力 IoT Portal - MySQL 设备与设备管理模块建表脚本
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 设备组织单元表 device_org_relation
-- 用途：描述设备所在的组织单元，如厂区、车间、产线，使用 system_dict_data.value 作为类型标识
-- 示例：减速器厂区/机加工车间/A1产线
-- ============================================================
CREATE TABLE IF NOT EXISTS device_org_relation (
    id              BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    tenant_uuid     CHAR(36) NOT NULL COMMENT '租户UUID（关联 tenant.id）',
    unit_code       VARCHAR(100) NOT NULL COMMENT '组织单元编码（租户内唯一）',
    unit_name       VARCHAR(255) NOT NULL COMMENT '组织单元名称',
    unit_type_value VARCHAR(100) NOT NULL COMMENT '组织单元类型值（system_dict_data.value，FACTORY/WORKSHOP/PRODUCTION_LINE）',
    org_parent_id   BIGINT DEFAULT NULL COMMENT '父级组织ID（关联 device_org_relation.id）',
    level_no        INT NOT NULL COMMENT '层级：1厂区、2车间、3产线',
    path            VARCHAR(500) DEFAULT NULL COMMENT '层级路径（物化路径模式）：使用 unit_code 组合，以 / 分隔，从根节点到当前节点。例：/FACTORY_A/WORKSHOP_A1/LINE_A1_01。用于快速查询所有子节点（LIKE path%）和所有祖先节点',
    description     TEXT COMMENT '描述信息',
    is_active       TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    sort_order      INT DEFAULT 0 COMMENT '排序号（越小越靠前）',
    create_time datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    deleted     tinyint(1)   default 0                 not null comment '是否删除',
    updater           bigint                                null comment '更新人ID',
    creator           bigint                                null comment '创建人ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='设备组织单元表（厂区/车间/产线）';

CREATE UNIQUE INDEX uk_device_org_relation_code ON device_org_relation (unit_code);
CREATE INDEX idx_device_org_relation_parent ON device_org_relation (org_parent_id);
CREATE INDEX idx_device_org_relation_type ON device_org_relation (unit_type_value);
CREATE INDEX idx_device_org_relation_path ON device_org_relation (path(255));

-- ============================================================
-- 2. 设备类型配置表 device_type_relation
-- 用途：描述设备类型，如机床、机器人、PLC，使用 system_dict_data.value 作为类型标识
-- 示例：机床/五轴铣车中心/CNC_5AXIS
-- ============================================================
CREATE TABLE IF NOT EXISTS device_type_relation (
    id              BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    tenant_uuid     CHAR(36) NOT NULL COMMENT '租户UUID（关联 tenant.id）',
    type_code       VARCHAR(100) NOT NULL COMMENT '设备类型编码（字典 value）',
    type_dict_value VARCHAR(100) NOT NULL COMMENT '设备类型字典值（system_dict_data.value）',
    parent_type_id  BIGINT DEFAULT NULL COMMENT '父类型ID（关联 device_type_relation.id）',
    parent_type_code VARCHAR(100) DEFAULT NULL COMMENT '父类型编码',
    parent_dict_value  VARCHAR(100) DEFAULT NULL COMMENT '父类型字典值（system_dict_data.value）',
    level_no        INT NOT NULL COMMENT '层级：1主类型、2子类型',
    path            VARCHAR(500) COMMENT '类型路径（物化路径模式）：使用 type_code 组合，以 / 分隔，从根类型到当前类型。例：/MACHINE_TOOL/CNC_5AXIS。用于快速查询所有子类型（LIKE path%）',
    category        VARCHAR(100) COMMENT '业务分类：机床/机器人/PLC等',
    description     TEXT COMMENT '类型描述',
    custom_fields   JSON COMMENT '自定义字段定义（JSON）',
    is_active       TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    sort_order      INT DEFAULT 0 COMMENT '排序号',
    create_time     datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    deleted     tinyint(1)   default 0                 not null comment '是否删除',
    updater           bigint                                null comment '更新人ID',
    creator           bigint                                null comment '创建人ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='设备类型配置表：类型基础信息存储在数据字典';

CREATE UNIQUE INDEX uk_device_type_cfg_code ON device_type_relation (type_code);
CREATE INDEX idx_device_type_cfg_parent_id ON device_type_relation (parent_type_id);
CREATE INDEX idx_device_type_cfg_parent_code ON device_type_relation (parent_type_code);
CREATE INDEX idx_device_type_cfg_category ON device_type_relation (category);

-- ============================================================
-- 3. 设备型号表 device_model
-- 用途：描述设备型号（该型号是设备厂商提供的型号，如NL1250H，制造商为纽威数控装备（苏州）股份有限公司）
-- 主要用于描述设备固有属性，如型号、制造商、规格参数等
-- ============================================================
CREATE TABLE IF NOT EXISTS device_model (
    id                  BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    tenant_uuid         CHAR(36) NOT NULL COMMENT '租户UUID（关联 tenant.id）',
    model_code          VARCHAR(100) NOT NULL COMMENT '型号编码（租户内唯一）',
    model_name          VARCHAR(255) NOT NULL COMMENT '型号名称',
    device_type_code    VARCHAR(100) NOT NULL COMMENT '设备类型编码（关联 device_type_relation.type_code）',
    manufacturer        VARCHAR(255) COMMENT '制造商',
    specifications      JSON COMMENT '通用规格参数（JSON）：尺寸、重量、功率等',
    type_specific_attrs JSON COMMENT '类型特定属性（JSON）：机床/PLC/机器人等不同类型有不同结构',
    is_active           TINYINT(1) DEFAULT 1 COMMENT '是否启用：1-启用 0-停用',
    create_time datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    deleted     tinyint(1)   default 0                 not null comment '是否删除',
    updater           bigint                                null comment '更新人ID',
    creator           bigint                                null comment '创建人ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='设备型号表：管理设备型号与规格';

CREATE UNIQUE INDEX uk_device_model_code ON device_model (model_code);
CREATE INDEX idx_device_model_type_code ON device_model (device_type_code);
CREATE INDEX idx_device_model_mfr ON device_model (manufacturer);

-- ============================================================
-- 4. 设备信息表 device_info
-- 用途：描述设备信息，将前面设备组织单元表、设备类型配置表、设备型号表关联起来，形成一个完整的设备信息
-- 包含设备编号（威力编号），可供业务逻辑使用，与 ThingsBoard 设备实例关联
-- 把关联表的名称删除
-- 把网络，位置的当前信息是否有必要冗余到本表中吗*******************
-- ============================================================
CREATE TABLE IF NOT EXISTS device_info (
    id                  BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    tenant_uuid         CHAR(36) NOT NULL COMMENT '租户UUID（关联 tenant.id）',
    tb_device_id        CHAR(36) NOT NULL COMMENT 'ThingsBoard 设备ID（关联 ThingsBoard device.id）',
    device_code         VARCHAR(100) NOT NULL COMMENT '设备编号（威力编号，租户内唯一）',
    device_name         VARCHAR(255) NOT NULL COMMENT '设备名称',
    device_type_code    VARCHAR(100) NOT NULL COMMENT '设备类型编码（关联 device_type_relation.type_code）',
    device_model_id     BIGINT NOT NULL COMMENT '设备型号ID（关联 device_model.id）',
    device_type_name    VARCHAR(255) COMMENT '设备类型名称（冗余字段，优化查询）',
    device_sub_type_name VARCHAR(255) COMMENT '设备子类型名称（冗余字段，优化查询）',
    model_name          VARCHAR(255) COMMENT '型号名称（冗余字段，优化查询）',
    manufacturer        VARCHAR(255) COMMENT '制造商（冗余字段，优化查询）',
    org_factory_id      BIGINT COMMENT '所属厂区ID（关联 device_org_relation.id）',
    org_workshop_id     BIGINT COMMENT '所属车间ID（关联 device_org_relation.id）',
    org_production_line_id BIGINT COMMENT '所属产线ID（关联 device_org_relation.id）',
    factory_name        VARCHAR(255) COMMENT '厂区名称（冗余字段，优化查询）',
    workshop_name       VARCHAR(255) COMMENT '车间名称（冗余字段，优化查询）',
    production_line_name VARCHAR(255) COMMENT '产线名称（冗余字段，优化查询）',
    device_status       VARCHAR(50) DEFAULT 'INACTIVE' COMMENT '设备状态：ACTIVE-在用 INACTIVE-停用 MAINTENANCE-维护中 RETIRED-报废',
    is_monitored        TINYINT(1) DEFAULT 1 COMMENT '是否监控：1-监控 0-不监控',
    extra_properties    JSON COMMENT '扩展属性（JSON）：采购信息、资产编号、序列号等',
    remarks             TEXT COMMENT '备注信息',
    create_time datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    deleted     tinyint(1)   default 0                 not null comment '是否删除',
    updater           bigint                                null comment '更新人ID',
    creator           bigint                                null comment '创建人ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='设备信息表：关联 ThingsBoard 设备实例';

CREATE UNIQUE INDEX uk_device_info_tb_device ON device_info (tb_device_id);
CREATE UNIQUE INDEX uk_device_info_code ON device_info (device_code);
CREATE INDEX idx_device_info_type_code ON device_info (device_type_code);
CREATE INDEX idx_device_info_model ON device_info (device_model_id);
CREATE INDEX idx_device_info_factory ON device_info (org_factory_id);
CREATE INDEX idx_device_info_workshop ON device_info (org_workshop_id);
CREATE INDEX idx_device_info_line ON device_info (org_production_line_id);
CREATE INDEX idx_device_info_monitored ON device_info (is_monitored);

-- ============================================================
-- 5. 设备网络配置表 device_network_config
-- 说明：
--   1. 用于设备通信连接的网络配置（ip_address, port, protocol等）
--   2. 支持历史版本：记录网络配置的变更历史，通过effective_start_ts和effective_end_ts管理
--   3. 与device_info为1:N关系（一个设备可以有多个历史配置，但只有一个当前生效的配置）
--   4. 通过is_active字段标识当前生效的配置
-- ============================================================
CREATE TABLE IF NOT EXISTS device_network_config (
    id                  BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    tenant_uuid         CHAR(36) NOT NULL COMMENT '租户UUID（关联 tenant.id）',
    device_info_id      BIGINT NOT NULL COMMENT '设备ID（关联 device_info.id）',
    
    -- ========== 网络配置字段 ==========
    ip_address          VARCHAR(50) COMMENT 'IP地址',
    port                INT COMMENT '端口号',
    mac_address         VARCHAR(50) COMMENT 'MAC地址',
    gateway             VARCHAR(50) COMMENT '网关地址',
    subnet_mask         VARCHAR(50) COMMENT '子网掩码',
    protocol            VARCHAR(50) COMMENT '通信协议：MQTT、MODBUS、OPC_UA、FOCAS等',
    connection_params   JSON COMMENT '连接参数（JSON）：超时、重试、轮询间隔等',
    
    -- ========== 历史版本管理 ==========
    effective_start_ts  BIGINT NOT NULL COMMENT '生效开始时间戳（秒，Unix时间戳，用于历史修订）',
    effective_end_ts    BIGINT COMMENT '生效结束时间戳（秒，Unix时间戳，NULL表示当前生效）',
    is_active           TINYINT(1) DEFAULT 1 COMMENT '是否当前生效：1-当前生效 0-历史版本',
    description         TEXT COMMENT '配置说明（如：IP地址变更、网络调整等）',
    
    -- 审计字段
    create_time datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    deleted     tinyint(1)   default 0                 not null comment '是否删除',
    updater           bigint                                null comment '更新人ID',
    creator           bigint                                null comment '创建人ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='设备网络配置表：支持历史版本，记录网络配置变更历史';

CREATE INDEX idx_network_config_device ON device_network_config (device_info_id);
CREATE INDEX idx_network_config_active ON device_network_config (device_info_id, is_active);
CREATE INDEX idx_network_config_effective ON device_network_config (device_info_id, effective_start_ts);

-- ============================================================
-- 6. 设备位置信息表 device_location
-- 说明：
--   1. 设备物理位置信息（location_code, coordinates等），区别于device_info中的逻辑位置（组织结构）
--   2. 支持历史版本：记录设备位置变更历史（如设备搬迁），通过effective_start_ts和effective_end_ts管理
--   3. 逻辑位置（org_factory_id/org_workshop_id/org_production_line_id）在device_info表中，用于业务管理
--   4. 物理位置（location_code/coordinates）在本表中，用于资产定位和地图展示
--   5. 与device_info为1:N关系（一个设备可以有多个历史位置，但只有一个当前生效的位置）
-- ============================================================
CREATE TABLE IF NOT EXISTS device_location (
    id                  BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    tenant_uuid         CHAR(36) NOT NULL COMMENT '租户UUID（关联 tenant.id）',
    device_info_id      BIGINT NOT NULL COMMENT '设备ID（关联 device_info.id）',
    
    -- ========== 位置信息字段 ==========
    location_code       VARCHAR(100) COMMENT '位置编码（物理位置编码，如：A区-1层-01号位，区别于device_info中的逻辑位置）',
    location_description VARCHAR(500) COMMENT '位置描述',
    coordinates         JSON COMMENT '坐标信息（JSON）：经纬度、楼层、区域、位置编号等',
    
    -- ========== 冗余字段（从coordinates提取，便于查询和索引）==========
    floor_no            INT COMMENT '楼层号（从coordinates提取）',
    area_code           VARCHAR(50) COMMENT '区域编码（从coordinates提取）',
    longitude           DECIMAL(10,7) COMMENT '经度（从coordinates提取）',
    latitude            DECIMAL(10,7) COMMENT '纬度（从coordinates提取）',
    
    -- ========== 历史版本管理 ==========
    effective_start_ts  BIGINT NOT NULL COMMENT '生效开始时间戳（秒，Unix时间戳，用于历史修订）',
    effective_end_ts    BIGINT COMMENT '生效结束时间戳（秒，Unix时间戳，NULL表示当前生效）',
    is_active           TINYINT(1) DEFAULT 1 COMMENT '是否当前生效：1-当前生效 0-历史版本',
    description         TEXT COMMENT '位置说明（如：设备搬迁、位置调整等）',
    
    -- 审计字段
    create_time datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    deleted     tinyint(1)   default 0                 not null comment '是否删除',
    updater           bigint                                null comment '更新人ID',
    creator           bigint                                null comment '创建人ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='设备位置信息表：支持历史版本，记录设备位置变更历史。逻辑位置（组织结构）在device_info表中';

CREATE INDEX idx_location_device ON device_location (device_info_id);
CREATE INDEX idx_location_active ON device_location (device_info_id, is_active);
CREATE INDEX idx_location_effective ON device_location (device_info_id, effective_start_ts);
CREATE INDEX idx_location_coords ON device_location (longitude, latitude);

-- ============================================================
-- 7. 设备关系表 device_relation
-- 
-- ============================================================
-- CREATE TABLE IF NOT EXISTS device_relation (
--     id              BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
--     tenant_id       CHAR(36) NOT NULL COMMENT '租户ID',
--     from_device_id  BIGINT NOT NULL COMMENT '源设备ID（外键关联 device_info.id）',
--     to_device_id    BIGINT NOT NULL COMMENT '目标设备ID（外键关联 device_info.id）',
--     relation_type   VARCHAR(100) NOT NULL COMMENT '关系类型：GROUP-设备组 UPSTREAM-上游 DOWNSTREAM-下游 BACKUP-备用 MASTER_SLAVE-主从',
--     relation_name   VARCHAR(255) COMMENT '关系名称',
--     description     TEXT COMMENT '关系描述',
--     properties      JSON COMMENT '关系属性（JSON）：根据关系类型存储不同属性',
--     is_active       TINYINT(1) DEFAULT 1 COMMENT '是否启用：1-启用 0-停用',
--     create_time datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
--     update_time datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
--     deleted     tinyint(1)   default 0                 not null comment '是否删除',
--     updater           bigint                                null comment '更新人ID',
--     creator           bigint                                null comment '创建人ID',
--     CONSTRAINT fk_device_rel_from FOREIGN KEY (from_device_id) REFERENCES device_info(id) ON DELETE CASCADE,
--     CONSTRAINT fk_device_rel_to FOREIGN KEY (to_device_id) REFERENCES device_info(id) ON DELETE CASCADE,
--     CONSTRAINT fk_device_rel_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE,
--     CONSTRAINT chk_device_rel_different CHECK (from_device_id != to_device_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
--   COMMENT='设备关系表：设备组、上下游、主从等关系';

-- CREATE INDEX idx_device_rel_from ON device_relation (from_device_id);
-- CREATE INDEX idx_device_rel_to ON device_relation (to_device_id);
-- CREATE INDEX idx_device_rel_type ON device_relation (relation_type);
-- CREATE INDEX idx_device_rel_active ON device_relation (is_active);

-- ============================================================
-- 8. 设备状态明细表 device_state_record
--  state_code、shift_code放到字典中
-- ============================================================
CREATE TABLE IF NOT EXISTS device_state_record (
    id              BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    tenant_uuid     CHAR(36) NOT NULL COMMENT '租户UUID（关联 tenant.id）',
    device_info_id  BIGINT NOT NULL COMMENT '设备ID（关联 device_info.id）',
    state_code      VARCHAR(50) NOT NULL COMMENT '设备状态编码：WORKING-加工中 STANDBY-待机 FAULT-故障 SHUTDOWN-关机',
    start_ts        BIGINT NOT NULL COMMENT '状态开始时间戳（秒，Unix时间戳）',
    end_ts          BIGINT DEFAULT NULL COMMENT '状态结束时间戳（秒，Unix时间戳，NULL表示进行中）',
    duration_s      INT COMMENT '持续时长（秒）',
    shift_date      DATE COMMENT '所属班次日期',
    shift_code      VARCHAR(50) COMMENT '班次编码：SHIFT_1-一班 SHIFT_2-二班 SHIFT_3-三班',
    properties      JSON COMMENT '扩展属性（JSON）：故障代码、工件号等',
    is_complete     TINYINT(1) DEFAULT 1 COMMENT '是否完整片段：1-完整 0-跨班切分或数据缺失'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='设备状态明细记录表：用于状态时间线与甘特图';

CREATE INDEX idx_state_record_device ON device_state_record (device_info_id, start_ts);
CREATE INDEX idx_state_record_shift ON device_state_record (device_info_id, shift_date, shift_code);
CREATE INDEX idx_state_record_open ON device_state_record (device_info_id, end_ts);

-- ============================================================
-- 9. 设备状态汇总表 device_state_summary
-- ============================================================
CREATE TABLE IF NOT EXISTS device_state_summary (
    id                  BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    tenant_uuid         CHAR(36) NOT NULL COMMENT '租户UUID（关联 tenant.id）',
    device_info_id      BIGINT NOT NULL COMMENT '设备ID（关联 device_info.id）',
    summary_date        DATE NOT NULL COMMENT '汇总日期',
    shift_code          VARCHAR(50) NOT NULL COMMENT '班次编码：SHIFT_1-一班 SHIFT_2-二班 SHIFT_3-三班',
    shift_start_ts      BIGINT NOT NULL COMMENT '班次开始时间戳（秒，Unix时间戳）',
    shift_end_ts        BIGINT NOT NULL COMMENT '班次结束时间戳（秒，Unix时间戳）',
    state_statistics    JSON NOT NULL COMMENT '状态统计详情（JSON）：每个状态的时长、占比、片段数等',
    working_duration_s INT COMMENT '加工中时长（秒，冗余字段）',
    standby_duration_s INT COMMENT '待机时长（秒，冗余字段）',
    fault_duration_s   INT COMMENT '故障时长（秒，冗余字段）',
    shutdown_duration_s INT COMMENT '关机时长（秒，冗余字段）',
    working_ratio       DECIMAL(5,4) COMMENT '加工中占比（冗余字段）',
    standby_ratio       DECIMAL(5,4) COMMENT '待机占比（冗余字段）',
    fault_ratio         DECIMAL(5,4) COMMENT '故障占比（冗余字段）',
    shutdown_ratio      DECIMAL(5,4) COMMENT '关机占比（冗余字段）',
    is_finalized        TINYINT(1) DEFAULT 0 COMMENT '是否已最终确定：1-已确定 0-待确定（班次结束后为1）',
    calculated_time     BIGINT NOT NULL COMMENT '计算时间戳（秒，Unix时间戳）',
    calculation_source  VARCHAR(50) COMMENT '计算来源：SCHEDULED-定时任务 MANUAL-手动触发',
    data_completeness   DECIMAL(5,4) COMMENT '数据完整度（有效数据时长/班次总时长）',
    missing_data_s      INT COMMENT '缺失数据时长（秒）',
    CONSTRAINT uq_state_summary_shift UNIQUE (device_info_id, summary_date, shift_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='设备状态汇总表：按班次存储状态时长与占比';

CREATE INDEX idx_state_summary_device ON device_state_summary (device_info_id, summary_date);
CREATE INDEX idx_state_summary_time_range ON device_state_summary (device_info_id, shift_start_ts, shift_end_ts);
CREATE INDEX idx_state_summary_finalized_device ON device_state_summary (device_info_id, is_finalized, calculated_time DESC);

-- ============================================================
-- 10. 设备刀具使用记录表 device_tool_record
-- ============================================================
CREATE TABLE IF NOT EXISTS device_tool_record (
    id              BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    tenant_uuid     CHAR(36) NOT NULL COMMENT '租户UUID（关联 tenant.id）',
    device_info_id  BIGINT NOT NULL COMMENT '设备ID（关联 device_info.id）',
    tool_id         VARCHAR(100) COMMENT '刀具编号（刀具唯一标识，用于追踪刀具生命周期）',
    tool_no         VARCHAR(50) NOT NULL COMMENT '刀号（刀具在刀库中的位置号，如T01、T02等）',
    tool_magazine_no VARCHAR(50) COMMENT '刀套号',
    tool_type       VARCHAR(100) COMMENT '刀具类型：铣刀、钻头、镗刀等',
    start_ts        BIGINT NOT NULL COMMENT '开始使用时间戳（秒，Unix时间戳）',
    end_ts          BIGINT COMMENT '结束使用时间戳（秒，Unix时间戳，NULL表示使用中）',
    duration_s      INT COMMENT '使用时长（秒）',
    workpiece_no    VARCHAR(255) COMMENT '关联工件号',
    program_name    VARCHAR(255) COMMENT '关联程序名',
    compensation_snapshot JSON COMMENT '使用时的刀具补偿值快照（JSON）',
    shift_date      DATE COMMENT '所属班次日期',
    shift_code      VARCHAR(50) COMMENT '班次编码'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='刀具使用记录表';

CREATE INDEX idx_tool_record_device ON device_tool_record (device_info_id, start_ts);
CREATE INDEX idx_tool_record_tool_no ON device_tool_record (device_info_id, tool_no);
CREATE INDEX idx_tool_record_tool_id ON device_tool_record (tool_id, start_ts);

-- ============================================================
-- 11. 设备报警历史表 device_alarm_history
-- ============================================================
CREATE TABLE IF NOT EXISTS device_alarm_history (
    id              BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    tenant_uuid     CHAR(36) NOT NULL COMMENT '租户UUID（关联 tenant.id）',
    device_info_id  BIGINT NOT NULL COMMENT '设备ID（关联 device_info.id）',
    alarm_code      VARCHAR(100) NOT NULL COMMENT '报警编号',
    alarm_text      TEXT COMMENT '报警内容',
    alarm_level     VARCHAR(50) COMMENT '报警级别：INFO-信息 WARNING-警告 ERROR-错误 CRITICAL-严重',
    start_ts        BIGINT NOT NULL COMMENT '报警开始时间戳（秒，Unix时间戳）',
    end_ts          BIGINT COMMENT '报警结束时间戳（秒，Unix时间戳，NULL表示报警中）',
    duration_s      INT COMMENT '持续时长（秒）',
    is_active       TINYINT(1) DEFAULT 1 COMMENT '是否报警中：1-报警中 0-已解除',
    start_shift_date DATE COMMENT '报警开始班次日期',
    start_shift_code VARCHAR(50) COMMENT '报警开始班次编码',
    end_shift_date   DATE COMMENT '报警结束班次日期（NULL表示报警中）',
    end_shift_code   VARCHAR(50) COMMENT '报警结束班次编码（NULL表示报警中）',
    properties      JSON COMMENT '扩展属性（JSON）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='设备报警历史记录表';

CREATE INDEX idx_alarm_history_device ON device_alarm_history (device_info_id, start_ts);
CREATE INDEX idx_alarm_history_code ON device_alarm_history (device_info_id, alarm_code);
CREATE INDEX idx_alarm_history_active ON device_alarm_history (device_info_id, is_active);

-- ============================================================
-- 12. 设备产量明细表 device_production_record
-- ============================================================
CREATE TABLE IF NOT EXISTS device_production_record (
    id              BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    tenant_uuid     CHAR(36) NOT NULL COMMENT '租户UUID（关联 tenant.id）',
    device_info_id  BIGINT NOT NULL COMMENT '设备ID（关联 device_info.id）',
    start_ts        BIGINT COMMENT '开始加工时间戳（秒，Unix时间戳，可选）',
    end_ts          BIGINT NOT NULL COMMENT '生产完成时间戳（秒，Unix时间戳，用于判定归属班次）',
    duration_s      INT COMMENT '加工周期时长（秒，从开始加工到完成的时间）',
    workpiece_no    VARCHAR(255) COMMENT '工件唯一编码（当前NULL，对接MES后填充）',
    workpiece_type  VARCHAR(100) COMMENT '工件类型/型号（可选）',
    batch_no        VARCHAR(100) COMMENT '批次号（可选，对接MES后可追溯批次）',
    program_name    VARCHAR(255) COMMENT '加工程序名（可选）',
    shift_date      DATE NOT NULL COMMENT '归属班次日期（根据完成时间自动判定）',
    shift_code      VARCHAR(50) NOT NULL COMMENT '归属班次编码（根据完成时间自动判定）',
    quality_status  VARCHAR(50) COMMENT '质量状态：QUALIFIED-合格 DEFECT-不合格 UNKNOWN-未知',
    count_source    VARCHAR(50) COMMENT '计数来源：DOOR_SIGNAL-关门信号 CYCLE_SIGNAL-循环信号 MANUAL-手动',
    properties      JSON COMMENT '扩展属性（JSON）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='设备产量明细记录表';

CREATE INDEX idx_production_record_device ON device_production_record (device_info_id, end_ts);
CREATE INDEX idx_production_record_shift ON device_production_record (device_info_id, shift_date, shift_code);

-- ============================================================
-- 13. 设备产量汇总表 device_production_summary
-- ============================================================
CREATE TABLE IF NOT EXISTS device_production_summary (
    id              BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    tenant_uuid     CHAR(36) NOT NULL COMMENT '租户UUID（关联 tenant.id）',
    device_info_id  BIGINT NOT NULL COMMENT '设备ID（关联 device_info.id）',
    shift_date      DATE NOT NULL COMMENT '班次日期',
    shift_code      VARCHAR(50) NOT NULL COMMENT '班次编码：SHIFT_1-一班 SHIFT_2-二班 SHIFT_3-三班',
    shift_start_ts  BIGINT NOT NULL COMMENT '班次开始时间戳（秒，Unix时间戳）',
    shift_end_ts    BIGINT NOT NULL COMMENT '班次结束时间戳（秒，Unix时间戳）',
    part_count      INT DEFAULT 0 COMMENT '加工数量',
    qualified_count INT DEFAULT 0 COMMENT '合格数量（暂无质量数据时默认等于part_count）',
    defect_count    INT DEFAULT 0 COMMENT '不合格数量',
    count_method    VARCHAR(50) COMMENT '计数方式：DOOR_SIGNAL-关门信号 CYCLE_SIGNAL-循环信号 MANUAL-手动',
    properties      JSON COMMENT '扩展属性（JSON）',
    is_finalized    TINYINT(1) DEFAULT 0 COMMENT '是否已最终确定：1-已确定 0-待确定（班次结束后由定时任务确定）',
    calculated_time BIGINT COMMENT '计算时间戳（秒，Unix时间戳）',
    CONSTRAINT uq_production_summary_shift UNIQUE (device_info_id, shift_date, shift_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='设备产量汇总表（按班次）';

CREATE INDEX idx_production_summary_device ON device_production_summary (device_info_id, shift_date);
CREATE INDEX idx_production_summary_time_range ON device_production_summary (device_info_id, shift_start_ts, shift_end_ts);

-- ============================================================
-- 14. 设备参数配置表 device_param_config
-- ============================================================
CREATE TABLE IF NOT EXISTS device_param_config (
    id                  BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    tenant_uuid         CHAR(36) NOT NULL COMMENT '租户UUID（关联 tenant.id）',
    device_info_id      BIGINT NOT NULL COMMENT '设备ID（关联 device_info.id）',
    parameter_type      VARCHAR(100) NOT NULL COMMENT '参数类型：THEORETICAL_CYCLE-理论节拍 PLANNED_DOWNTIME-计划停机时间等',
    parameter_value     DECIMAL(10,4) COMMENT '参数值（数值型）',
    parameter_unit      VARCHAR(50) COMMENT '单位：HOUR-小时 MINUTE-分钟 SECOND-秒 PIECE-件等',
    parameter_text      VARCHAR(500) COMMENT '参数值（文本型）',
    effective_start_ts  BIGINT NOT NULL COMMENT '生效开始时间戳（秒，Unix时间戳，用于历史修订）',
    effective_end_ts    BIGINT COMMENT '生效结束时间戳（秒，Unix时间戳，NULL表示当前生效）',
    description         TEXT COMMENT '配置说明',
    is_active           TINYINT(1) DEFAULT 1 COMMENT '是否启用：1-启用 0-停用',
    create_time datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    deleted     tinyint(1)   default 0                 not null comment '是否删除',
    updater           bigint                                null comment '更新人ID',
    creator           bigint                                null comment '创建人ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='设备参数配置表（支持历史修订）';

CREATE INDEX idx_device_param_config_device ON device_param_config (device_info_id, parameter_type);
CREATE INDEX idx_device_param_config_effective ON device_param_config (device_info_id, effective_start_ts);

-- ============================================================
-- 15. 设备程序状态表 device_program_status （机床程序先不存储）
-- ============================================================
-- CREATE TABLE IF NOT EXISTS device_program_status (
--     id                  BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
--     tenant_id           CHAR(36) NOT NULL COMMENT '租户ID',
--     device_id           BIGINT NOT NULL COMMENT '设备ID（外键关联 device_info.id）',
--     program_name        VARCHAR(255) NOT NULL COMMENT '程序名称（如O0001、PART001.NC）',
--     program_path        VARCHAR(500) COMMENT '程序路径（如/CNC_MEM/PART/O0001.NC）',
--     program_size_bytes  BIGINT COMMENT '程序大小（字节）',
--     program_hash        VARCHAR(64) COMMENT '程序哈希值（用于判断是否变化）',
--     program_content     LONGTEXT COMMENT '完整G代码内容（可选，较大时存文件）',
--     program_content_url VARCHAR(500) COMMENT '程序文件URL（存储到对象存储）',
--     code_statistics     JSON COMMENT '代码统计（G代码/M代码分析，JSON）',
--     first_used_ts       BIGINT COMMENT '首次使用时间戳（秒，Unix时间戳）',
--     last_used_ts        BIGINT COMMENT '最后使用时间戳（秒，Unix时间戳）',
--     usage_count         INT DEFAULT 0 COMMENT '使用次数',
--     device_type_category VARCHAR(50) COMMENT '设备类型分类：CNC_MACHINE-数控机床 SENSOR-传感器 ROBOT-机器人等（用于判断是否需要程序信息）',
--     CONSTRAINT fk_program_status_device FOREIGN KEY (device_id) REFERENCES device_info(id) ON DELETE CASCADE,
--     CONSTRAINT fk_program_status_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE,
--     CONSTRAINT uq_program_status_device_program UNIQUE (device_id, program_name)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
--   COMMENT='设备程序状态表：记录机床程序信息';

-- CREATE INDEX idx_program_status_device ON device_program_status (device_id, last_used_ts);
-- CREATE INDEX idx_program_status_type ON device_program_status (device_type_category);

-- ============================================================
-- 16. 设备指标汇总表 device_metrics_summary
-- ============================================================
CREATE TABLE IF NOT EXISTS device_metrics_summary (
    id                  BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    tenant_uuid         CHAR(36) NOT NULL COMMENT '租户UUID（关联 tenant.id）',
    device_info_id      BIGINT NOT NULL COMMENT '设备ID（关联 device_info.id）',
    shift_date          DATE NOT NULL COMMENT '班次日期',
    shift_code          VARCHAR(50) NOT NULL COMMENT '班次编码：SHIFT_1-一班 SHIFT_2-二班 SHIFT_3-三班',
    shift_start_ts      BIGINT NOT NULL COMMENT '班次开始时间戳（秒，Unix时间戳）',
    shift_end_ts        BIGINT NOT NULL COMMENT '班次结束时间戳（秒，Unix时间戳）',
    
    -- ========== 核心指标（独立字段，用于查询和索引）==========
    oee                 DECIMAL(5,4) COMMENT 'OEE（整体设备效率）',
    availability        DECIMAL(5,4) COMMENT '可用率',
    performance         DECIMAL(5,4) COMMENT '性能率',
    quality             DECIMAL(5,4) COMMENT '质量率',
    utilization_rate    DECIMAL(5,4) COMMENT '设备利用率',
    working_hours        DECIMAL(10,2) COMMENT '加工时长（小时）',
    planned_downtime_s  INT COMMENT '计划停机时长（秒）',
    unplanned_downtime_s INT COMMENT '非计划停机时长（秒）',
    theoretical_cycle_s  INT COMMENT '理论节拍（秒）',
    actual_cycle_s       DECIMAL(10,2) COMMENT '实际节拍（秒）',
    production_count     INT COMMENT '加工数量',
    qualified_count      INT COMMENT '合格数量',
    
    -- ========== JSON 字段（用于扩展指标和完整数据）==========
    metrics             JSON COMMENT '完整指标数据（JSON）：包含核心指标和扩展指标，用于存储完整数据和动态扩展',
    calculation_data    JSON NOT NULL COMMENT '计算依据的原始数据（JSON）：用于审计和重算',
    parameter_snapshot  JSON COMMENT '参数快照（JSON）：用于追溯指标计算时使用的参数版本',
    
    is_finalized        TINYINT(1) DEFAULT 0 COMMENT '是否已最终确定：1-已确定 0-待确定（班次结束后为1）',
    calculation_status  VARCHAR(50) COMMENT '计算状态：PENDING-待计算 CALCULATED-已计算 RECALCULATED-已重算 FAILED-计算失败',
    calculated_time     BIGINT COMMENT '计算时间戳（秒，Unix时间戳）',
    calculation_source  VARCHAR(50) COMMENT '计算来源：SCHEDULED-定时任务 MANUAL-手动触发 RECALC-重算',
    data_completeness   DECIMAL(5,4) COMMENT '数据完整度（有效数据时长/班次总时长）',
    recalculated_at     BIGINT COMMENT '重算时间戳（秒，Unix时间戳，NULL表示未重算）',
    recalculation_reason TEXT COMMENT '重算原因（如参数修订、数据补全等）',
    recalculation_count INT DEFAULT 0 COMMENT '重算次数',
    CONSTRAINT uq_metrics_summary UNIQUE (device_info_id, shift_date, shift_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='设备指标汇总表（按班次）：OEE、开动率等核心指标使用独立字段，扩展指标存储在JSON中';

CREATE INDEX idx_metrics_summary_device ON device_metrics_summary (device_info_id, shift_date);
CREATE INDEX idx_metrics_summary_time_range ON device_metrics_summary (device_info_id, shift_start_ts, shift_end_ts);
CREATE INDEX idx_metrics_summary_finalized_device ON device_metrics_summary (device_info_id, is_finalized, calculated_time DESC);
CREATE INDEX idx_metrics_summary_oee ON device_metrics_summary (device_info_id, oee DESC);
CREATE INDEX idx_metrics_summary_utilization ON device_metrics_summary (device_info_id, utilization_rate DESC);
CREATE INDEX idx_metrics_summary_production ON device_metrics_summary (device_info_id, production_count DESC);

-- ============================================================
-- 17. 设备班次配置表 device_shift_config
-- ============================================================
CREATE TABLE IF NOT EXISTS device_shift_config (
    id              BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    tenant_uuid     CHAR(36) NOT NULL COMMENT '租户UUID（关联 tenant.id）',
    device_info_id  BIGINT NOT NULL COMMENT '设备ID（关联 device_info.id）',
    shift_mode      INT NOT NULL DEFAULT 2 COMMENT '班次数量：2-2班制 3-3班制',
    
    -- 班次1（必填）
    shift_1_code        VARCHAR(50) NOT NULL COMMENT '班次1编码：SHIFT_1',
    shift_1_name        VARCHAR(100) NOT NULL COMMENT '班次1名称：一班/早班',
    shift_1_start_time  TIME NOT NULL COMMENT '班次1开始时间：08:00:00',
    shift_1_end_time    TIME NOT NULL COMMENT '班次1结束时间：16:00:00',
    shift_1_duration_s  INT COMMENT '班次1时长（秒）',
    
    -- 班次2（必填）
    shift_2_code        VARCHAR(50) NOT NULL COMMENT '班次2编码：SHIFT_2',
    shift_2_name        VARCHAR(100) NOT NULL COMMENT '班次2名称：二班/中班',
    shift_2_start_time  TIME NOT NULL COMMENT '班次2开始时间：16:00:00',
    shift_2_end_time    TIME NOT NULL COMMENT '班次2结束时间：00:00:00',
    shift_2_duration_s  INT COMMENT '班次2时长（秒）',
    
    -- 班次3（可选，仅3班制时使用）
    shift_3_code        VARCHAR(50) COMMENT '班次3编码：SHIFT_3（仅3班制时使用）',
    shift_3_name        VARCHAR(100) COMMENT '班次3名称：三班/晚班（仅3班制时使用）',
    shift_3_start_time  TIME COMMENT '班次3开始时间（仅3班制时使用）',
    shift_3_end_time    TIME COMMENT '班次3结束时间（仅3班制时使用）',
    shift_3_duration_s  INT COMMENT '班次3时长（秒，仅3班制时使用）',
    
    effective_start_ts BIGINT NOT NULL COMMENT '生效开始时间戳（秒，Unix时间戳）',
    effective_end_ts BIGINT COMMENT '生效结束时间戳（秒，Unix时间戳，NULL表示当前生效）',
    is_active       TINYINT(1) DEFAULT 1 COMMENT '是否启用：1-启用 0-停用',
    create_time datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    deleted     tinyint(1)   default 0                 not null comment '是否删除',
    updater           bigint                                null comment '更新人ID',
    creator           bigint                                null comment '创建人ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='设备班次配置表：存储设备级班次定义（支持2班制/3班制）';

CREATE INDEX idx_shift_config_device ON device_shift_config (device_info_id, effective_start_ts);
CREATE INDEX idx_shift_config_active_effective ON device_shift_config (device_info_id, is_active, effective_start_ts DESC, effective_end_ts);

-- ============================================================
-- 18. 工厂级指标汇总表 factory_metric_summary
-- ============================================================
CREATE TABLE IF NOT EXISTS factory_metric_summary (
    id                  BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    tenant_uuid         CHAR(36) NOT NULL COMMENT '租户UUID（关联 tenant.id）',
    org_factory_id      BIGINT NOT NULL COMMENT '工厂ID（关联 device_org_relation.id，unit_type=FACTORY）',
    shift_date          DATE NOT NULL COMMENT '班次日期',
    shift_code          VARCHAR(50) NOT NULL COMMENT '班次编码：SHIFT_1-一班 SHIFT_2-二班 SHIFT_3-三班',
    shift_start_ts      BIGINT NOT NULL COMMENT '班次开始时间戳（秒，Unix时间戳）',
    shift_end_ts        BIGINT NOT NULL COMMENT '班次结束时间戳（秒，Unix时间戳）',
    
    -- ========== 核心指标（独立字段，用于查询和索引）==========
    average_oee         DECIMAL(5,4) COMMENT '平均OEE（整体设备效率）',
    average_availability DECIMAL(5,4) COMMENT '平均可用率',
    average_performance  DECIMAL(5,4) COMMENT '平均性能率',
    average_quality      DECIMAL(5,4) COMMENT '平均质量率',
    average_utilization_rate DECIMAL(5,4) COMMENT '平均设备利用率',
    average_working_hours   DECIMAL(10,2) COMMENT '平均加工时长（小时）',
    total_production_count  INT COMMENT '总加工数量',
    total_qualified_count   INT COMMENT '总合格数量',
    total_planned_downtime_s INT COMMENT '总计划停机时长（秒）',
    total_unplanned_downtime_s INT COMMENT '总非计划停机时长（秒）',
    
    -- ========== JSON 字段（用于扩展指标和完整数据）==========
    metrics             JSON COMMENT '完整指标数据（JSON）：包含核心指标和扩展指标，用于存储完整数据和动态扩展',
    calculation_data    JSON NOT NULL COMMENT '计算依据的原始数据（JSON）：用于审计和重算',
    
    device_count        INT NOT NULL COMMENT '参与计算的设备数量',
    is_finalized        TINYINT(1) DEFAULT 0 COMMENT '是否已最终确定：1-已确定 0-待确定（班次结束后为1）',
    calculation_status  VARCHAR(50) COMMENT '计算状态：PENDING-待计算 CALCULATED-已计算 RECALCULATED-已重算 FAILED-计算失败',
    calculated_time     BIGINT COMMENT '计算时间戳（秒，Unix时间戳）',
    calculation_source  VARCHAR(50) COMMENT '计算来源：SCHEDULED-定时任务 MANUAL-手动触发 RECALC-重算',
    data_completeness   DECIMAL(5,4) COMMENT '数据完整度（有效数据设备数/总设备数）',
    recalculated_at     BIGINT COMMENT '重算时间戳（秒，Unix时间戳，NULL表示未重算）',
    recalculation_reason TEXT COMMENT '重算原因（如参数修订、数据补全等）',
    recalculation_count INT DEFAULT 0 COMMENT '重算次数',
    CONSTRAINT uq_factory_metric_shift UNIQUE (org_factory_id, shift_date, shift_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='工厂级指标汇总表（按班次）：平均OEE、平均设备利用率等核心指标使用独立字段，扩展指标存储在JSON中';

CREATE INDEX idx_factory_metric_factory ON factory_metric_summary (org_factory_id, shift_date);
CREATE INDEX idx_factory_metric_time_range ON factory_metric_summary (org_factory_id, shift_start_ts, shift_end_ts);
CREATE INDEX idx_factory_metric_finalized_factory ON factory_metric_summary (org_factory_id, is_finalized, calculated_time DESC);
CREATE INDEX idx_factory_metric_oee ON factory_metric_summary (org_factory_id, average_oee DESC);
CREATE INDEX idx_factory_metric_utilization ON factory_metric_summary (org_factory_id, average_utilization_rate DESC);
CREATE INDEX idx_factory_metric_production ON factory_metric_summary (org_factory_id, total_production_count DESC);

-- ============================================================
-- 19. Webhook 收件箱与失败日志
-- 说明：
--   1. webhook_inbox：业务Webhook收件箱，等待异步处理/重试
--   2. webhook_fail_log：处理失败记录，支持人工介入与恢复
-- ============================================================
CREATE TABLE IF NOT EXISTS webhook_inbox (
    id               BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    message_id       VARCHAR(100) NOT NULL COMMENT '消息唯一ID（幂等）',
    tenant_id        CHAR(36) NOT NULL COMMENT '租户UUID',
    device_id        CHAR(36) DEFAULT NULL COMMENT 'TB设备ID',
    device_code      VARCHAR(100) NOT NULL COMMENT '设备编号（必填）',
    event_type       VARCHAR(50) NOT NULL COMMENT '事件类型',
    webhook_category VARCHAR(20) NOT NULL COMMENT '分类：BUSINESS/REALTIME',
    payload          JSON NOT NULL COMMENT '事件载荷（eventData/telemetryData/metadata/transactionInfo）',
    status           VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSING/SUCCESS/FAILED',
    process_count    INT DEFAULT 0 COMMENT '处理次数',
    next_retry_time  datetime DEFAULT NULL COMMENT '下一次重试时间',
    last_error       TEXT COMMENT '最后一次错误信息',
    received_time    datetime DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '接收时间',
    processed_time   datetime DEFAULT NULL COMMENT '处理完成时间',
    created_time     datetime DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time     datetime DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Webhook 收件箱表：业务数据入箱等待异步处理';

CREATE UNIQUE INDEX uk_webhook_inbox_message_id ON webhook_inbox (message_id);
CREATE INDEX idx_webhook_inbox_status_retry ON webhook_inbox (status, next_retry_time);
CREATE INDEX idx_webhook_inbox_device_code ON webhook_inbox (device_code);

CREATE TABLE IF NOT EXISTS webhook_fail_log (
    id            BIGINT NOT NULL PRIMARY KEY COMMENT '主键ID（雪花算法）',
    message_id    VARCHAR(100) NOT NULL COMMENT '消息唯一ID',
    tenant_id     CHAR(36) NOT NULL COMMENT '租户UUID',
    device_id     CHAR(36) DEFAULT NULL COMMENT 'TB设备ID',
    device_code   VARCHAR(100) NOT NULL COMMENT '设备编号',
    event_type    VARCHAR(50) NOT NULL COMMENT '事件类型',
    payload       JSON NOT NULL COMMENT '完整载荷（冗余）',
    error_type    VARCHAR(50) NOT NULL COMMENT '错误类型：PROCESS/RETRY/VALIDATION等',
    error_message TEXT NOT NULL COMMENT '错误详情',
    need_manual   TINYINT(1) DEFAULT 0 COMMENT '是否需要人工处理',
    retry_count   INT DEFAULT 0 COMMENT '已重试次数',
    recovered     TINYINT(1) DEFAULT 0 COMMENT '是否已恢复/重新入箱',
    failed_time   datetime DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '失败时间',
    created_time  datetime DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time  datetime DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Webhook 失败日志表：记录处理失败或需人工介入的消息';

CREATE INDEX idx_webhook_fail_message_id ON webhook_fail_log (message_id);
CREATE INDEX idx_webhook_fail_recovered ON webhook_fail_log (recovered, need_manual);

SET FOREIGN_KEY_CHECKS = 1;

