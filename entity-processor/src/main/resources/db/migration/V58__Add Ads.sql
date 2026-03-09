USE `entity_processor`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_ads`;

CREATE TABLE entity_processor_ads
(
    `ID`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `CODE`        CHAR(22)        NOT NULL COMMENT 'Unique Code to identify this row.',
    `APP_CODE`    CHAR(64)        NOT NULL COMMENT 'AppCode on which this ad created.',
    `CLIENT_CODE` CHAR(8)         NOT NULL COMMENT 'ClientCode on which this ad created.',
    `AD_ID`       CHAR(32)        NOT NULL COMMENT 'External Ad Id.',
    `AD_NAME`     VARCHAR(256)             DEFAULT NULL COMMENT 'External Ad Name.',
    `ADSET_ID`    BIGINT UNSIGNED NOT NULL COMMENT 'Adset this ad belongs to.',
    `CAMPAIGN_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Campaign this ad belongs to (denormalized).',
    `TEMP_ACTIVE` TINYINT         NOT NULL DEFAULT 0 COMMENT 'Temporary active flag.',
    `IS_ACTIVE`   TINYINT         NOT NULL DEFAULT 1 COMMENT 'Flag to check if this ad is active or not.',
    `CREATED_BY`  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_ADS_ID` (`APP_CODE`, `CLIENT_CODE`, `AD_ID`),
    UNIQUE KEY `UK2_ADS_CODE` (`CODE`),
    INDEX `IDX_ADS_APP_CLIENT` (`APP_CODE`, `CLIENT_CODE`),
    CONSTRAINT `FK1_ADS_ADSET_ID` FOREIGN KEY (`ADSET_ID`)
        REFERENCES `entity_processor`.`entity_processor_adsets` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_ADS_CAMPAIGN_ID` FOREIGN KEY (`CAMPAIGN_ID`)
        REFERENCES `entity_processor`.`entity_processor_campaigns` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
