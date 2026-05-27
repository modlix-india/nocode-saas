USE `entity_processor`;

-- Many-to-many Campaign <-> Product join. Replaces the single
-- entity_processor_campaigns.PRODUCT_ID FK. The old column is retained
-- (made nullable) for one release cycle for backward compatibility.

CREATE TABLE IF NOT EXISTS `entity_processor`.`entity_processor_campaign_products`
(
    `CAMPAIGN_ID` BIGINT UNSIGNED NOT NULL COMMENT 'FK to entity_processor_campaigns.ID',
    `PRODUCT_ID`  BIGINT UNSIGNED NOT NULL COMMENT 'FK to entity_processor_products.ID',
    `APP_CODE`    CHAR(64)        NOT NULL COMMENT 'AppCode on which this link was created.',
    `CLIENT_CODE` CHAR(8)         NOT NULL COMMENT 'ClientCode on which this link was created.',
    `CREATED_BY`  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',

    PRIMARY KEY (`CAMPAIGN_ID`, `PRODUCT_ID`),
    CONSTRAINT `FK1_CP_CAMPAIGN` FOREIGN KEY (`CAMPAIGN_ID`)
        REFERENCES `entity_processor`.`entity_processor_campaigns` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_CP_PRODUCT` FOREIGN KEY (`PRODUCT_ID`)
        REFERENCES `entity_processor`.`entity_processor_products` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    INDEX `IDX1_CP_PRODUCT` (`PRODUCT_ID`),
    INDEX `IDX2_CP_AC_CC` (`APP_CODE`, `CLIENT_CODE`)
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

-- Backfill existing single-product associations into the join table.
INSERT INTO `entity_processor`.`entity_processor_campaign_products`
    (`CAMPAIGN_ID`, `PRODUCT_ID`, `APP_CODE`, `CLIENT_CODE`)
SELECT `ID`, `PRODUCT_ID`, `APP_CODE`, `CLIENT_CODE`
FROM `entity_processor`.`entity_processor_campaigns`
WHERE `PRODUCT_ID` IS NOT NULL;

-- Relax the legacy FK column so auto-discovered campaigns can exist with no
-- product link. DEPRECATED: holds the "primary" product only; new code reads
-- the join table.
ALTER TABLE `entity_processor`.`entity_processor_campaigns`
    DROP FOREIGN KEY `FK1_CAMPAIGNS_PRODUCT_ID`;

ALTER TABLE `entity_processor`.`entity_processor_campaigns`
    MODIFY `PRODUCT_ID` BIGINT UNSIGNED NULL
        COMMENT 'DEPRECATED. Migrated to entity_processor_campaign_products. Kept for backward compat; holds primary product only.';
