/* Creating Database */

DROP DATABASE IF EXISTS `notification`;

CREATE DATABASE IF NOT EXISTS `notification` DEFAULT CHARACTER SET `UTF8MB4` COLLATE `UTF8MB4_UNICODE_CI`;

USE `notification`;

DROP TABLE IF EXISTS `notification`.`notification_user_preferences`;

CREATE TABLE `notification`.`notification_user_preferences` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'App Id for which this user preference is getting created. References security_app table',
    `USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'App User Id under which this user preference is getting created. References security_user table',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row',

    `PREFERENCES` JSON NULL COMMENT 'Notification preference',
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

DROP TABLE IF EXISTS `notification`.`notification_sent_notifications`;

CREATE TABLE `notification`.`notification_sent_notifications` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this notification was sent. References security_app table',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code to whom this notification we sent. References security_user table',
    `USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the user. References security_user table',

    `NOTIFICATION_MESSAGE` JSON NOT NULL COMMENT 'Notification message that is sent',
    `NOTIFICATION_TYPE` ENUM ('ALERT', 'BULK', 'INFO', 'WARNING', 'ERROR', 'SUCCESS', 'REMINDER', 'SCHEDULED', 'SYSTEM', 'PROMOTIONAL', 'UPDATE', 'SECURITY') NOT NULL DEFAULT 'INFO' COMMENT 'Type of notification that is sent',
    `NOTIFICATION_STAGE` ENUM ('PLATFORM', 'GATEWAY', 'NETWORK') NOT NULL DEFAULT 'PLATFORM' COMMENT 'Stage of the notification that is sent',
    `TRIGGER_TIME` TIMESTAMP NOT NULL COMMENT 'Time when the notification was triggered',

    `IS_EMAIL` TINYINT NOT NULL DEFAULT 0 COMMENT 'Email notification enabled or not',
    `EMAIL_DELIVERY_STATUS` JSON NULL COMMENT 'Email delivery status',
    `IS_IN_APP` TINYINT NOT NULL DEFAULT 0 COMMENT 'In-app notification enabled or not',
    `IN_APP_DELIVERY_STATUS` JSON NULL COMMENT 'In-app delivery status',
    `IS_MOBILE_PUSH` TINYINT NOT NULL DEFAULT 0 COMMENT 'Mobile push notification enabled or not',
    `MOBILE_PUSH_DELIVERY_STATUS` JSON NULL COMMENT 'Mobile push delivery status',
    `IS_WEB_PUSH` TINYINT NOT NULL DEFAULT 0 COMMENT 'Web push notification enabled or not',
    `WEB_PUSH_DELIVERY_STATUS` JSON NULL COMMENT 'Web push delivery status',
    `IS_SMS` TINYINT NOT NULL DEFAULT 0 COMMENT 'SMS notification enabled or not',
    `SMS_DELIVERY_STATUS` JSON NULL COMMENT 'SMS delivery status',
    `IS_ERROR` TINYINT NOT NULL DEFAULT 0 COMMENT 'If we are getting error in notification or not',
    `ERROR_MESSAGE` JSON NULL COMMENT 'Error message if error occurs during this notification',
    `CHANNEL_ERRORS` JSON NULL COMMENT 'Channel Errors if error occurs during channel listeners processing',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_SENT_NOTIFICATION_CODE` (`CODE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
