/* Creating Database */

DROP DATABASE IF EXISTS `notification`;

CREATE DATABASE IF NOT EXISTS `notification` DEFAULT CHARACTER SET `UTF8MB4` COLLATE `UTF8MB4_UNICODE_CI`;

USE `notification`;

DROP TABLE IF EXISTS `notification`.`notification_connection`;

CREATE TABLE `notification`.`notification_connection` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the client. References security_client table',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application. References security_app table',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row',
    `NAME` CHAR(125) NOT NULL COMMENT 'Connection name',
    `DESCRIPTION` TEXT DEFAULT NULL COMMENT 'Description of notification connection',
    `CHANNEL_TYPE` ENUM ('DISABLED', 'EMAIL', 'IN_APP', 'MOBILE_PUSH', 'WEB_PUSH', 'SMS') NOT NULL COMMENT 'Type of notification channel',

    `CONNECTION_DETAILS` JSON NOT NULL COMMENT 'Connection details object',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_CONNECTION_CODE` (`CODE`),
    INDEX `IDX1_CONNECTION_CODE_CLIENT_ID_APP_ID` (`CODE`, `CLIENT_ID`, `APP_ID`),
    INDEX `IDX2_CONNECTION_CLIENT_ID_APP_ID` (`CLIENT_ID`, `APP_ID`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `notification`.`notification_template`;

CREATE TABLE `notification`.`notification_template` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the client. References security_client table',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application. References security_app table',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row',
    `NAME` CHAR(125) NOT NULL COMMENT 'Template name',
    `DESCRIPTION` TEXT DEFAULT NULL COMMENT 'Description of notification Template',

    `CHANNEL_TYPE` ENUM ('DISABLED', 'EMAIL', 'IN_APP', 'MOBILE_PUSH', 'WEB_PUSH', 'SMS') NOT NULL COMMENT 'Type of notification channel',
    `TEMPLATE_PARTS` JSON NOT NULL COMMENT 'Notification Template parts object',
    `RESOURCES` JSON NOT NULL COMMENT 'Notification resources object',
    `VARIABLES` JSON NOT NULL COMMENT 'Variables for Template',
    `TEMPLATE_TYPE` CHAR(36) NOT NULL COMMENT 'Type of template',
    `DEFAULT_LANGUAGE` CHAR(36) NOT NULL COMMENT 'The default language for this template',
    `LANGUAGE_EXPRESSION` VARCHAR(255) NULL COMMENT 'Language Expression for this template',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_TEMPLATE_CODE` (`CODE`),
    INDEX `IDX1_TEMPLATE_CODE_CLIENT_ID_APP_ID` (`CODE`, `CLIENT_ID`, `APP_ID`),
    INDEX `IDX2_TEMPLATE_CLIENT_ID_APP_ID` (`CLIENT_ID`, `APP_ID`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `notification`.`notification_notification`;

CREATE TABLE `notification`.`notification_notification` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the client. References security_client table',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application. References security_app table',
    `USER_ID` BIGINT UNSIGNED NULL COMMENT 'Identifier for the application. References security_app table',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row',
    `NOTIFICATION_TYPE` ENUM ('ALERT', 'BULK', 'INFO', 'WARNING', 'ERROR', 'SUCCESS', 'REMINDER', 'SCHEDULED', 'SYSTEM', 'PROMOTIONAL', 'UPDATE', 'SECURITY') NOT NULL COMMENT 'Type of notification',

    `CHANNEL_DETAILS` JSON NOT NULL COMMENT 'Notification details per channel',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_NOTIFICATION_CODE` (`CODE`),
    INDEX `IDX1_NOTIFICATION_CODE_NOTI_TYPE` (`CODE`, `NOTIFICATION_TYPE`),
    INDEX `IDX1_NOTIFICATION_CLIENT_ID_APP_ID_NOTI_TYPE` (`CLIENT_ID`, `APP_ID`, `NOTIFICATION_TYPE`),
    INDEX `IDX2_NOTIFICATION_APP_ID_USER_ID_NOTI_TYPE` (`APP_ID`, `USER_ID`, `NOTIFICATION_TYPE`),
    INDEX `IDX3_NOTIFICATION_CLIENT_ID_APP_ID_USER_ID_NOTI_TYPE` (`CLIENT_ID`, `APP_ID`, `USER_ID`, `NOTIFICATION_TYPE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `notification`.`notification_user_preference`;

CREATE TABLE `notification`.`notification_user_preference` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application. References security_app table',
    `USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the user. References security_user table',
    `NOTIFICATION_TYPE` ENUM ('ALERT', 'BULK', 'INFO', 'WARNING', 'ERROR', 'SUCCESS', 'REMINDER', 'SCHEDULED', 'SYSTEM', 'PROMOTIONAL', 'UPDATE', 'SECURITY') NOT NULL COMMENT 'Type of notification',
    `PREFERENCES` JSON NOT NULL COMMENT 'Notification user preferences',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_USER_PREF_APP_ID_USER_ID_NOTI_TYPE` (`APP_ID`, `USER_ID`, `NOTIFICATION_TYPE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `notification`.`notification_app_preference`;

CREATE TABLE `notification`.`notification_app_preference` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the client. References security_client table',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application. References security_app table',
    `NOTIFICATION_TYPE` ENUM ('ALERT', 'BULK', 'INFO', 'WARNING', 'ERROR', 'SUCCESS', 'REMINDER', 'SCHEDULED', 'SYSTEM', 'PROMOTIONAL', 'UPDATE', 'SECURITY') NOT NULL COMMENT 'Type of notification',
    `PREFERENCES` JSON NOT NULL COMMENT 'Notification app preferences',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_APP_PREF_CLIENT_ID_APP_ID_NOTI_TYPE` (`CLIENT_ID`, `APP_ID`, `NOTIFICATION_TYPE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
