/* V3 — additive: J21 creative-experiment. Builds ON V1 + V2 (no drop). */

USE `adzump`;

CREATE TABLE IF NOT EXISTS `adzump`.`adzump_experiment` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    client_code CHAR(8) NOT NULL COMMENT 'Client code (tenant-private)',
    campaign_plan_id BIGINT UNSIGNED NOT NULL COMMENT 'Campaign plan this experiment runs on',
    hypothesis VARCHAR(512) NULL COMMENT 'Human hypothesis under test',
    metric VARCHAR(64) NOT NULL COMMENT 'Success metric: blended objective at creative grain (e.g. blendedScore@creative)',
    min_volume_per_variant INT NOT NULL DEFAULT 300 COMMENT 'Minimum CRM outcome volume per arm before a readout may reach significance',
    max_duration_days INT NOT NULL DEFAULT 14 COMMENT 'Hard duration cap in days; experiment ends INCONCLUSIVE at this cap rather than running unbounded',
    status ENUM('DESIGNED','RUNNING','SIGNIFICANT','INCONCLUSIVE','APPLIED') NOT NULL DEFAULT 'DESIGNED' COMMENT 'Experiment lifecycle status',
    variants JSON NOT NULL COMMENT 'Array of arms {creativeId, attributes, allocation} varying exactly one attribute axis',
    readout JSON NULL COMMENT 'Computed readout {perVariant array + winner + pValue + significant}; NULL until first measured',
    started_at DATETIME NULL COMMENT 'When the experiment started rotating live (RUNNING); NULL while DESIGNED',
    ended_at DATETIME NULL COMMENT 'When the experiment reached a terminal readout; NULL while DESIGNED/RUNNING',
    created_by BIGINT UNSIGNED NULL COMMENT 'ID of the user who created this row',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    updated_by BIGINT UNSIGNED NULL COMMENT 'ID of the user who updated this row',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (id),
    KEY idx_exp_campaign (client_code, campaign_plan_id, status))
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
