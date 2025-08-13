DROP TABLE IF EXISTS `message`.`message_whatsapp_messages`;

CREATE TABLE `message`.`message_whatsapp_messages` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code related to this WhatsApp message.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code related to this WhatsApp message.',
    `USER_ID` BIGINT UNSIGNED NULL COMMENT 'ID of the user associated with this WhatsApp message.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',

    `MESSAGE_ID` VARCHAR(255) NULL COMMENT 'WhatsApp message ID.',

    `WHATSAPP_PHONE_NUMBER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'ID of the associated Business phone number.',
    `FROM_DIAL_CODE` SMALLINT NOT NULL DEFAULT 91 COMMENT 'Dial code of the sender\'s phone number.',
    `FROM_PHONE` CHAR(15) NULL COMMENT 'Phone number of the sender.',
    `TO_DIAL_CODE` SMALLINT NOT NULL DEFAULT 91 COMMENT 'Dial code of the recipient\'s phone number.',
    `TO_PHONE` CHAR(15) NULL COMMENT 'Phone number of the recipient.',

    `MESSAGE_TYPE` ENUM (
        'AUDIO',
        'BUTTON',
        'CONTACTS',
        'DOCUMENT',
        'LOCATION',
        'TEXT',
        'TEMPLATE',
        'IMAGE',
        'INTERACTIVE',
        'ORDER',
        'REACTION',
        'STICKER',
        'SYSTEM',
        'UNKNOWN',
        'VIDEO',
        'UNSUPPORTED'
        ) NOT NULL DEFAULT 'TEXT' COMMENT 'Type of the message (TEXT, IMAGE, VIDEO, etc.).',

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
    INDEX `IDX1_WHATSAPP_MESSAGES_MESSAGE_ID` (`MESSAGE_ID`),
    INDEX `IDX2_WHATSAPP_MESSAGES_FROM_PHONE` (`FROM_DIAL_CODE`, `FROM_PHONE`),
    INDEX `IDX3_WHATSAPP_MESSAGES_TO_PHONE` (`TO_DIAL_CODE`, `TO_PHONE`),
    INDEX `IDX4_WHATSAPP_MESSAGES_MESSAGE_STATUS` (`MESSAGE_STATUS`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `message`.`message_whatsapp_templates`;

CREATE TABLE `message`.`message_whatsapp_templates` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code related to this WhatsApp template.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code related to this WhatsApp template.',
    `USER_ID` BIGINT UNSIGNED NULL COMMENT 'ID of the user associated with this WhatsApp template.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',

    `WHATSAPP_BUSINESS_ACCOUNT_ID` VARCHAR(255) NOT NULL COMMENT 'WhatsApp Business Account ID.',
    `TEMPLATE_ID` CHAR(255) NULL COMMENT 'WhatsApp template ID from Meta.',
    `TEMPLATE_NAME` VARCHAR(512) NOT NULL COMMENT 'Name of the WhatsApp template.',
    `ALLOW_CATEGORY_CHANGE` TINYINT NULL COMMENT 'Indicates whether category change is allowed for this template.',
    `CATEGORY` ENUM ('AUTHENTICATION', 'UTILITY', 'MARKETING') NOT NULL COMMENT 'Category of the template (MARKETING, UTILITY, AUTHENTICATION).',
    `SUB_CATEGORY` ENUM ('ORDER_DETAILS', 'ORDER_STATUS') NULL COMMENT 'Sub-category of the template.',
    `MESSAGE_SEND_TTL_SECONDS` BIGINT UNSIGNED NULL COMMENT 'Time-to-live for message sending in seconds.',
    `PARAMETER_FORMAT` ENUM ('NAMED', 'POSITIONAL') NOT NULL DEFAULT 'POSITIONAL' COMMENT 'Format for template parameters.',
    `LANGUAGE` CHAR(10) NOT NULL COMMENT 'Language code of the template.',
    `STATUS` ENUM ('APPROVED', 'IN_APPEAL', 'PENDING', 'REJECTED', 'PENDING_DELETION', 'DELETED', 'DISABLED', 'PAUSED', 'LIMIT_EXCEEDED') NULL COMMENT 'Status of the template.',
    `REJECTED_REASON` ENUM ('ABUSIVE_CONTENT', 'INVALID_FORMAT', 'NONE', 'PROMOTIONAL', 'TAG_CONTENT_MISMATCH', 'SCAM') NULL COMMENT 'Reason for template rejection.',
    `PREVIOUS_CATEGORY` ENUM ('AUTHENTICATION', 'UTILITY', 'MARKETING') NULL COMMENT 'Previous category of the template.',
    `MONTHLY_EDIT_COUNT` INT DEFAULT 0 NOT NULL COMMENT 'Count of edit done in this month.',
    `COMPONENTS` JSON NULL COMMENT 'Template components in JSON format.',

    `CREATED_BY` BIGINT UNSIGNED NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Indicates whether this row is active.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_MESSAGE_WHATSAPP_TEMPLATES_CODE` (`CODE`),
    UNIQUE KEY `UK2_MESSAGE_WHATSAPP_TEMPLATES_TEMPLATE_ID` (`TEMPLATE_ID`)
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci` COMMENT ='WhatsApp message templates';

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
    `WHATSAPP_TEMPLATE_ID` BIGINT UNSIGNED NULL COMMENT 'ID of the associated WhatsApp template.',

    `METADATA` JSON NULL COMMENT 'Additional metadata related to the message.',

    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this message is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this record was created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this record was last updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_MESSAGES_CODE` (`CODE`),
    INDEX `IDX1_MESSAGES_FROM_PHONE` (`FROM_DIAL_CODE`, `FROM_PHONE`),
    INDEX `IDX2_MESSAGES_TO_PHONE` (`TO_DIAL_CODE`, `TO_PHONE`),
    INDEX `IDX3_WHATSAPP_MESSAGES_MESSAGE_STATUS` (`MESSAGE_STATUS`),
    CONSTRAINT `FK1_MESSAGES_WHATSAPP_MESSAGES_ID` FOREIGN KEY (`WHATSAPP_MESSAGE_ID`)
        REFERENCES `message_whatsapp_messages` (`ID`)
        ON DELETE SET NULL
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_MESSAGES_WHATSAPP_TEMPLATES_ID` FOREIGN KEY (`WHATSAPP_TEMPLATE_ID`)
        REFERENCES `message_whatsapp_templates` (`ID`)
        ON DELETE SET NULL
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

CREATE TABLE `message`.`message_whatsapp_phone_number` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code related to this WhatsApp phone number.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code related to this WhatsApp phone number.',
    `USER_ID` BIGINT UNSIGNED NULL COMMENT 'ID of the user associated with this WhatsApp phone number.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',

    `DISPLAY_PHONE_NUMBER` CHAR(20) NULL COMMENT 'Display phone number for WhatsApp Business.',
    `QUALITY_RATING` ENUM ('GREEN', 'YELLOW', 'RED', 'NA', 'UNKNOWN') NULL COMMENT 'Quality rating of the phone number.',
    `VERIFIED_NAME` VARCHAR(255) NULL COMMENT 'Verified name associated with the phone number.',
    `PHONE_NUMBER_ID` VARCHAR(255) NULL COMMENT 'WhatsApp phone number ID from Meta.',
    `CODE_VERIFICATION_STATUS` ENUM ('VERIFIED', 'NOT_VERIFIED', 'EXPIRED') NULL COMMENT 'Status of code verification.',
    `NAME_STATUS` ENUM ('APPROVED', 'AVAILABLE_WITHOUT_REVIEW', 'DECLINED', 'EXPIRED', 'PENDING_REVIEW', 'NONE') NULL COMMENT 'Status of the verified name.',
    `PLATFORM_TYPE` ENUM ('CLOUD_API', 'ON_PREMISE', 'NOT_APPLICABLE') NULL COMMENT 'Platform type for WhatsApp Business.',
    `THROUGHPUT_LEVEL_TYPE` ENUM ('STANDARD', 'HIGH', 'NOT_APPLICABLE') NULL COMMENT 'Throughput level for message sending.',
    `IS_DEFAULT` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to indicate if this is the default phone number.',

    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this phone number is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this record was created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this record was last updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_WHATSAPP_PHONE_NUMBER_CODE` (`CODE`),
    UNIQUE KEY `UK2_WHATSAPP_PHONE_NUMBER_PHONE_NUMBER_ID` (`PHONE_NUMBER_ID`),
    INDEX `IDX3_WHATSAPP_PHONE_NUMBER_IS_DEFAULT` (`IS_DEFAULT`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci` COMMENT = 'WhatsApp Business phone numbers';

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
    `WHATSAPP_TEMPLATE_ID` BIGINT UNSIGNED NULL COMMENT 'ID of the associated WhatsApp template.',

    `METADATA` JSON NULL COMMENT 'Additional metadata related to the message.',

    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this message is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this record was created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this record was last updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_MESSAGES_CODE` (`CODE`),
    INDEX `IDX1_MESSAGES_FROM_PHONE` (`FROM_DIAL_CODE`, `FROM_PHONE`),
    INDEX `IDX2_MESSAGES_TO_PHONE` (`TO_DIAL_CODE`, `TO_PHONE`),
    INDEX `IDX3_WHATSAPP_MESSAGES_MESSAGE_STATUS` (`MESSAGE_STATUS`),
    CONSTRAINT `FK1_MESSAGES_WHATSAPP_MESSAGES_ID` FOREIGN KEY (`WHATSAPP_MESSAGE_ID`)
        REFERENCES `message_whatsapp_messages` (`ID`)
        ON DELETE SET NULL
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_MESSAGES_WHATSAPP_TEMPLATES_ID` FOREIGN KEY (`WHATSAPP_TEMPLATE_ID`)
        REFERENCES `message_whatsapp_templates` (`ID`)
        ON DELETE SET NULL
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

