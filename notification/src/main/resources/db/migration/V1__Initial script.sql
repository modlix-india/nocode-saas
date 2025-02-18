/* Creating Database */

DROP DATABASE IF EXISTS `notification`;

CREATE DATABASE IF NOT EXISTS `notification` DEFAULT CHARACTER SET `UTF8MB4` COLLATE `UTF8MB4_UNICODE_CI`;

USE `notification`;

DROP TABLE IF EXISTS `notification`.`notification_user_preferences`;

CREATE TABLE `notification`.`notification_user_preferences` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application. References security_app table',
    `USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the user. References security_user table',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row',

    `PREFERENCES_JSON` JSON NULL COMMENT 'Notification preference',
    `ENABLED` TINYINT NOT NULL DEFAULT 1 COMMENT 'Notification enabled or not',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_USER_PREF_CODE` (`CODE`),
    UNIQUE KEY `UK2_USER_NOTI_PREF_APP_ID_USER_ID_NAME` (`APP_ID`, `USER_ID`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
