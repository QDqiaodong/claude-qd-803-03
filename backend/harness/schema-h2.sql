-- H2(MODE=MySQL) 本地端到端验证用：结构/列与 mysql/schema.sql 保持一致，仅替换方言语法。
-- docker 构建仍用 mysql/schema.sql，本文件不参与部署。

CREATE TABLE IF NOT EXISTS fabric_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fabric_type VARCHAR(40) NOT NULL,
    fabric_name VARCHAR(64) NOT NULL,
    max_ramp_rate DECIMAL(5,2) NOT NULL,
    max_hold_temp DECIMAL(5,1) NOT NULL,
    min_liquor_ratio DECIMAL(5,2) NOT NULL,
    enabled SMALLINT NOT NULL DEFAULT 1,
    CONSTRAINT uk_fabric_type UNIQUE (fabric_type)
);

CREATE TABLE IF NOT EXISTS dye_vat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vat_code VARCHAR(32) NOT NULL,
    machine_type VARCHAR(20) NOT NULL,
    capacity_kg DECIMAL(10,2) NOT NULL,
    liquor_ratio DECIMAL(5,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_temp DECIMAL(5,1) NOT NULL,
    target_temp DECIMAL(5,1) NOT NULL,
    hold_minutes INT NOT NULL DEFAULT 0,
    hold_remain_minutes INT NOT NULL DEFAULT 0,
    workshop VARCHAR(32) NOT NULL,
    remark VARCHAR(200),
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_vat_code UNIQUE (vat_code)
);
CREATE INDEX IF NOT EXISTS idx_vat_status ON dye_vat (status);

CREATE TABLE IF NOT EXISTS recipe (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipe_code VARCHAR(32) NOT NULL,
    color_code VARCHAR(32) NOT NULL,
    color_name VARCHAR(64) NOT NULL,
    customer VARCHAR(64) NOT NULL,
    fabric_type VARCHAR(40) NOT NULL,
    version_no INT NOT NULL DEFAULT 1,
    color_hex VARCHAR(9) NOT NULL,
    lab_l DECIMAL(6,2),
    lab_a DECIMAL(6,2),
    lab_b DECIMAL(6,2),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_owf DECIMAL(7,3) NOT NULL DEFAULT 0,
    cost_per_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
    create_time TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_recipe_code UNIQUE (recipe_code)
);
CREATE INDEX IF NOT EXISTS idx_recipe_color ON recipe (color_code);

CREATE TABLE IF NOT EXISTS recipe_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    seq_no INT NOT NULL,
    item_type VARCHAR(10) NOT NULL,
    dye_name VARCHAR(64) NOT NULL,
    owf_percent DECIMAL(8,3) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    color_hex VARCHAR(9),
    deleted SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_item_recipe ON recipe_item (recipe_id, seq_no);

CREATE TABLE IF NOT EXISTS process_curve (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    curve_code VARCHAR(32) NOT NULL,
    curve_name VARCHAR(64) NOT NULL,
    fabric_type VARCHAR(40) NOT NULL,
    color_code VARCHAR(32),
    recipe_id BIGINT,
    start_temp DECIMAL(5,1) NOT NULL DEFAULT 30,
    total_minutes INT NOT NULL DEFAULT 0,
    max_ramp_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_curve_code UNIQUE (curve_code)
);

CREATE TABLE IF NOT EXISTS curve_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    curve_id BIGINT NOT NULL,
    seq_no INT NOT NULL,
    stage VARCHAR(10) NOT NULL,
    target_temp DECIMAL(5,1) NOT NULL,
    duration_min INT NOT NULL,
    ramp_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
    memo VARCHAR(100)
);
CREATE INDEX IF NOT EXISTS idx_node_curve ON curve_node (curve_id, seq_no);

CREATE TABLE IF NOT EXISTS cloth_cart (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_code VARCHAR(32) NOT NULL,
    fabric_type VARCHAR(40) NOT NULL,
    meters DECIMAL(10,1) NOT NULL,
    color_code VARCHAR(32) NOT NULL,
    color_name VARCHAR(64) NOT NULL,
    customer VARCHAR(64) NOT NULL,
    vat_id BIGINT,
    vat_code VARCHAR(32),
    status VARCHAR(20) NOT NULL,
    dye_start_time TIMESTAMP,
    dye_end_time TIMESTAMP,
    due_time TIMESTAMP,
    rush_flag SMALLINT NOT NULL DEFAULT 0,
    rework_times INT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_cart_code UNIQUE (cart_code)
);
CREATE INDEX IF NOT EXISTS idx_cart_status ON cloth_cart (status);
CREATE INDEX IF NOT EXISTS idx_cart_vat ON cloth_cart (vat_id);
CREATE INDEX IF NOT EXISTS idx_cart_due ON cloth_cart (due_time);

CREATE TABLE IF NOT EXISTS vat_execution_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_code VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    cart_id BIGINT NOT NULL,
    cart_code VARCHAR(32) NOT NULL,
    vat_id BIGINT NOT NULL,
    vat_code VARCHAR(32) NOT NULL,
    recipe_id BIGINT NOT NULL,
    recipe_code VARCHAR(32) NOT NULL,
    recipe_version_no INT NOT NULL,
    curve_id BIGINT NOT NULL,
    curve_code VARCHAR(32) NOT NULL,
    source_revision CHAR(64) NOT NULL,
    estimated_weight_kg DECIMAL(10,2),
    estimated_minutes INT,
    key_temp DECIMAL(5,1),
    target_temp DECIMAL(5,1),
    hold_minutes INT,
    machine_max_temp DECIMAL(5,1),
    snapshot_json CLOB,
    invalid_reason VARCHAR(500),
    active_cart_id BIGINT,
    created_by VARCHAR(32),
    confirmed_by VARCHAR(32),
    create_time TIMESTAMP,
    issue_time TIMESTAMP,
    CONSTRAINT uk_execution_order_code UNIQUE (order_code),
    CONSTRAINT uk_issued_cart UNIQUE (active_cart_id)
);
CREATE INDEX IF NOT EXISTS idx_execution_status ON vat_execution_order (status);
CREATE INDEX IF NOT EXISTS idx_execution_recipe ON vat_execution_order (recipe_id);
CREATE INDEX IF NOT EXISTS idx_execution_curve ON vat_execution_order (curve_id);
CREATE INDEX IF NOT EXISTS idx_execution_vat ON vat_execution_order (vat_id);

CREATE TABLE IF NOT EXISTS chemical_stock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chem_code VARCHAR(32) NOT NULL,
    chem_name VARCHAR(64) NOT NULL,
    category VARCHAR(10) NOT NULL,
    unit VARCHAR(10) NOT NULL DEFAULT 'kg',
    stock_kg DECIMAL(10,2) NOT NULL,
    safety_kg DECIMAL(10,2) NOT NULL,
    batch_no VARCHAR(32) NOT NULL,
    expire_date DATE NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    supplier VARCHAR(64) NOT NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_chem_code UNIQUE (chem_code)
);

CREATE TABLE IF NOT EXISTS issue_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_id BIGINT NOT NULL,
    chem_code VARCHAR(32) NOT NULL,
    chem_name VARCHAR(64) NOT NULL,
    qty_kg DECIMAL(10,2) NOT NULL,
    operator VARCHAR(32) NOT NULL,
    recipe_code VARCHAR(32),
    cart_code VARCHAR(32),
    issue_time TIMESTAMP NOT NULL,
    deleted SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_issue_stock ON issue_record (stock_id);
CREATE INDEX IF NOT EXISTS idx_issue_time ON issue_record (issue_time);

-- 换色排程台
CREATE TABLE IF NOT EXISTS color_family (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_code VARCHAR(32) NOT NULL,
    family_name VARCHAR(48) NOT NULL,
    depth_level VARCHAR(10) NOT NULL,
    dye_class VARCHAR(20) NOT NULL,
    color_hex VARCHAR(9) NOT NULL DEFAULT '#cccccc',
    remark VARCHAR(200),
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_family_code UNIQUE (family_code)
);

CREATE TABLE IF NOT EXISTS color_family_map (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    color_code VARCHAR(32) NOT NULL,
    family_id BIGINT NOT NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_color_family_map UNIQUE (color_code)
);

CREATE TABLE IF NOT EXISTS wash_rule_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_no INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    change_note VARCHAR(300),
    created_by VARCHAR(32),
    create_time TIMESTAMP NOT NULL,
    publish_time TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_wash_version_no UNIQUE (version_no)
);

CREATE TABLE IF NOT EXISTS wash_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    from_family_id BIGINT NOT NULL,
    to_family_id BIGINT NOT NULL,
    switch_type VARCHAR(10) NOT NULL,
    wash_minutes INT NOT NULL,
    wash_cost DECIMAL(10,2) NOT NULL,
    remark VARCHAR(200),
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_wash_rule_cell UNIQUE (version_id, from_family_id, to_family_id)
);
CREATE INDEX IF NOT EXISTS idx_wash_rule_version ON wash_rule (version_id);

CREATE TABLE IF NOT EXISTS fabric_machine (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fabric_type VARCHAR(40) NOT NULL,
    machine_type VARCHAR(20) NOT NULL,
    preferred SMALLINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_fabric_machine UNIQUE (fabric_type, machine_type)
);

CREATE TABLE IF NOT EXISTS maintenance_window (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vat_id BIGINT NOT NULL,
    vat_code VARCHAR(32) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    reason VARCHAR(200) NOT NULL,
    created_by VARCHAR(32),
    create_time TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_maint_vat ON maintenance_window (vat_id, start_time, end_time);

CREATE TABLE IF NOT EXISTS schedule_lock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vat_id BIGINT NOT NULL,
    vat_code VARCHAR(32) NOT NULL,
    cart_id BIGINT,
    cart_code VARCHAR(32),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    lock_reason VARCHAR(200) NOT NULL,
    created_by VARCHAR(32),
    create_time TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_lock_vat ON schedule_lock (vat_id, start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_lock_cart ON schedule_lock (cart_id);

CREATE TABLE IF NOT EXISTS schedule_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_code VARCHAR(32) NOT NULL,
    plan_name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    rule_version_id BIGINT NOT NULL,
    rule_version_no INT NOT NULL,
    horizon_start_time TIMESTAMP NOT NULL,
    horizon_end_time TIMESTAMP NOT NULL,
    anchor_mode VARCHAR(16) NOT NULL DEFAULT 'NOW',
    input_snapshot_json CLOB NOT NULL,
    total_carts INT NOT NULL DEFAULT 0,
    scheduled_carts INT NOT NULL DEFAULT 0,
    excluded_carts INT NOT NULL DEFAULT 0,
    late_carts INT NOT NULL DEFAULT 0,
    total_wash_minutes INT NOT NULL DEFAULT 0,
    total_wash_cost DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_idle_minutes INT NOT NULL DEFAULT 0,
    total_late_minutes INT NOT NULL DEFAULT 0,
    created_by VARCHAR(32),
    create_time TIMESTAMP,
    reference_time TIMESTAMP,
    reference_key BIGINT,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_plan_code UNIQUE (plan_code),
    CONSTRAINT uk_plan_reference UNIQUE (reference_key)
);
CREATE INDEX IF NOT EXISTS idx_plan_rule ON schedule_plan (rule_version_id);

CREATE TABLE IF NOT EXISTS schedule_plan_assignment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    cart_id BIGINT NOT NULL,
    cart_code VARCHAR(32) NOT NULL,
    result_type VARCHAR(12) NOT NULL,
    vat_id BIGINT,
    vat_code VARCHAR(32),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    wash_before_min INT,
    wash_before_cost DECIMAL(10,2),
    wash_from_cart_id BIGINT,
    switch_type VARCHAR(10),
    family_code VARCHAR(32),
    late_minutes INT NOT NULL DEFAULT 0,
    locked SMALLINT NOT NULL DEFAULT 0,
    reason_text VARCHAR(500) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_assign_plan ON schedule_plan_assignment (plan_id);
CREATE INDEX IF NOT EXISTS idx_assign_cart ON schedule_plan_assignment (cart_id);
