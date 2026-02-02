DROP TABLE IF EXISTS `message`.`message_message_webhooks`;

CREATE TABLE `message`.`message_message_webhooks` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code related to this message.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code related to this message.',
    `USER_ID` BIGINT UNSIGNED NULL COMMENT 'ID of the user associated with this message.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',

    `PROVIDER` CHAR(22) NOT NULL DEFAULT 'NONE' COMMENT 'Provider of the message.',
    `IS_PROCESSED` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to check if this message has been processed or not.',
    `EVENT` JSON NULL COMMENT 'Message Content',

    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this message is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this record was created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this record was last updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_MESSAGES_CODE` (`CODE`),
    INDEX `IDX1_MESSAGE_WEBHOOKS_PROVIDER` (`APP_CODE`, `CLIENT_CODE`, `PROVIDER`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
