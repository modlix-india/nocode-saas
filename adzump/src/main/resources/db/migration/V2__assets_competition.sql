/* V2 — additive: J16 assets/media + J19 competition research. Builds ON V1 (no drop). */

USE `adzump`;

CREATE TABLE IF NOT EXISTS `adzump`.`adzump_asset` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    client_code CHAR(8) NOT NULL COMMENT 'Client code (tenant-private)',
    kind ENUM('IMAGE','VIDEO','LOGO') NOT NULL COMMENT 'Asset medium kind',
    file_key VARCHAR(512) NOT NULL COMMENT 'Modlix files service reference (source of truth for the bytes)',
    url VARCHAR(1024) NULL COMMENT 'Resolvable files-service URL for the asset',
    attributes JSON NULL COMMENT 'A4/vision classification + attributes (theme, scene, dominant colour, logo/hero/amenity/floor-plan)',
    platform_ids JSON NULL COMMENT 'Per-platform registration cache, e.g. {META:{id,status,registeredAt},GOOGLE:{...}}',
    created_by BIGINT UNSIGNED NULL COMMENT 'ID of the user who created this row',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    updated_by BIGINT UNSIGNED NULL COMMENT 'ID of the user who updated this row',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (id),
    KEY idx_asset_client (client_code))
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `adzump`.`adzump_competition_research` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    client_code CHAR(8) NOT NULL COMMENT 'Client code (tenant-private)',
    product_id VARCHAR(64) NOT NULL COMMENT 'Product this research finding is for',
    vertical VARCHAR(48) NULL COMMENT 'Business vertical (open set, J5 registry-defined)',
    generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time this research finding was generated (append-only history key)',
    body JSON NOT NULL COMMENT 'Ranked competitor ads + proxy scores + extracted attributes (CompetitionResearchBody)',
    created_by BIGINT UNSIGNED NULL COMMENT 'ID of the user who created this row',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    updated_by BIGINT UNSIGNED NULL COMMENT 'ID of the user who updated this row',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (id),
    KEY idx_comp_product (client_code, product_id))
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
