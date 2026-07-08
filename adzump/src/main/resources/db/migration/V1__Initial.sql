/* Creating Database */

-- DROP DATABASE IF EXISTS `adzump`;

CREATE DATABASE IF NOT EXISTS `adzump` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `adzump`;

CREATE TABLE IF NOT EXISTS `adzump`.`adzump_campaign_plan` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    client_code CHAR(8) NOT NULL COMMENT 'Client code',
    schema_version VARCHAR(8) NOT NULL COMMENT 'Plan IR schema version',
    revision INT NOT NULL DEFAULT 1 COMMENT 'Plan revision, bumped on every patch',
    status ENUM('DRAFT','VALIDATED','LAUNCHING','LIVE_PAUSED','ACTIVE','PAUSED','PARTIALLY_LAUNCHED','ARCHIVED','FAILED') NOT NULL DEFAULT 'DRAFT' COMMENT 'Plan status',
    name VARCHAR(255) NOT NULL COMMENT 'Plan name',
    product_id VARCHAR(64) NOT NULL COMMENT 'Product this plan advertises',
    product_template_id VARCHAR(64) NULL COMMENT 'Product template id',
    vertical VARCHAR(48) NULL COMMENT 'Business vertical (open set, J5 registry-defined)',
    google_campaign_type ENUM('SEARCH','PMAX','DEMAND_GEN','DISPLAY','VIDEO','APP','SHOPPING','DSA') NULL COMMENT 'Google campaign type; NULL = plan does not target Google',
    meta_campaign_type ENUM('LEADS','SALES','TRAFFIC','AWARENESS','ENGAGEMENT','APP','ADVANTAGE_PLUS') NULL COMMENT 'Meta campaign type; NULL = plan does not target Meta',
    body JSON NOT NULL COMMENT 'Full nested plan IR (CampaignPlanBody)',
    created_by BIGINT UNSIGNED NULL COMMENT 'ID of the user who created this row',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    updated_by BIGINT UNSIGNED NULL COMMENT 'ID of the user who updated this row',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (id),
    KEY idx_plan_client_product (client_code, product_id),
    KEY idx_plan_status (client_code, status))
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `adzump`.`adzump_performance_policy` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    client_code CHAR(8) NOT NULL COMMENT 'Client code',
    scope ENUM('ACCOUNT_DEFAULT','CAMPAIGN_OVERRIDE') NOT NULL COMMENT 'Config scope',
    campaign_id BIGINT UNSIGNED NULL COMMENT 'Campaign plan id when scope is CAMPAIGN_OVERRIDE',
    vertical VARCHAR(48) NULL COMMENT 'Vertical the account default was seeded from (open set)',
    body JSON NOT NULL COMMENT 'PerformancePolicy body',
    created_by BIGINT UNSIGNED NULL COMMENT 'ID of the user who created this row',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    updated_by BIGINT UNSIGNED NULL COMMENT 'ID of the user who updated this row',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (id),
    KEY idx_pp_scope (client_code, scope, campaign_id))
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `adzump`.`adzump_autonomy_config` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    client_code CHAR(8) NOT NULL COMMENT 'Client code',
    scope ENUM('ACCOUNT_DEFAULT','CAMPAIGN_OVERRIDE') NOT NULL COMMENT 'Config scope',
    campaign_id BIGINT UNSIGNED NULL COMMENT 'Campaign plan id when scope is CAMPAIGN_OVERRIDE',
    vertical VARCHAR(48) NULL COMMENT 'Vertical the account default was seeded from (open set)',
    body JSON NOT NULL COMMENT 'AutonomyConfig body',
    created_by BIGINT UNSIGNED NULL COMMENT 'ID of the user who created this row',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    updated_by BIGINT UNSIGNED NULL COMMENT 'ID of the user who updated this row',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (id),
    KEY idx_ac_scope (client_code, scope, campaign_id))
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `adzump`.`adzump_milestone_mapping` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    client_code CHAR(8) NOT NULL COMMENT 'Client code',
    scope ENUM('ACCOUNT_DEFAULT','CAMPAIGN_OVERRIDE') NOT NULL COMMENT 'Config scope',
    campaign_id BIGINT UNSIGNED NULL COMMENT 'Campaign plan id when scope is CAMPAIGN_OVERRIDE',
    product_template_id VARCHAR(64) NOT NULL COMMENT 'Product template this mapping belongs to',
    body JSON NOT NULL COMMENT 'MilestoneMapping body (stage/status -> milestone + junk picks)',
    created_by BIGINT UNSIGNED NULL COMMENT 'ID of the user who created this row',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    updated_by BIGINT UNSIGNED NULL COMMENT 'ID of the user who updated this row',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (id),
    KEY idx_mm_template (client_code, product_template_id, scope))
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `adzump`.`adzump_creative_attribute` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    client_code CHAR(8) NOT NULL COMMENT 'Client code',
    creative_id VARCHAR(64) NOT NULL COMMENT 'Creative id within the plan body',
    axis VARCHAR(48) NOT NULL COMMENT 'Attribute axis (open set, vertical taxonomy J5)',
    value VARCHAR(128) NOT NULL COMMENT 'Attribute value on the axis (open set)',
    PRIMARY KEY (id),
    KEY idx_ca_creative (creative_id))
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `adzump`.`adzump_performance_snapshot` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    client_code CHAR(8) NOT NULL COMMENT 'Client code',
    campaign_plan_id BIGINT UNSIGNED NOT NULL COMMENT 'Campaign plan this snapshot belongs to',
    window_from DATE NOT NULL COMMENT 'Metrics window start date',
    window_to DATE NOT NULL COMMENT 'Metrics window end date',
    timezone VARCHAR(48) NOT NULL COMMENT 'Timezone of the metrics window',
    taken_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this snapshot was taken',
    body JSON NOT NULL COMMENT 'Snapshot metrics body (grain rows)',
    PRIMARY KEY (id),
    KEY idx_ps_plan (client_code, campaign_plan_id, taken_at))
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `adzump`.`adzump_action_audit` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    client_code CHAR(8) NOT NULL COMMENT 'Client code',
    campaign_plan_id BIGINT UNSIGNED NOT NULL COMMENT 'Campaign plan this action applies to',
    action_type ENUM('SHIFT_BUDGET','ADJUST_BID','REFINE_AUDIENCE','ADD_NEGATIVE_KEYWORD','PAUSE_ENTITY','ROTATE_CREATIVE','REQUEST_VARIANT') NOT NULL COMMENT 'Type of the action taken',
    verdict ENUM('APPLIED','QUEUED','SUPPRESSED','REJECTED','REVERSED') NOT NULL COMMENT 'Routing/apply verdict',
    triggered_by ENUM('USER','AGENT','SCHEDULER') NOT NULL COMMENT 'Who/what triggered the action',
    before_value JSON NULL COMMENT 'Value before the action',
    after_value JSON NULL COMMENT 'Value after the action',
    snapshot_id BIGINT UNSIGNED NULL COMMENT 'Performance snapshot backing the action',
    notes VARCHAR(512) NULL COMMENT 'Free-form notes',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    PRIMARY KEY (id),
    KEY idx_aa_plan (client_code, campaign_plan_id, created_at))
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
