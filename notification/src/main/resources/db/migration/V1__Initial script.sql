/* Creating Database */

DROP DATABASE IF EXISTS `notification`;

CREATE DATABASE IF NOT EXISTS `notification` DEFAULT CHARACTER SET `UTF8MB4` COLLATE `UTF8MB4_UNICODE_CI`;

USE `notification`;

DROP TABLE IF EXISTS `notification`.`notification_user_notification_pref`;

CREATE TABLE `notification`.`notification_user_notification_pref` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application. References security_app table',
    `USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the user. References security_user table',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row',

    `NOTIFICATION_NAME` CHAR(125) NOT NULL COMMENT 'Notification name preference',
    `ENABLED` TINYINT NOT NULL DEFAULT 0 COMMENT 'Notification name enabled or not',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_USER_PREF_CODE` (`CODE`),
    UNIQUE KEY `UK2_USER_NOTI_PREF_APP_ID_USER_ID_NAME` (`APP_ID`, `USER_ID`, `NOTIFICATION_NAME`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `notification`.`notification_user_channel_pref`;

CREATE TABLE `notification`.`notification_user_channel_pref` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application. References security_app table',
    `USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the user. References security_user table',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row',

    `CHANNEL_TYPE` ENUM ('DISABLED', 'EMAIL', 'IN_APP', 'MOBILE_PUSH', 'WEB_PUSH', 'SMS') NOT NULL COMMENT 'Notification app preferences',
    `ENABLED` TINYINT NOT NULL DEFAULT 0 COMMENT 'Notification name enabled or not',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_APP_PREF_CODE` (`CODE`),
    UNIQUE KEY `UK1_USER_CHANNEL_PREF_USER_ID_APP_ID_CHANNEL` (`APP_ID`, `USER_ID`, `CHANNEL_TYPE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
