CREATE TABLE `entity_processor`.`entity_processor_diagnostics` (
    `ID`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`        CHAR(64) NOT NULL COMMENT 'App Code.',
    `CLIENT_CODE`     CHAR(8) NOT NULL COMMENT 'Client Code.',
    `OBJECT_TYPE`     ENUM('TICKET','PRODUCT','PRODUCT_TEMPLATE') NOT NULL COMMENT 'Type of the entity.',
    `OBJECT_ID`       BIGINT UNSIGNED NOT NULL COMMENT 'ID of the entity.',
    `ACTION`          VARCHAR(128) NOT NULL COMMENT 'Diagnostic action performed.',
    `OLD_VALUE`       VARCHAR(512) NULL COMMENT 'Previous value.',
    `NEW_VALUE`       VARCHAR(512) NULL COMMENT 'New value.',
    `REASON`          TEXT NULL COMMENT 'Reason for the change.',
    `ACTOR_ID`        BIGINT UNSIGNED NULL COMMENT 'User who triggered the change.',
    `META_DATA`       JSON NULL COMMENT 'Additional context as JSON.',
    `CREATED_BY`      BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    PRIMARY KEY (`ID`),
    INDEX `IDX1_DIAG_APP_CLIENT` (`APP_CODE`, `CLIENT_CODE`),
    INDEX `IDX2_DIAG_OBJECT` (`OBJECT_TYPE`, `OBJECT_ID`),
    INDEX `IDX3_DIAG_ACTION` (`ACTION`),
    INDEX `IDX4_DIAG_CREATED_AT` (`CREATED_AT`)
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
