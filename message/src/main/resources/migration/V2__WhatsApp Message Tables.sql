DROP TABLE IF EXISTS `message`.`message_whatsapp_messages`;

CREATE TABLE `message`.`message_whatsapp_messages` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code related to this WhatsApp message.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code related to this WhatsApp message.',
    `USER_ID` BIGINT UNSIGNED NULL COMMENT 'ID of the user associated with this WhatsApp message.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',

    `MESSAGE_ID` VARCHAR(255) NULL COMMENT 'WhatsApp message ID.',

    `PHONE_NUMBER_ID` CHAR(20) NOT NULL COMMENT 'ID of the associated Business phone number.',
    `FROM_DIAL_CODE` SMALLINT NOT NULL DEFAULT 91 COMMENT 'Dial code of the sender\'s phone number.',
    `FROM_PHONE` CHAR(15) NULL COMMENT 'Phone number of the sender.',
    `TO_DIAL_CODE` SMALLINT NOT NULL DEFAULT 91 COMMENT 'Dial code of the recipient\'s phone number.',
    `TO_PHONE` CHAR(15) NULL COMMENT 'Phone number of the recipient.',

    `MESSAGE_TYPE` VARCHAR(50) NULL COMMENT 'Type of the message (TEXT, IMAGE, VIDEO, etc.).',

    `MESSAGE_STATUS` ENUM ('SENT', 'DELIVERED', 'READ', 'FAILED', 'DELETED') NOT NULL DEFAULT 'SENT' COMMENT 'Status of the message.',
    `SENT_TIME` DATETIME NULL COMMENT 'Timestamp when the message was sent.',
    `DELIVERED_TIME` DATETIME NULL COMMENT 'Timestamp when the message was delivered.',
    `READ_TIME` DATETIME NULL COMMENT 'Timestamp when the message was read.',
    `FAILED_TIME` DATETIME NULL COMMENT 'Timestamp when the message failed.',
    `FAILURE_REASON` TEXT NULL COMMENT 'Reason for message failure.',

    `IS_OUTBOUND` TINYINT NOT NULL DEFAULT 1 COMMENT 'Indicates whether the message is outbound.',

    `MESSAGE` JSON NULL COMMENT 'Entire Message object send by WhatsApp.',
    `MESSAGE_RESPONSE` JSON NULL COMMENT 'Entire Message Response object send by WhatsApp.',

    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this message is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this record was created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this record was last updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_WHATSAPP_MESSAGES_CODE` (`CODE`),
    INDEX `IDX_WHATSAPP_MESSAGES_MESSAGE_ID` (`MESSAGE_ID`),
    INDEX `IDX_WHATSAPP_MESSAGES_FROM_PHONE` (`FROM_DIAL_CODE`, `FROM_PHONE`),
    INDEX `IDX_WHATSAPP_MESSAGES_TO_PHONE` (`TO_DIAL_CODE`, `TO_PHONE`),
    INDEX `IDX_WHATSAPP_MESSAGES_MESSAGE_STATUS` (`MESSAGE_STATUS`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `message`.`message_messages`;

CREATE TABLE `message`.`message_messages` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code related to this message.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code related to this message.',
    `USER_ID` BIGINT UNSIGNED NULL COMMENT 'ID of the user associated with this message.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',

    `FROM_DIAL_CODE` SMALLINT NOT NULL DEFAULT 91 COMMENT 'Dial code of the sender\'s phone number.',
    `FROM_PHONE` CHAR(15) NULL COMMENT 'Phone number of the sender.',
    `TO_DIAL_CODE` SMALLINT NOT NULL DEFAULT 91 COMMENT 'Dial code of the recipient\'s phone number.',
    `TO_PHONE` CHAR(15) NULL COMMENT 'Phone number of the recipient.',

    `CONNECTION_NAME` VARCHAR(255) NULL COMMENT 'Name of the connection used for the message.',
    `MESSAGE_PROVIDER` CHAR(50) NULL COMMENT 'Name of the message provider (e.g., WhatsApp or similar).',
    `IS_OUTBOUND` TINYINT NOT NULL DEFAULT 1 COMMENT 'Indicates whether the message is outbound.',

    `MESSAGE_STATUS` ENUM ('SENT', 'DELIVERED', 'READ', 'FAILED', 'DELETED') NOT NULL DEFAULT 'SENT' COMMENT 'Status of the message.',
    `SENT_TIME` DATETIME NULL COMMENT 'Timestamp when the message was sent.',
    `DELIVERED_TIME` DATETIME NULL COMMENT 'Timestamp when the message was delivered.',
    `READ_TIME` DATETIME NULL COMMENT 'Timestamp when the message was read.',

    `WHATSAPP_MESSAGE_ID` BIGINT UNSIGNED NULL COMMENT 'ID of the associated WhatsApp message.',

    `METADATA` JSON NULL COMMENT 'Additional metadata related to the message.',

    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this message is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this record was created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this record was last updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_MESSAGES_CODE` (`CODE`),
    INDEX `IDX_MESSAGES_FROM_PHONE` (`FROM_DIAL_CODE`, `FROM_PHONE`),
    INDEX `IDX_MESSAGES_TO_PHONE` (`TO_DIAL_CODE`, `TO_PHONE`),
    INDEX `IDX_WHATSAPP_MESSAGES_MESSAGE_STATUS` (`MESSAGE_STATUS`),
    CONSTRAINT `FK1_MESSAGES_WHATSAPP_MESSAGES_ID` FOREIGN KEY (`WHATSAPP_MESSAGE_ID`)
        REFERENCES `message_whatsapp_messages` (`ID`)
        ON DELETE SET NULL
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
