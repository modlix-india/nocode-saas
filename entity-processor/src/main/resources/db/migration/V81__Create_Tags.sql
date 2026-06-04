CREATE TABLE `entity_processor`.`entity_processor_tags`
(
    `ID`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`      CHAR(64)        NOT NULL COMMENT 'App Code.',
    `CLIENT_CODE`   CHAR(8)         NOT NULL COMMENT 'Client Code.',
    `NAME`          VARCHAR(512)    NOT NULL COMMENT 'Display name of the tag.',
    `IS_ACTIVE`     TINYINT         NOT NULL DEFAULT 1 COMMENT 'Flag to check if this tag is active or not.',
    `CREATED_BY`    BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`    BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_TAGS_AC_CC_NAME` (`APP_CODE`, `CLIENT_CODE`, `NAME`),
    INDEX `IDX1_TAGS_AC_CC` (`APP_CODE`, `CLIENT_CODE`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

ALTER TABLE `entity_processor`.`entity_processor_tickets`
  MODIFY COLUMN `TAG` VARCHAR(32) DEFAULT NULL COMMENT 'Ticket Tag';
