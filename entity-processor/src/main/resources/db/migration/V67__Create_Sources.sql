DROP TABLE IF EXISTS `entity_processor`.`entity_processor_sources`;

CREATE TABLE `entity_processor`.`entity_processor_sources`
(
    `ID`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`      CHAR(64)        NOT NULL COMMENT 'App Code.',
    `CLIENT_CODE`   CHAR(8)         NOT NULL COMMENT 'Client Code.',
    `NAME`          VARCHAR(512)    NOT NULL COMMENT 'Display name of the source or sub-source.',
    `PARENT_ID`     BIGINT UNSIGNED NULL COMMENT 'NULL for top-level sources; points to parent source ID for sub-sources.',
    `DISPLAY_ORDER` INT             NOT NULL DEFAULT 0 COMMENT 'Ordering within the same parent.',
    `IS_ACTIVE`     TINYINT         NOT NULL DEFAULT 1 COMMENT 'Flag to check if this source is active or not.',
    `CREATED_BY`    BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`    BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_SOURCES_AC_CC_PID_NAME` (`APP_CODE`, `CLIENT_CODE`, `PARENT_ID`, `NAME`),
    INDEX `IDX1_SOURCES_AC_CC` (`APP_CODE`, `CLIENT_CODE`),
    CONSTRAINT `FK1_SOURCES_PARENT` FOREIGN KEY (`PARENT_ID`)
        REFERENCES `entity_processor_sources` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
