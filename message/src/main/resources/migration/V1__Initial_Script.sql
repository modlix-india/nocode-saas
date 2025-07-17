-- DROP DATABASE IF EXISTS `message`;

CREATE DATABASE IF NOT EXISTS `message` DEFAULT CHARACTER SET `UTF8MB4` COLLATE `UTF8MB4_UNICODE_CI`;

USE `message`;

DROP TABLE IF EXISTS `message`.`message_exotel_calls`;

CREATE TABLE `message`.`message_exotel_calls` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code related to this Exotel Call.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code related to this Exotel Call.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `SID` CHAR(32) NOT NULL COMMENT 'Unique identifier for the call.',
    `PARENT_CALL_SID` CHAR(32) NULL COMMENT 'Identifier for the parent call, if it exists.',
    `ACCOUNT_SID` CHAR(50) NOT NULL COMMENT 'Exotel account SID for this call.',
    `FROM_DIAL_CODE` SMALLINT NOT NULL DEFAULT 91 COMMENT 'Dial code of the caller\'s phone number.',
    `FROM_PHONE` CHAR(15) NOT NULL COMMENT 'Phone number of the caller.',
    `TO_DIAL_CODE` SMALLINT NOT NULL DEFAULT 91 COMMENT 'Dial code of the receiver\'s phone number.',
    `TO_PHONE` CHAR(15) NOT NULL COMMENT 'Phone number of the receiver.',
    `CALLER_ID` CHAR(50) NULL COMMENT 'Caller ID configured in Exotel.',
    `EXOTEL_CALL_STATUS` ENUM ('NULL', 'COMPLETED', 'BUSY', 'FAILED', 'NO_ANSWER', 'CANCELED') DEFAULT 'NULL' NOT NULL COMMENT 'Status of the call.',
    `START_TIME` DATETIME NULL COMMENT 'Timestamp when the call was initiated.',
    `END_TIME` DATETIME NULL COMMENT 'Timestamp when the call ended.',
    `DURATION` BIGINT NULL COMMENT 'Duration of the call in seconds.',
    `PRICE` DECIMAL(12, 2) NULL COMMENT 'Cost of the call.',
    `DIRECTION` VARCHAR(50) NULL COMMENT 'Direction of the call (e.g., inbound or outbound).',
    `ANSWERED_BY` VARCHAR(255) DEFAULT 'human' NULL COMMENT 'Person or system that answered the call.',
    `RECORDING_URL` VARCHAR(2083) NULL COMMENT 'URL of the call recording, if available.',
    `CONVERSATION_DURATION` BIGINT NULL COMMENT 'Conversation duration of the call in seconds.',
    `LEG1_STATUS` ENUM ('NULL', 'COMPLETED', 'BUSY', 'FAILED', 'NO_ANSWER', 'CANCELED') DEFAULT 'NULL' NOT NULL COMMENT 'Status of the first leg of the call.',
    `LEG2_STATUS` ENUM ('NULL', 'COMPLETED', 'BUSY', 'FAILED', 'NO_ANSWER', 'CANCELED') DEFAULT 'NULL' NOT NULL COMMENT 'Status of the second leg of the call.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this call is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this record was created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this record was last updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_EXOTEL_CALLS_CODE` (`CODE`),
    UNIQUE KEY `UK2_EXOTEL_CALLS_SID` (SID)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `message`.`message_calls`;

CREATE TABLE `message`.`message_calls` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code related to this Call.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code related to this Call.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `USER_ID` BIGINT UNSIGNED NULL COMMENT 'ID of the user associated with this Call.',
    `FROM_DIAL_CODE` SMALLINT NOT NULL DEFAULT 91 COMMENT 'Dial code of the caller\'s phone number.',
    `FROM_PHONE` CHAR(15) NOT NULL COMMENT 'Phone number of the caller.',
    `TO_DIAL_CODE` SMALLINT NOT NULL DEFAULT 91 COMMENT 'Dial code of the receiver\'s phone number.',
    `TO_PHONE` CHAR(15) NOT NULL COMMENT 'Phone number of the receiver.',
    `CALLER_ID` CHAR(50) NULL COMMENT 'Caller ID configured for this call.',
    `CONNECTION_NAME` VARCHAR(255) NULL COMMENT 'Name of the connection used for the call.',
    `CALL_PROVIDER` VARCHAR(255) NULL COMMENT 'Name of the call provider (e.g., Exotel or similar).',
    `IS_OUTBOUND` BOOLEAN NOT NULL COMMENT 'Indicates whether the call is outbound.',
    `CALL_STATUS` ENUM ('UNKNOWN', 'ORIGINATE', 'FAILED', 'BUSY', 'NO_ANSWER', 'CALL_COMPLETE', 'INSUFFICIENT_BALANCE', 'CANCELED') NOT NULL COMMENT 'Status of the call.',
    `START_TIME` DATETIME NULL COMMENT 'Timestamp when the call started.',
    `END_TIME` DATETIME NULL COMMENT 'Timestamp when the call ended.',
    `DURATION` BIGINT NULL COMMENT 'Duration of the call in seconds.',
    `RECORDING_URL` VARCHAR(2083) NULL COMMENT 'URL of the call recording.',
    `EXOTEL_CALL_ID` BIGINT UNSIGNED NULL COMMENT 'ID of the associated Exotel call.',
    `METADATA` JSON NULL COMMENT 'Additional metadata related to the call.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this Call is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this record was created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this record was last updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_CALLS_CODE` (`CODE`),
    CONSTRAINT `FK1_CALLS_EXOTEL_CALLS_ID` FOREIGN KEY (`EXOTEL_CALL_ID`)
        REFERENCES `message_exotel_calls` (`ID`)
        ON DELETE SET NULL
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
