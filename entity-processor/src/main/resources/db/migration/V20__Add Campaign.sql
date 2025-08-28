USE `entity_processor`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_campaigns`;

CREATE TABLE entity_processor_campaigns
(
    `ID`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `CODE`            CHAR(22)        NOT NULL COMMENT 'Unique Code to identify this row.',
    `APP_CODE`        CHAR(64)        NOT NULL COMMENT 'AppCode on which this campaign created.',
    `CLIENT_CODE`     CHAR(8)         NOT NULL COMMENT 'ClientCode on which this campaign created',
    `CAMPAIGN_ID`     CHAR(32)        NOT NULL COMMENT 'Campaign Id',
    `CAMPAIGN_NAME`   VARCHAR(128)    NOT NULL COMMENT 'Campaign Name',
    `CAMPAIGN_TYPE`   VARCHAR(32)              DEFAULT NULL COMMENT 'Campaign Type',
    `CAMPAIGN_SOURCE` VARCHAR(32)              DEFAULT NULL COMMENT 'Campaign Source',
    `PRODUCT_ID`      BIGINT UNSIGNED NOT NULL COMMENT 'Product Id campaign belongs to.',
    `TEMP_ACTIVE`     TINYINT         NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this campaign.',
    `IS_ACTIVE`       TINYINT         NOT NULL DEFAULT 1 COMMENT 'Flag to check if this campaign is active or not.',
    `CREATED_BY`      BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`      BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_CAMPAIGNS_ID` (`APP_CODE`, `CLIENT_CODE`, `CAMPAIGN_ID`),
    CONSTRAINT `FK1_CAMPAIGNS_PRODUCT_ID` FOREIGN KEY (`PRODUCT_ID`)
        REFERENCES `entity_processor`.`entity_processor_products` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;