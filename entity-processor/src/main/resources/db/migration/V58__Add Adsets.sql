USE
`entity_processor`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_adsets`;

CREATE TABLE entity_processor_adsets
(
    `ID`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `CODE`        CHAR(22)  NOT NULL COMMENT 'Unique Code to identify this row.',
    `APP_CODE`    CHAR(64)  NOT NULL COMMENT 'AppCode on which this adset created.',
    `CLIENT_CODE` CHAR(8)   NOT NULL COMMENT 'ClientCode on which this adset created.',
    `ADSET_ID`    CHAR(32)  NOT NULL COMMENT 'External Adset Id.',
    `ADSET_NAME`  VARCHAR(256)       DEFAULT NULL COMMENT 'External Adset Name.',
    `CAMPAIGN_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Campaign this adset belongs to.',
    `TEMP_ACTIVE` TINYINT   NOT NULL DEFAULT 0 COMMENT 'Temporary active flag.',
    `IS_ACTIVE`   TINYINT   NOT NULL DEFAULT 1 COMMENT 'Flag to check if this adset is active or not.',
    `CREATED_BY`  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_ADSETS_ID` (`APP_CODE`, `CLIENT_CODE`, `ADSET_ID`),
    UNIQUE KEY `UK2_ADSETS_CODE` (`CODE`),
    INDEX         `IDX_ADSETS_APP_CLIENT` (`APP_CODE`, `CLIENT_CODE`),
    CONSTRAINT `FK1_ADSETS_CAMPAIGN_ID` FOREIGN KEY (`CAMPAIGN_ID`)
        REFERENCES `entity_processor`.`entity_processor_campaigns` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
