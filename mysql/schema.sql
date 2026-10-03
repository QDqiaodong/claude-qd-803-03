-- ============================================================
-- 印染厂 · 染缸与染色配方管理  —— 建表 + 种子数据
-- 主线：缸 → 配方 → 工艺曲线 → 布车流转（+ 染化料库存）
-- 注意：application.yml 里 ddl-auto: none，表结构完全由本文件决定
-- ============================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS schedule_plan_assignment;
DROP TABLE IF EXISTS schedule_plan;
DROP TABLE IF EXISTS schedule_lock;
DROP TABLE IF EXISTS maintenance_window;
DROP TABLE IF EXISTS fabric_machine;
DROP TABLE IF EXISTS wash_rule;
DROP TABLE IF EXISTS wash_rule_version;
DROP TABLE IF EXISTS color_family_map;
DROP TABLE IF EXISTS color_family;
DROP TABLE IF EXISTS vat_execution_order;
DROP TABLE IF EXISTS issue_record;
DROP TABLE IF EXISTS chemical_stock;
DROP TABLE IF EXISTS cloth_cart;
DROP TABLE IF EXISTS curve_node;
DROP TABLE IF EXISTS process_curve;
DROP TABLE IF EXISTS recipe_item;
DROP TABLE IF EXISTS recipe;
DROP TABLE IF EXISTS fabric_profile;
DROP TABLE IF EXISTS dye_vat;

-- ---------------- 布种工艺档案 ----------------
CREATE TABLE fabric_profile (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    fabric_type       VARCHAR(40)  NOT NULL COMMENT '布种编码 COTTON/POLY/NYLON...',
    fabric_name       VARCHAR(64)  NOT NULL COMMENT '布种名称',
    max_ramp_rate     DECIMAL(5,2) NOT NULL COMMENT '允许升温速率上限 ℃/min',
    max_hold_temp     DECIMAL(5,1) NOT NULL COMMENT '允许保温温度上限 ℃',
    min_liquor_ratio  DECIMAL(5,2) NOT NULL COMMENT '最小浴比',
    enabled           TINYINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fabric_type (fabric_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='布种工艺档案';

-- ---------------- 染缸台账 ----------------
CREATE TABLE dye_vat (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    vat_code            VARCHAR(32)   NOT NULL COMMENT '缸号 V-01',
    machine_type        VARCHAR(20)   NOT NULL COMMENT 'OVERFLOW 溢喷/AIRFLOW 气流/JIG 卷染',
    capacity_kg         DECIMAL(10,2) NOT NULL COMMENT '容量 kg',
    liquor_ratio        DECIMAL(5,2)  NOT NULL COMMENT '浴比 1:N 中的 N',
    status              VARCHAR(20)   NOT NULL COMMENT 'IDLE/HEATING/HOLDING/COOLING/DRAINING/WASHING',
    current_temp        DECIMAL(5,1)  NOT NULL COMMENT '当前温度 ℃',
    target_temp         DECIMAL(5,1)  NOT NULL COMMENT '目标温度 ℃',
    hold_minutes        INT           NOT NULL DEFAULT 0 COMMENT '保温时长 分钟',
    hold_remain_minutes INT           NOT NULL DEFAULT 0 COMMENT '保温剩余 分钟',
    workshop            VARCHAR(32)   NOT NULL COMMENT '所在区域',
    remark              VARCHAR(200)  NULL,
    deleted             TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vat_code (vat_code),
    KEY idx_vat_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='染缸台账';

-- ---------------- 染色配方 ----------------
CREATE TABLE recipe (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    recipe_code  VARCHAR(32)   NOT NULL COMMENT '配方编号',
    color_code   VARCHAR(32)   NOT NULL COMMENT '色号',
    color_name   VARCHAR(64)   NOT NULL COMMENT '色名',
    customer     VARCHAR(64)   NOT NULL COMMENT '客户',
    fabric_type  VARCHAR(40)   NOT NULL COMMENT '布种',
    version_no   INT           NOT NULL DEFAULT 1 COMMENT '配方版本',
    color_hex    VARCHAR(9)    NOT NULL COMMENT '目标色卡 CSS 色值',
    lab_l        DECIMAL(6,2)  NULL COMMENT 'LAB-L',
    lab_a        DECIMAL(6,2)  NULL COMMENT 'LAB-A',
    lab_b        DECIMAL(6,2)  NULL COMMENT 'LAB-B',
    status       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/RELEASED/ARCHIVED',
    total_owf    DECIMAL(7,3)  NOT NULL DEFAULT 0 COMMENT '染料 owf 合计（应 100.000）',
    cost_per_kg  DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '染化料成本 元/kg',
    create_time  DATETIME      NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_recipe_code (recipe_code),
    KEY idx_recipe_color (color_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='染色配方';

-- ---------------- 配方明细（染料 / 助剂） ----------------
CREATE TABLE recipe_item (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    recipe_id   BIGINT        NOT NULL,
    seq_no      INT           NOT NULL COMMENT '行序号（@OrderBy 维护）',
    item_type   VARCHAR(10)   NOT NULL COMMENT 'DYE 染料 / AUX 助剂',
    dye_name    VARCHAR(64)   NOT NULL COMMENT '染料或助剂名称',
    owf_percent DECIMAL(8,3)  NOT NULL COMMENT 'owf 百分比',
    unit_price  DECIMAL(10,2) NOT NULL COMMENT '单价 元/kg',
    color_hex   VARCHAR(9)    NULL COMMENT '该行色阶色值',
    deleted     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_item_recipe (recipe_id, seq_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配方明细行';

-- ---------------- 工艺曲线 ----------------
CREATE TABLE process_curve (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    curve_code    VARCHAR(32)  NOT NULL COMMENT '曲线编号',
    curve_name    VARCHAR(64)  NOT NULL COMMENT '曲线名称',
    fabric_type   VARCHAR(40)  NOT NULL COMMENT '适用布种',
    color_code    VARCHAR(32)  NULL COMMENT '关联色号',
    recipe_id     BIGINT       NULL COMMENT '关联配方',
    start_temp    DECIMAL(5,1) NOT NULL DEFAULT 30 COMMENT '起始温度 ℃',
    total_minutes INT          NOT NULL DEFAULT 0 COMMENT '总时长 分钟',
    max_ramp_rate DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '全曲线最大升温速率 ℃/min',
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_curve_code (curve_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工艺曲线';

-- ---------------- 工艺曲线节点序列 ----------------
CREATE TABLE curve_node (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    curve_id     BIGINT       NOT NULL,
    seq_no       INT          NOT NULL COMMENT '节点序号（有序数据）',
    stage        VARCHAR(10)  NOT NULL COMMENT 'RAMP 升温/HOLD 保温/COOL 降温',
    target_temp  DECIMAL(5,1) NOT NULL COMMENT '节点目标温度 ℃',
    duration_min INT          NOT NULL COMMENT '本段时长 分钟',
    ramp_rate    DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '本段速率 ℃/min',
    memo         VARCHAR(100) NULL,
    PRIMARY KEY (id),
    KEY idx_node_curve (curve_id, seq_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工艺曲线节点';

-- ---------------- 布车流转 ----------------
CREATE TABLE cloth_cart (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    cart_code      VARCHAR(32)   NOT NULL COMMENT '布车号',
    fabric_type    VARCHAR(40)   NOT NULL COMMENT '布种',
    meters         DECIMAL(10,1) NOT NULL COMMENT '米数',
    color_code     VARCHAR(32)   NOT NULL COMMENT '色号',
    color_name     VARCHAR(64)   NOT NULL COMMENT '色名',
    customer       VARCHAR(64)   NOT NULL COMMENT '客户',
    vat_id         BIGINT        NULL COMMENT '所在染缸',
    vat_code       VARCHAR(32)   NULL COMMENT '缸号',
    status         VARCHAR(20)   NOT NULL COMMENT 'PENDING/DYEING/DONE/REWORK/SCRAPPED',
    dye_start_time DATETIME      NULL COMMENT '计划开始',
    dye_end_time   DATETIME      NULL COMMENT '计划结束',
    due_time       DATETIME      NULL COMMENT '客户交期（排程台维护，不改布车状态）',
    rush_flag      TINYINT       NOT NULL DEFAULT 0 COMMENT '急单标记 0/1',
    rework_times   INT           NOT NULL DEFAULT 0 COMMENT '返修次数',
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_code (cart_code),
    KEY idx_cart_status (status),
    KEY idx_cart_vat (vat_id),
    KEY idx_cart_due (due_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='布车流转';

-- ---------------- 开缸执行单 ----------------
CREATE TABLE vat_execution_order (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    order_code          VARCHAR(32)   NOT NULL COMMENT '执行单编号',
    status              VARCHAR(24)   NOT NULL COMMENT 'PENDING_CONFIRM/ISSUED/INVALID',
    cart_id             BIGINT        NOT NULL,
    cart_code           VARCHAR(32)   NOT NULL,
    vat_id              BIGINT        NOT NULL,
    vat_code            VARCHAR(32)   NOT NULL,
    recipe_id           BIGINT        NOT NULL,
    recipe_code         VARCHAR(32)   NOT NULL,
    recipe_version_no   INT           NOT NULL,
    curve_id            BIGINT        NOT NULL,
    curve_code          VARCHAR(32)   NOT NULL,
    source_revision     CHAR(64)      NOT NULL COMMENT '来源资料 SHA-256 指纹',
    estimated_weight_kg DECIMAL(10,2) NULL COMMENT '布车折算重量',
    estimated_minutes   INT           NULL COMMENT '曲线预计总时长',
    key_temp            DECIMAL(5,1)  NULL COMMENT '最高保温温度',
    target_temp         DECIMAL(5,1)  NULL COMMENT '下达目标温度',
    hold_minutes        INT           NULL COMMENT '下达保温总时长',
    machine_max_temp    DECIMAL(5,1)  NULL COMMENT '机台温度上限',
    snapshot_json       LONGTEXT      NULL COMMENT '下达时完整工艺快照',
    invalid_reason      VARCHAR(500)  NULL,
    active_cart_id      BIGINT        GENERATED ALWAYS AS (CASE WHEN status = 'ISSUED' THEN cart_id ELSE NULL END) STORED COMMENT '并发防重：同一布车仅一张已下达单',
    created_by          VARCHAR(32)   NULL,
    confirmed_by        VARCHAR(32)   NULL,
    create_time         DATETIME      NULL,
    issue_time          DATETIME      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_execution_order_code (order_code),
    UNIQUE KEY uk_issued_cart (active_cart_id),
    KEY idx_execution_status (status),
    KEY idx_execution_recipe (recipe_id),
    KEY idx_execution_curve (curve_id),
    KEY idx_execution_vat (vat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开缸执行单';

-- ---------------- 染料与助剂库存 ----------------
CREATE TABLE chemical_stock (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    chem_code   VARCHAR(32)   NOT NULL COMMENT '物料编码',
    chem_name   VARCHAR(64)   NOT NULL COMMENT '物料名称',
    category    VARCHAR(10)   NOT NULL COMMENT 'DYE 染料 / AUX 助剂',
    unit        VARCHAR(10)   NOT NULL DEFAULT 'kg',
    stock_kg    DECIMAL(10,2) NOT NULL COMMENT '库存 kg',
    safety_kg   DECIMAL(10,2) NOT NULL COMMENT '安全库存 kg',
    batch_no    VARCHAR(32)   NOT NULL COMMENT '批次号',
    expire_date DATE          NOT NULL COMMENT '效期',
    unit_price  DECIMAL(10,2) NOT NULL COMMENT '单价 元/kg',
    supplier    VARCHAR(64)   NOT NULL COMMENT '供应商',
    deleted     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chem_code (chem_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='染化料库存';

-- ---------------- 领用记录 ----------------
CREATE TABLE issue_record (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    stock_id    BIGINT        NOT NULL,
    chem_code   VARCHAR(32)   NOT NULL,
    chem_name   VARCHAR(64)   NOT NULL,
    qty_kg      DECIMAL(10,2) NOT NULL COMMENT '领用量 kg',
    operator    VARCHAR(32)   NOT NULL COMMENT '领用人',
    recipe_code VARCHAR(32)   NULL COMMENT '关联配方',
    cart_code   VARCHAR(32)   NULL COMMENT '关联布车',
    issue_time  DATETIME      NOT NULL COMMENT '领用时间',
    deleted     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_issue_stock (stock_id),
    KEY idx_issue_time (issue_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='染化料领用记录';

-- ============================================================
-- 染缸换色排程台
-- ============================================================

-- ---------------- 颜色族（深浅 + 染料族：清洗矩阵的行列） ----------------
CREATE TABLE color_family (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    family_code    VARCHAR(32)  NOT NULL COMMENT '族编码，如 REACTIVE_DARK',
    family_name    VARCHAR(48)  NOT NULL COMMENT '族名称，如 活性深色',
    depth_level    VARCHAR(10)  NOT NULL COMMENT 'SAME 同色/LIGHT 浅色/DARK 深色/SPECIAL 特殊染料',
    dye_class      VARCHAR(20)  NOT NULL COMMENT 'REACTIVE 活性/DISPERSE 分散/ACID 酸性/CATIONIC 阳离子/BRIGHT 增白/NEUTRAL 中性',
    color_hex      VARCHAR(9)   NOT NULL DEFAULT '#cccccc',
    remark         VARCHAR(200) NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_family_code (family_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排程颜色族';

-- ---------------- 色号 → 颜色族映射（排程解析色族的依据） ----------------
CREATE TABLE color_family_map (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    color_code  VARCHAR(32) NOT NULL,
    family_id   BIGINT      NOT NULL,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_color_family_map (color_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='色号色族映射';

-- ---------------- 清洗规则版本（矩阵快照口径） ----------------
CREATE TABLE wash_rule_version (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    version_no    INT          NOT NULL COMMENT '版本号（从 1 递增，永不复用）',
    status        VARCHAR(16)  NOT NULL COMMENT 'DRAFT 草稿/ACTIVE 当前/ARCHIVED 归档',
    change_note   VARCHAR(300) NULL COMMENT '本版调整说明',
    created_by    VARCHAR(32)  NULL,
    create_time   DATETIME     NOT NULL,
    publish_time  DATETIME     NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wash_version_no (version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='清洗规则版本';

-- ---------------- 清洗矩阵单元（版本 × 源族 → 目标族） ----------------
CREATE TABLE wash_rule (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    version_id     BIGINT        NOT NULL,
    from_family_id BIGINT        NOT NULL,
    to_family_id   BIGINT        NOT NULL,
    switch_type    VARCHAR(10)   NOT NULL COMMENT 'SAME 同色续染/L2D 浅转深/D2L 深转浅/SPECIAL 特殊切换',
    wash_minutes   INT           NOT NULL COMMENT '所需清洗时长 分钟',
    wash_cost      DECIMAL(10,2) NOT NULL COMMENT '清洗成本 元（水电+助剂）',
    remark         VARCHAR(200)  NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wash_rule_cell (version_id, from_family_id, to_family_id),
    KEY idx_wash_rule_version (version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='清洗矩阵单元';

-- ---------------- 布种 × 机型适配表 ----------------
CREATE TABLE fabric_machine (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    fabric_type   VARCHAR(40) NOT NULL,
    machine_type  VARCHAR(20) NOT NULL COMMENT 'OVERFLOW/AIRFLOW/JIG',
    preferred     TINYINT     NOT NULL DEFAULT 0 COMMENT '是否首选机型',
    deleted       TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fabric_machine (fabric_type, machine_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='布种机型适配';

-- ---------------- 染缸维护时段 ----------------
CREATE TABLE maintenance_window (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    vat_id       BIGINT       NOT NULL,
    vat_code     VARCHAR(32)  NOT NULL,
    start_time   DATETIME     NOT NULL,
    end_time     DATETIME     NOT NULL,
    reason       VARCHAR(200) NOT NULL COMMENT '维护原因',
    created_by   VARCHAR(32)  NULL,
    create_time  DATETIME     NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_maint_vat (vat_id, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='染缸维护时段';

-- ---------------- 人工锁定的承诺排程段 ----------------
CREATE TABLE schedule_lock (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    vat_id       BIGINT       NOT NULL,
    vat_code     VARCHAR(32)  NOT NULL,
    cart_id      BIGINT       NULL COMMENT '锁定布车（可空=仅封缸）',
    cart_code    VARCHAR(32)  NULL,
    start_time   DATETIME     NOT NULL,
    end_time     DATETIME     NOT NULL,
    lock_reason  VARCHAR(200) NOT NULL COMMENT '承诺/锁定原因',
    created_by   VARCHAR(32)  NULL,
    create_time  DATETIME     NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_lock_vat (vat_id, start_time, end_time),
    KEY idx_lock_cart (cart_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排程人工锁定段';

-- ---------------- 跨缸换色排程方案 ----------------
CREATE TABLE schedule_plan (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    plan_code           VARCHAR(32)  NOT NULL COMMENT '方案编号',
    plan_name           VARCHAR(64)  NOT NULL COMMENT '方案名（调度员留存用）',
    status              VARCHAR(16)  NOT NULL COMMENT 'DRAFT 草稿/REFERENCE 车间参考/ARCHIVED 归档',
    rule_version_id     BIGINT       NOT NULL COMMENT '生成时所用清洗规则版本',
    rule_version_no     INT          NOT NULL,
    horizon_start_time  DATETIME     NOT NULL COMMENT '排程锚点时间',
    horizon_end_time    DATETIME     NOT NULL COMMENT '排程视窗结束',
    anchor_mode         VARCHAR(16)  NOT NULL DEFAULT 'NOW' COMMENT 'NOW 当前/REPLAY 历史复算',
    input_snapshot_json LONGTEXT     NOT NULL COMMENT '生成时输入全量快照（布车/染缸/维护/锁定/规则）',
    total_carts         INT          NOT NULL DEFAULT 0,
    scheduled_carts     INT          NOT NULL DEFAULT 0,
    excluded_carts      INT          NOT NULL DEFAULT 0,
    late_carts          INT          NOT NULL DEFAULT 0,
    total_wash_minutes  INT          NOT NULL DEFAULT 0,
    total_wash_cost     DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_idle_minutes  INT          NOT NULL DEFAULT 0,
    total_late_minutes  INT          NOT NULL DEFAULT 0,
    created_by          VARCHAR(32)  NULL,
    create_time         DATETIME     NULL,
    reference_time      DATETIME     NULL COMMENT '选定为车间参考的时间',
    reference_key       BIGINT       GENERATED ALWAYS AS (CASE WHEN status = 'REFERENCE' THEN 1 ELSE NULL END) STORED COMMENT '同表仅允许一个参考方案',
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_code (plan_code),
    UNIQUE KEY uk_plan_reference (reference_key),
    KEY idx_plan_rule (rule_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='换色排程方案';

-- ---------------- 方案内每辆布车的排程结果 ----------------
CREATE TABLE schedule_plan_assignment (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    plan_id           BIGINT       NOT NULL,
    cart_id           BIGINT       NOT NULL,
    cart_code         VARCHAR(32)  NOT NULL,
    result_type       VARCHAR(12)  NOT NULL COMMENT 'SCHEDULED 已排/EXCLUDED 排除/FIXED 锁定或在制固定段',
    vat_id            BIGINT       NULL,
    vat_code          VARCHAR(32)  NULL,
    start_time        DATETIME     NULL COMMENT '染色开始（清洗结束后）',
    end_time          DATETIME     NULL COMMENT '染色结束',
    wash_before_min   INT          NULL COMMENT '入缸前清洗时长',
    wash_before_cost  DECIMAL(10,2) NULL,
    wash_from_cart_id BIGINT       NULL COMMENT '上一缸（清洗来源）布车',
    switch_type       VARCHAR(10)  NULL,
    family_code       VARCHAR(32)  NULL COMMENT '本车色族',
    late_minutes      INT          NOT NULL DEFAULT 0,
    locked            TINYINT      NOT NULL DEFAULT 0 COMMENT '是否人工锁定段',
    reason_text       VARCHAR(500) NOT NULL COMMENT '安排/延后/排除原因（人话解释）',
    PRIMARY KEY (id),
    KEY idx_assign_plan (plan_id),
    KEY idx_assign_cart (cart_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='方案布车排程明细';

-- ============================================================
-- 种子数据
-- ============================================================

-- 布种工艺档案（6 行）
INSERT INTO fabric_profile (id, fabric_type, fabric_name, max_ramp_rate, max_hold_temp, min_liquor_ratio, enabled) VALUES
 (1, 'SILK',     '真丝',        1.00,  95.0, 15.00, 1),
 (2, 'NYLON',    '锦纶弹力',    1.50, 120.0, 12.00, 1),
 (3, 'POLY',     '涤纶机织',    2.00, 135.0, 10.00, 1),
 (4, 'BLEND',    '涤棉混纺',    2.20, 130.0, 10.00, 1),
 (5, 'VISCOSE',  '人棉针织',    2.50, 110.0,  8.00, 1),
 (6, 'COTTON',   '全棉针织',    3.00, 130.0,  8.00, 1),
 (7, 'WOOL',     '羊毛精纺',    1.00, 100.0, 14.00, 1);

-- 染缸台账（12 行）
INSERT INTO dye_vat (id, vat_code, machine_type, capacity_kg, liquor_ratio, status, current_temp, target_temp, hold_minutes, hold_remain_minutes, workshop, remark, deleted) VALUES
 (1,  'V-01', 'OVERFLOW', 500.00,  8.00, 'HEATING',  96.0, 130.0, 30, 12, 'A区', '一号溢喷机，主力做全棉中温', 0),
 (2,  'V-02', 'AIRFLOW',  400.00,  6.00, 'HOLDING', 130.0, 130.0, 45, 28, 'A区', '气流机，适合涤纶高温高压', 0),
 (3,  'V-03', 'OVERFLOW', 600.00,  8.00, 'HEATING',  72.0, 100.0, 30, 30, 'A区', '大容量，锦纶酸性专用', 0),
 (4,  'V-04', 'JIG',      300.00, 12.00, 'COOLING',  78.0,  45.0,  0,  0, 'B区', '卷染机，真丝低温慢升', 0),
 (5,  'V-05', 'AIRFLOW',  450.00,  6.50, 'HOLDING', 110.0, 110.0, 40, 15, 'B区', '人流棉活性染色', 0),
 (6,  'V-06', 'OVERFLOW', 500.00,  8.00, 'DRAINING', 46.0,  45.0,  0,  0, 'B区', '排液中，准备过清水', 0),
 (7,  'V-07', 'JIG',      300.00, 10.00, 'WASHING',  38.0,  32.0,  0,  0, 'B区', '清洗中', 0),
 (8,  'V-08', 'AIRFLOW',  400.00,  6.00, 'IDLE',     32.0,  32.0,  0,  0, 'C区', '空闲待排产', 0),
 (9,  'V-09', 'OVERFLOW', 600.00,  8.00, 'IDLE',     32.0,  32.0,  0,  0, 'C区', '空闲待排产', 0),
 (10, 'V-10', 'JIG',      300.00, 12.00, 'HOLDING',  95.0,  95.0, 30, 22, 'C区', '卷染中温保温', 0),
 (11, 'V-11', 'AIRFLOW',  450.00,  6.50, 'HEATING',  58.0, 120.0, 35, 35, 'C区', '升温段，注意速率', 0),
 (12, 'V-12', 'OVERFLOW', 800.00,  9.00, 'COOLING',  88.0,  60.0,  0,  0, 'A区', '最大缸，混纺大批量', 0);

-- 染色配方（8 行）
INSERT INTO recipe (id, recipe_code, color_code, color_name, customer, fabric_type, version_no, color_hex, lab_l, lab_a, lab_b, status, total_owf, cost_per_kg, create_time, deleted) VALUES
 (1, 'R-2401', 'C-1901', '藏青',   '恒生纺织', 'COTTON',  1, '#1A2B6B', 28.40,  1.20, -32.60, 'RELEASED', 100.000, 82.31, '2026-08-11 09:20:00', 0),
 (2, 'R-2402', 'C-1902', '酒红',   '雅戈尔',   'POLY',    2, '#8B1A1A', 35.10, 42.30,  18.70, 'RELEASED', 100.000, 113.68, '2026-08-12 10:05:00', 0),
 (3, 'R-2403', 'C-1903', '墨绿',   '申洲针织', 'NYLON',   1, '#0F6E4F', 42.60, -18.40, 12.30, 'RELEASED', 100.000, 136.66, '2026-08-15 14:40:00', 0),
 (4, 'R-2404', 'C-1904', '芥黄',   '鲁泰纺织', 'BLEND',   3, '#C9A227', 72.30,  8.60,  56.40, 'RELEASED', 100.000, 108.62, '2026-08-18 11:15:00', 0),
 (5, 'R-2405', 'C-1905', '雪青',   '华孚色纺', 'VISCOSE', 1, '#6B4A8F', 48.20, 22.10, -26.80, 'DRAFT',    100.000, 162.06, '2026-08-20 16:30:00', 0),
 (6, 'R-2406', 'C-1906', '米白',   '恒生纺织', 'SILK',    1, '#D8D3C4', 88.40,  0.60,   6.20, 'RELEASED', 100.000, 79.44, '2026-08-22 09:00:00', 0),
 (7, 'R-2407', 'C-1907', '元黑',   '申洲针织', 'POLY',    2, '#1F1F1F', 16.80,  0.40,  -1.20, 'RELEASED', 100.000, 100.30, '2026-08-25 15:20:00', 0),
 (8, 'R-2408', 'C-1908', '湖蓝',   '雅戈尔',   'COTTON',  2, '#2E8BC0', 56.20, -12.60, -32.10, 'RELEASED', 100.000, 133.44, '2026-08-28 08:45:00', 0);

-- 配方明细（染料 owf 合计 100%，助剂单列不计入）
INSERT INTO recipe_item (id, recipe_id, seq_no, item_type, dye_name, owf_percent, unit_price, color_hex, deleted) VALUES
 (1,  1, 1, 'DYE', '活性藏青 B-133',   58.000,  86.00, '#1A2B6B', 0),
 (2,  1, 2, 'DYE', '活性黑 KN-B',      34.000,  72.50, '#10131C', 0),
 (3,  1, 3, 'DYE', '活性红 3BS',        8.000,  68.00, '#B03030', 0),
 (4,  1, 4, 'AUX', '匀染剂 AL-200',     1.500,  24.00, '#8E7CC3', 0),
 (5,  1, 5, 'AUX', '元明粉(盐)',       45.000,   1.20, '#D9D9D9', 0),
 (6,  1, 6, 'AUX', '纯碱',             20.000,   2.60, '#EFEFEF', 0),
 (7,  2, 1, 'DYE', '分散红玉 S-5BL',   46.000, 128.00, '#8B1A1A', 0),
 (8,  2, 2, 'DYE', '分散黄棕 S-2RFL',  32.000,  96.00, '#B5651D', 0),
 (9,  2, 3, 'DYE', '分散蓝 2BLN',      22.000, 104.00, '#27408B', 0),
 (10, 2, 4, 'AUX', '匀染剂 1227',       1.200,  32.00, '#8E7CC3', 0),
 (11, 2, 5, 'AUX', '冰醋酸',            0.800,   6.50, '#EAF2F8', 0),
 (12, 2, 6, 'AUX', '固色剂 SF-30',      2.000,  46.00, '#C7B8E3', 0),
 (13, 3, 1, 'DYE', '酸性绿 25',        52.000, 152.00, '#0F6E4F', 0),
 (14, 3, 2, 'DYE', '酸性黄 2G',        30.000, 118.00, '#C9B400', 0),
 (15, 3, 3, 'DYE', '酸性蓝 RAW',       18.000, 136.00, '#1B4F72', 0),
 (16, 3, 4, 'AUX', '匀染剂 WE-2',       1.000,  38.00, '#8E7CC3', 0),
 (17, 3, 5, 'AUX', '硫酸铵',            3.000,   4.20, '#E8E8E8', 0),
 (18, 4, 1, 'DYE', '分散黄 SE-6G',     44.000, 112.00, '#C9A227', 0),
 (19, 4, 2, 'DYE', '分散橙 S-4RL',     26.000,  98.00, '#D2691E', 0),
 (20, 4, 3, 'DYE', '分散红 FB',        18.000, 124.00, '#C0392B', 0),
 (21, 4, 4, 'DYE', '分散蓝 B-56',      12.000, 108.00, '#2874A6', 0),
 (22, 4, 5, 'AUX', '匀染剂 1227',       1.000,  32.00, '#8E7CC3', 0),
 (23, 4, 6, 'AUX', '固色剂 SF-30',      1.500,  46.00, '#C7B8E3', 0),
 (24, 4, 7, 'AUX', '碱剂',              5.000,   3.80, '#F1F1F1', 0),
 (25, 5, 1, 'DYE', '阳离子紫 3R',      40.000, 168.00, '#6B4A8F', 0),
 (26, 5, 2, 'DYE', '阳离子蓝 X-GRRL',  36.000, 156.00, '#3A5BA0', 0),
 (27, 5, 3, 'DYE', '阳离子红 X-GRL',   24.000, 162.00, '#A93226', 0),
 (28, 5, 4, 'AUX', '匀染剂 PAN',        1.200,  42.00, '#8E7CC3', 0),
 (29, 5, 5, 'AUX', '元明粉(盐)',       30.000,   1.20, '#D9D9D9', 0),
 (30, 6, 1, 'DYE', '增白剂 VBL',       88.000,  88.00, '#F4F1E6', 0),
 (31, 6, 2, 'DYE', '分散紫 HFRL',       0.600, 210.00, '#7D3C98', 0),
 (32, 6, 3, 'DYE', '分散蓝 2BLN',      11.400, 104.00, '#27408B', 0),
 (33, 6, 4, 'AUX', '双氧水',            6.000,   3.20, '#EAF2F8', 0),
 (34, 6, 5, 'AUX', '精练剂',            2.000,  12.00, '#D5D8DC', 0),
 (35, 7, 1, 'DYE', '分散黑 S-3BL',     62.000,  92.00, '#1F1F1F', 0),
 (36, 7, 2, 'DYE', '分散深蓝 HGL',     26.000, 118.00, '#154360', 0),
 (37, 7, 3, 'DYE', '分散红玉 S-5BL',   12.000, 128.00, '#8B1A1A', 0),
 (38, 7, 4, 'AUX', '匀染剂 1227',       1.500,  32.00, '#8E7CC3', 0),
 (39, 7, 5, 'AUX', '固色剂 SF-30',      2.500,  46.00, '#C7B8E3', 0),
 (40, 7, 6, 'AUX', '冰醋酸',            1.000,   6.50, '#EAF2F8', 0),
 (41, 8, 1, 'DYE', '活性翠蓝 KN-G',    56.000, 148.00, '#2E8BC0', 0),
 (42, 8, 2, 'DYE', '活性蓝 KN-R',      30.000, 132.00, '#1A5276', 0),
 (43, 8, 3, 'DYE', '活性嫩黄 4GL',     14.000,  96.00, '#F1C40F', 0),
 (44, 8, 4, 'AUX', '元明粉(盐)',       50.000,   1.20, '#D9D9D9', 0),
 (45, 8, 5, 'AUX', '纯碱',             18.000,   2.60, '#EFEFEF', 0),
 (46, 8, 6, 'AUX', '匀染剂 AL-200',     1.200,  24.00, '#8E7CC3', 0);

-- 工艺曲线（6 条）
INSERT INTO process_curve (id, curve_code, curve_name, fabric_type, color_code, recipe_id, start_temp, total_minutes, max_ramp_rate, deleted) VALUES
 (1, 'PC-001', '全棉活性中温染色曲线', 'COTTON',  'C-1901', 1, 30.0, 115, 1.50, 0),
 (2, 'PC-002', '涤纶分散高温高压曲线', 'POLY',    'C-1902', 2, 30.0, 155, 1.60, 0),
 (3, 'PC-003', '锦纶酸性染色曲线',     'NYLON',   'C-1903', 3, 30.0, 115, 1.50, 0),
 (4, 'PC-004', '涤棉混纺一浴法曲线',   'BLEND',   'C-1904', 4, 30.0, 120, 2.50, 0),
 (5, 'PC-005', '人棉活性低温曲线',     'VISCOSE', 'C-1905', 5, 30.0, 125, 1.40, 0),
 (6, 'PC-006', '真丝低温慢升曲线',     'SILK',    'C-1906', 6, 30.0, 135, 1.00, 0),
 (7, 'PC-007', '全棉活性湖蓝中温曲线', 'COTTON',  'C-1908', 8, 30.0, 110, 1.50, 0);

-- 工艺曲线节点（有序序列：升温 → 保温 → 降温）
INSERT INTO curve_node (id, curve_id, seq_no, stage, target_temp, duration_min, ramp_rate, memo) VALUES
 (1,  1, 1, 'RAMP',  60.0, 20,  1.50, '入染后慢升，防色花'),
 (2,  1, 2, 'HOLD',  60.0, 15,  0.00, '加盐促染'),
 (3,  1, 3, 'RAMP',  95.0, 25,  1.40, '升至固色温度'),
 (4,  1, 4, 'HOLD',  95.0, 30,  0.00, '加碱固色'),
 (5,  1, 5, 'COOL',  60.0, 15, -2.33, '缓慢降温'),
 (6,  1, 6, 'COOL',  45.0, 10, -1.50, '降至排液温度'),
 (7,  2, 1, 'RAMP',  90.0, 40,  1.50, '升温至玻璃化温度'),
 (8,  2, 2, 'HOLD',  90.0, 10,  0.00, '匀染'),
 (9,  2, 3, 'RAMP', 130.0, 25,  1.60, '升至高温高压'),
 (10, 2, 4, 'HOLD', 130.0, 45,  0.00, '保温上染'),
 (11, 2, 5, 'COOL',  80.0, 20, -2.50, '降温防皱'),
 (12, 2, 6, 'COOL',  45.0, 15, -2.33, '降至排液温度'),
 (13, 3, 1, 'RAMP',  70.0, 30,  1.33, '锦纶低温起步'),
 (14, 3, 2, 'HOLD',  70.0, 15,  0.00, '酸性染料匀染'),
 (15, 3, 3, 'RAMP', 100.0, 20,  1.50, '升至上限温度'),
 (16, 3, 4, 'HOLD', 100.0, 30,  0.00, '保温上染'),
 (17, 3, 5, 'COOL',  50.0, 20, -2.50, '降温'),
 (18, 4, 1, 'RAMP',  80.0, 25,  2.00, '一浴法起步'),
 (19, 4, 2, 'RAMP', 130.0, 20,  2.50, '⚠ 该段速率超过涤棉允许上限 2.20'),
 (20, 4, 3, 'HOLD', 130.0, 40,  0.00, '保温上染'),
 (21, 4, 4, 'COOL',  60.0, 25, -2.80, '降温'),
 (22, 4, 5, 'COOL',  45.0, 10, -1.50, '降至排液温度'),
 (23, 5, 1, 'RAMP',  65.0, 25,  1.40, '人棉慢升'),
 (24, 5, 2, 'HOLD',  65.0, 20,  0.00, '加盐'),
 (25, 5, 3, 'RAMP', 100.0, 30,  1.17, '升至固色温度'),
 (26, 5, 4, 'HOLD', 100.0, 25,  0.00, '固色'),
 (27, 5, 5, 'COOL',  45.0, 25, -2.20, '降温'),
 (28, 6, 1, 'RAMP',  60.0, 40,  0.75, '真丝极慢升温'),
 (29, 6, 2, 'HOLD',  60.0, 20,  0.00, '匀染'),
 (30, 6, 3, 'RAMP',  85.0, 25,  1.00, '升至上限温度'),
 (31, 6, 4, 'HOLD',  85.0, 30,  0.00, '保温'),
 (32, 6, 5, 'COOL',  45.0, 20, -2.00, '降温'),
 (33, 7, 1, 'RAMP',  60.0, 20,  1.50, '湖蓝慢升入染'),
 (34, 7, 2, 'HOLD',  60.0, 15,  0.00, '加盐促染'),
 (35, 7, 3, 'RAMP',  90.0, 20,  1.50, '升至固色温度'),
 (36, 7, 4, 'HOLD',  90.0, 30,  0.00, '加碱固色'),
 (37, 7, 5, 'COOL',  45.0, 25, -1.80, '降至排液温度');

-- 布车流转（24 行，含 1 辆报废车，会被 @SQLRestriction 过滤掉；带交期/急单的行供换色排程台验收）
INSERT INTO cloth_cart (id, cart_code, fabric_type, meters, color_code, color_name, customer, vat_id, vat_code, status, dye_start_time, dye_end_time, due_time, rush_flag, rework_times, deleted) VALUES
 (1,  'C-2401', 'COTTON',  1250.0, 'C-1901', '藏青', '申洲针织', 1,  'V-01', 'DYEING',   '2026-09-27 08:00:00', '2026-09-27 11:30:00', NULL,                       0, 0, 0),
 (2,  'C-2402', 'POLY',     860.0, 'C-1902', '酒红', '雅戈尔',   2,  'V-02', 'DYEING',   '2026-09-27 08:30:00', '2026-09-27 12:00:00', NULL,                       0, 0, 0),
 (3,  'C-2403', 'NYLON',   1520.0, 'C-1903', '墨绿', '申洲针织', 3,  'V-03', 'DYEING',   '2026-09-27 09:00:00', '2026-09-27 12:30:00', NULL,                       0, 0, 0),
 (4,  'C-2404', 'BLEND',    980.0, 'C-1904', '芥黄', '鲁泰纺织', 4,  'V-04', 'DYEING',   '2026-09-27 09:30:00', '2026-09-27 13:00:00', NULL,                       0, 0, 0),
 (5,  'C-2405', 'VISCOSE', 1100.0, 'C-1905', '雪青', '华孚色纺', 5,  'V-05', 'DYEING',   '2026-09-27 10:00:00', '2026-09-27 13:30:00', NULL,                       0, 0, 0),
 (6,  'C-2406', 'COTTON',   760.0, 'C-1908', '湖蓝', '雅戈尔',   NULL, NULL, 'PENDING',  NULL, NULL, '2026-10-04 18:00:00', 0, 0, 0),
 (7,  'C-2407', 'POLY',    1340.0, 'C-1907', '元黑', '申洲针织', NULL, NULL, 'PENDING',  NULL, NULL, '2026-10-03 12:00:00', 0, 0, 0),
 (8,  'C-2408', 'SILK',     420.0, 'C-1906', '米白', '恒生纺织', NULL, NULL, 'PENDING',  NULL, NULL, '2026-10-03 18:00:00', 1, 0, 0),
 (9,  'C-2409', 'COTTON',  1580.0, 'C-1901', '藏青', '恒生纺织', NULL, NULL, 'PENDING',  NULL, NULL, '2026-10-05 18:00:00', 0, 0, 0),
 (10, 'C-2410', 'COTTON',  1050.0, 'C-1908', '湖蓝', '雅戈尔',   6,  'V-06', 'DONE',     '2026-09-26 08:00:00', '2026-09-26 11:00:00', NULL,                       0, 0, 0),
 (11, 'C-2411', 'POLY',    1210.0, 'C-1902', '酒红', '雅戈尔',   7,  'V-07', 'DONE',     '2026-09-26 09:00:00', '2026-09-26 12:30:00', NULL,                       0, 0, 0),
 (12, 'C-2412', 'NYLON',    890.0, 'C-1903', '墨绿', '申洲针织', 8,  'V-08', 'DONE',     '2026-09-26 13:00:00', '2026-09-26 16:30:00', NULL,                       0, 0, 0),
 (13, 'C-2413', 'VISCOSE',  640.0, 'C-1905', '雪青', '华孚色纺', 9,  'V-09', 'DONE',     '2026-09-26 14:00:00', '2026-09-26 17:00:00', NULL,                       0, 0, 0),
 (14, 'C-2414', 'SILK',     380.0, 'C-1906', '米白', '恒生纺织', NULL, NULL, 'REWORK',   NULL, NULL, '2026-10-02 12:00:00', 1, 1, 0),
 (15, 'C-2415', 'COTTON',   900.0, 'C-1907', '元黑', '申洲针织', NULL, NULL, 'SCRAPPED', NULL, NULL, NULL,                       0, 2, 0),
 -- —— 以下为换色排程台验收补充布车（待排产池） ——
 (16, 'C-2501', 'COTTON',   880.0, 'C-1901', '藏青', '恒生纺织', NULL, NULL, 'PENDING',  NULL, NULL, '2026-10-04 20:00:00', 0, 0, 0),
 (17, 'C-2502', 'COTTON',   960.0, 'C-1908', '湖蓝', '雅戈尔',   NULL, NULL, 'PENDING',  NULL, NULL, '2026-10-04 12:00:00', 0, 0, 0),
 (18, 'C-2503', 'SILK',     450.0, 'C-1906', '米白', '鲁泰纺织', NULL, NULL, 'PENDING',  NULL, NULL, '2026-10-05 18:00:00', 0, 0, 0),
 (19, 'C-2504', 'POLY',    1100.0, 'C-1907', '元黑', '申洲针织', NULL, NULL, 'PENDING',  NULL, NULL, '2026-10-02 20:00:00', 1, 0, 0),
 (20, 'C-2505', 'POLY',     720.0, 'C-1902', '酒红', '雅戈尔',   NULL, NULL, 'PENDING',  NULL, NULL, '2026-10-05 12:00:00', 0, 0, 0),
 (21, 'C-2506', 'NYLON',    820.0, 'C-1903', '墨绿', '华孚色纺', NULL, NULL, 'PENDING',  NULL, NULL, '2026-10-06 18:00:00', 0, 0, 0),
 (22, 'C-2507', 'VISCOSE',  660.0, 'C-1905', '雪青', '申洲针织', NULL, NULL, 'PENDING',  NULL, NULL, '2026-10-06 12:00:00', 1, 0, 0),
 (23, 'C-2508', 'COTTON',  4600.0, 'C-1901', '藏青', '恒生纺织', NULL, NULL, 'PENDING',  NULL, NULL, '2026-10-07 18:00:00', 0, 0, 0),
 (24, 'C-2509', 'SILK',     950.0, 'C-1906', '米白', '华孚色纺', NULL, NULL, 'PENDING',  NULL, NULL, '2026-10-05 12:00:00', 0, 0, 0),
 (25, 'C-2510', 'WOOL',     500.0, 'W-3001', '驼色', '阳光毛纺', NULL, NULL, 'PENDING',  NULL, NULL, '2026-10-04 12:00:00', 0, 0, 0),
 (26, 'C-2511', 'COTTON',   700.0, 'C-1908', '湖蓝', '申洲针织', NULL, NULL, 'PENDING',  NULL, NULL, '2026-10-03 18:00:00', 1, 0, 0);

-- 染料与助剂库存（13 行）
INSERT INTO chemical_stock (id, chem_code, chem_name, category, unit, stock_kg, safety_kg, batch_no, expire_date, unit_price, supplier, deleted) VALUES
 (1,  'DC-1001', '活性藏青 B-133',  'DYE', 'kg',  320.00, 120.00, 'B-2601-01', '2027-03-31',  86.00, '浙江龙盛', 0),
 (2,  'DC-1002', '活性黑 KN-B',     'DYE', 'kg',   86.50, 100.00, 'B-2602-04', '2027-01-31',  72.50, '浙江龙盛', 0),
 (3,  'DC-1003', '活性红 3BS',      'DYE', 'kg',  240.00,  80.00, 'B-2601-09', '2027-05-30',  68.00, '闰土股份', 0),
 (4,  'DC-1004', '分散红玉 S-5BL',  'DYE', 'kg',  410.00, 150.00, 'B-2512-11', '2026-12-31', 128.00, '浙江龙盛', 0),
 (5,  'DC-1005', '分散黄棕 S-2RFL', 'DYE', 'kg',   52.00,  90.00, 'B-2603-02', '2027-06-30',  96.00, '闰土股份', 0),
 (6,  'DC-1006', '分散蓝 2BLN',     'DYE', 'kg',  180.00,  90.00, 'B-2601-06', '2027-04-30', 104.00, '安诺其',   0),
 (7,  'DC-1007', '酸性绿 25',       'DYE', 'kg',  145.00,  60.00, 'B-2602-08', '2027-02-28', 152.00, '安诺其',   0),
 (8,  'DC-1008', '阳离子紫 3R',     'DYE', 'kg',   96.00,  60.00, 'B-2601-12', '2027-03-15', 168.00, '传化智联', 0),
 (9,  'AX-2001', '匀染剂 AL-200',   'AUX', 'kg',  640.00, 200.00, 'B-2601-03', '2027-08-31',  24.00, '传化智联', 0),
 (10, 'AX-2002', '元明粉(盐)',      'AUX', 'kg', 3200.00, 800.00, 'B-2602-01', '2028-02-28',   1.20, '中盐化工', 0),
 (11, 'AX-2003', '纯碱',            'AUX', 'kg', 1150.00, 400.00, 'B-2601-07', '2028-01-31',   2.60, '三友化工', 0),
 (12, 'AX-2004', '固色剂 SF-30',    'AUX', 'kg',   74.00, 120.00, 'B-2512-05', '2026-11-30',  46.00, '传化智联', 0),
 (13, 'AX-2005', '冰醋酸',          'AUX', 'kg',  480.00, 150.00, 'B-2602-10', '2027-07-31',   6.50, '华鲁恒升', 0);

-- 领用记录（18 行）
INSERT INTO issue_record (id, stock_id, chem_code, chem_name, qty_kg, operator, recipe_code, cart_code, issue_time, deleted) VALUES
 (1,  1,  'DC-1001', '活性藏青 B-133',  62.50, '王建国', 'R-2401', 'C-2401', '2026-09-27 07:40:00', 0),
 (2,  5,  'DC-1005', '分散黄棕 S-2RFL', 18.40, '李红梅', 'R-2402', 'C-2402', '2026-09-27 08:10:00', 0),
 (3,  4,  'DC-1004', '分散红玉 S-5BL',  27.80, '李红梅', 'R-2402', 'C-2402', '2026-09-27 08:12:00', 0),
 (4,  7,  'DC-1007', '酸性绿 25',       41.20, '张伟',   'R-2403', 'C-2403', '2026-09-27 08:45:00', 0),
 (5,  6,  'DC-1006', '分散蓝 2BLN',     14.60, '张伟',   'R-2403', 'C-2403', '2026-09-27 08:47:00', 0),
 (6,  8,  'DC-1008', '阳离子紫 3R',     22.10, '陈晓芳', 'R-2405', 'C-2405', '2026-09-27 09:35:00', 0),
 (7,  9,  'AX-2001', '匀染剂 AL-200',    8.00, '王建国', 'R-2401', 'C-2401', '2026-09-27 07:45:00', 0),
 (8,  10, 'AX-2002', '元明粉(盐)',     180.00, '王建国', 'R-2401', 'C-2401', '2026-09-27 07:50:00', 0),
 (9,  11, 'AX-2003', '纯碱',            75.00, '王建国', 'R-2401', 'C-2401', '2026-09-27 09:55:00', 0),
 (10, 2,  'DC-1002', '活性黑 KN-B',     24.30, '刘志强', 'R-2407', 'C-2407', '2026-09-26 15:20:00', 0),
 (11, 1,  'DC-1001', '活性藏青 B-133',  55.00, '刘志强', 'R-2401', 'C-2409', '2026-09-26 16:05:00', 0),
 (12, 12, 'AX-2004', '固色剂 SF-30',    12.50, '李红梅', 'R-2402', 'C-2411', '2026-09-26 09:20:00', 0),
 (13, 13, 'AX-2005', '冰醋酸',           9.80, '李红梅', 'R-2402', 'C-2411', '2026-09-26 09:22:00', 0),
 (14, 3,  'DC-1003', '活性红 3BS',       6.40, '陈晓芳', 'R-2401', 'C-2410', '2026-09-26 08:05:00', 0),
 (15, 4,  'DC-1004', '分散红玉 S-5BL',  33.60, '张伟',   'R-2407', 'C-2411', '2026-09-26 09:30:00', 0),
 (16, 9,  'AX-2001', '匀染剂 AL-200',    6.50, '张伟',   'R-2403', 'C-2412', '2026-09-26 13:10:00', 0),
 (17, 10, 'AX-2002', '元明粉(盐)',     150.00, '陈晓芳', 'R-2405', 'C-2413', '2026-09-26 14:05:00', 0),
 (18, 7,  'DC-1007', '酸性绿 25',       19.70, '刘志强', 'R-2403', 'C-2412', '2026-09-26 13:15:00', 0);

-- ============================================================
-- 换色排程台种子数据
-- ============================================================

-- 颜色族：8 个（按染料类 × 深浅/特殊划分）
INSERT INTO color_family (id, family_code, family_name, depth_level, dye_class, color_hex, remark, deleted) VALUES
 (1, 'REACTIVE_LIGHT', '活性浅色', 'LIGHT',  'REACTIVE', '#9fd0e8', '活性染料浅色系（湖蓝等）', 0),
 (2, 'REACTIVE_DARK',  '活性深色', 'DARK',   'REACTIVE', '#1A2B6B', '活性藏青/活性黑', 0),
 (3, 'DISPERSE_LIGHT', '分散浅色', 'LIGHT',  'DISPERSE', '#c98a5a', '分散酒红/芥黄等中浅色', 0),
 (4, 'DISPERSE_DARK',  '分散深色', 'DARK',   'DISPERSE', '#1F1F1F', '分散黑/分散深蓝', 0),
 (5, 'ACID_DARK',      '酸性深色', 'DARK',   'ACID',     '#0F6E4F', '锦纶酸性墨绿', 0),
 (6, 'CATIONIC_SPECIAL','阳离子特殊', 'SPECIAL', 'CATIONIC', '#6B4A8F', '阳离子染料雪青：残留沾色风险高，切换需拆缸清洗', 0),
 (7, 'WHITE_BRIGHT',   '增白浅白', 'SPECIAL', 'BRIGHT',   '#F4F1E6', '真丝增白米白：任何残色都可见，进缸前必须深洗', 0),
 (8, 'NEUTRAL',        '中性清缸', 'SAME',   'NEUTRAL',  '#f0f0f0', '空缸/大保养后的中性起点', 0);

-- 色号 → 颜色族（排程解析色族的依据）
INSERT INTO color_family_map (id, color_code, family_id, deleted) VALUES
 (1, 'C-1901', 2, 0), -- 藏青   → 活性深色
 (2, 'C-1908', 1, 0), -- 湖蓝   → 活性浅色
 (3, 'C-1902', 3, 0), -- 酒红   → 分散浅色
 (4, 'C-1904', 3, 0), -- 芥黄   → 分散浅色
 (5, 'C-1907', 4, 0), -- 元黑   → 分散深色
 (6, 'C-1903', 5, 0), -- 墨绿   → 酸性深色
 (7, 'C-1905', 6, 0), -- 雪青   → 阳离子特殊
 (8, 'C-1906', 7, 0); -- 米白   → 增白浅白
-- W-3001 驼色不配置色族映射 → C-2510 同时触发“色族/配方资料不完整”排除

-- 布种 × 机型适配（排程机型约束依据）
INSERT INTO fabric_machine (id, fabric_type, machine_type, preferred, deleted) VALUES
 (1,  'COTTON',  'OVERFLOW', 1, 0),
 (2,  'COTTON',  'AIRFLOW',  0, 0),
 (3,  'POLY',    'AIRFLOW',  1, 0),
 (4,  'POLY',    'OVERFLOW', 0, 0),
 (5,  'NYLON',   'OVERFLOW', 1, 0),
 (6,  'BLEND',   'OVERFLOW', 1, 0),
 (7,  'BLEND',   'AIRFLOW',  0, 0),
 (8,  'VISCOSE', 'AIRFLOW',  1, 0),
 (9,  'SILK',    'JIG',      1, 0);
-- 注意：WOOL 没有任何适配行 → 驼色布车 C-2510 必然机型不适配

-- 清洗规则 V1（当前口径）
INSERT INTO wash_rule_version (id, version_no, status, change_note, created_by, create_time, publish_time, deleted) VALUES
 (1, 1, 'ACTIVE', '初版清洗矩阵：同色续染 0~15 分钟，深转浅/特殊切换重洗', 'system', '2026-09-20 09:00:00', '2026-09-20 09:00:00', 0);

-- 清洗矩阵单元（from_family × to_family；未显式配置的格按深浅类型兜底）
-- 时长单位：分钟；成本单位：元（水电+清洗助剂）
INSERT INTO wash_rule (id, version_id, from_family_id, to_family_id, switch_type, wash_minutes, wash_cost, remark, deleted) VALUES
-- 同色/同族续染
 (1,  1, 1, 1, 'SAME', 15,  60.00, '活性浅色续染：清水过缸', 0),
 (2,  1, 2, 2, 'SAME', 10,  45.00, '活性深色续染：深色互染风险低', 0),
 (3,  1, 3, 3, 'SAME', 15,  70.00, '分散浅色续染', 0),
 (4,  1, 4, 4, 'SAME', 10,  50.00, '分散深色续染', 0),
 (5,  1, 5, 5, 'SAME', 15,  65.00, '酸性深色续染', 0),
-- 浅转深（方向有利，轻洗即可）
 (6,  1, 1, 2, 'L2D', 20,  90.00, '活性浅转深：轻洗', 0),
 (7,  1, 3, 4, 'L2D', 20,  95.00, '分散浅转深：轻洗', 0),
-- 深转浅（残色会沾到浅色，必须重洗）
 (8,  1, 2, 1, 'D2L', 75, 420.00, '活性深转浅：两道皂洗+热水', 0),
 (9,  1, 4, 3, 'D2L', 80, 460.00, '分散深转浅：还原清洗+皂洗', 0),
 (10, 1, 5, 1, 'D2L', 70, 400.00, '酸性深转活性浅', 0),
-- 特殊染料切换
 (11, 1, 2, 6, 'SPECIAL', 90, 560.00, '活性深→阳离子：剥色处理防沉淀', 0),
 (12, 1, 6, 2, 'SPECIAL', 90, 560.00, '阳离子→活性深：全拆洗', 0),
 (13, 1, 4, 6, 'SPECIAL', 95, 600.00, '分散深→阳离子', 0),
 (14, 1, 6, 4, 'SPECIAL', 85, 520.00, '阳离子→分散深', 0),
 (15, 1, 1, 7, 'SPECIAL', 60, 360.00, '任何色→增白米白：深洗防泛黄', 0),
 (16, 1, 2, 7, 'SPECIAL', 110, 720.00, '活性深→增白：漂白级清洗', 0),
 (17, 1, 4, 7, 'SPECIAL', 120, 780.00, '分散深→增白：还原+漂白', 0),
 (18, 1, 5, 7, 'SPECIAL', 100, 660.00, '酸性深→增白', 0),
 (19, 1, 6, 7, 'SPECIAL', 120, 800.00, '阳离子→增白：最高等级清洗', 0),
 (20, 1, 7, 2, 'SPECIAL', 30, 150.00, '增白→活性深：短洗', 0),
 (21, 1, 7, 4, 'SPECIAL', 30, 150.00, '增白→分散深：短洗', 0),
-- 跨染料类常规深浅切换
 (22, 1, 3, 1, 'D2L', 60, 330.00, '分散浅→活性浅：换染料体系中洗', 0),
 (23, 1, 1, 3, 'L2D', 35, 180.00, '活性浅→分散浅', 0),
 (24, 1, 4, 2, 'D2L', 65, 360.00, '分散深→活性深', 0),
 (25, 1, 2, 4, 'L2D', 30, 160.00, '活性深→分散深', 0),
 (26, 1, 5, 3, 'D2L', 70, 390.00, '酸性深→分散浅', 0),
 (27, 1, 5, 6, 'SPECIAL', 85, 520.00, '酸性→阳离子特殊切换', 0),
 (28, 1, 3, 7, 'SPECIAL', 90, 580.00, '分散浅色→增白', 0);

-- 染缸维护时段（锚点 2026-10-02 14:00 附近，验收维护窗口冲突）
INSERT INTO maintenance_window (id, vat_id, vat_code, start_time, end_time, reason, created_by, create_time, deleted) VALUES
 (1, 9,  'V-09', '2026-10-03 08:00:00', '2026-10-03 14:00:00', '喷咀保养+滤网更换', '设备科', '2026-09-28 10:00:00', 0),
 (2, 4,  'V-04', '2026-10-04 08:00:00', '2026-10-04 12:00:00', '卷染辊筒检修',     '设备科', '2026-09-28 10:05:00', 0),
 (3, 10, 'V-10', '2026-10-03 18:00:00', '2026-10-04 02:00:00', '夜班停机校验温控', '设备科', '2026-09-28 10:10:00', 0);

-- 人工锁定段：急单元黑 C-2504 承诺锁在 V-02；另一段 V-08 封缸
INSERT INTO schedule_lock (id, vat_id, vat_code, cart_id, cart_code, start_time, end_time, lock_reason, created_by, create_time, deleted) VALUES
 (1, 2, 'V-02', 19, 'C-2504', '2026-10-03 06:00:00', '2026-10-03 09:35:00', '已向申洲针织承诺的急单缸位，重算不得移动', '调度员-周敏', '2026-09-29 16:20:00', 0),
 (2, 8, 'V-08', NULL, NULL,     '2026-10-03 13:00:00', '2026-10-03 17:00:00', '留缸等待翻改布（仅封缸，不绑布车）',   '调度员-周敏', '2026-09-29 16:25:00', 0);
