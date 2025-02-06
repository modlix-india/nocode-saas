/* Creating Database */

-- DROP DATABASE IF EXISTS `notification`;

CREATE DATABASE IF NOT EXISTS `notification` DEFAULT CHARACTER SET `UTF8MB4` COLLATE `UTF8MB4_UNICODE_CI`;

USE `notification`;

DROP TABLE IF EXISTS `notification`.`notification_type`;

CREATE TABLE `notification`.`notification_type` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the client to which this OTP policy belongs. References security_client table',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application to which this OTP policy belongs. References security_app table',
    `CODE` CHAR(36) NOT NULL COMMENT 'Code',
    `NAME` CHAR(125) NOT NULL COMMENT 'Notification type name',
    `DESCRIPTION` TEXT DEFAULT NULL COMMENT 'Description of notification type',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_NOTIFICATION_TYPE_CODE_CLIENT_ID_APP_ID` (`CODE`, `CLIENT_ID`, `APP_ID`),
    INDEX `IDX1_NOTIFICATION_TYPE_CLIENT_ID_APP_ID` (`CLIENT_ID`, `APP_ID`),
    INDEX `IDX2_NOTIFICATION_TYPE_APP_ID` (`APP_ID`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `notification`.`notification_connection`;

CREATE TABLE `notification`.`notification_connection` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the client to which this OTP policy belongs. References security_client table',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application to which this OTP policy belongs. References security_app table',
    `CODE` CHAR(36) NOT NULL COMMENT 'Code',
    `NAME` CHAR(125) NOT NULL COMMENT 'Connection name',
    `DESCRIPTION` TEXT DEFAULT NULL COMMENT 'Description of notification connection',
    `CONNECTION_DETAILS` JSON DEFAULT '{}' NOT NULL COMMENT 'Connection details object',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_NOTIFICATION_CONN_CODE_CLIENT_ID_APP_ID` (`CODE`, `CLIENT_ID`, `APP_ID`),
    INDEX `IDX1_NOTIFICATION_CONN_CLIENT_ID_APP_ID` (`CLIENT_ID`, `APP_ID`),
    INDEX `IDX2_NOTIFICATION_CONN_APP_ID` (`APP_ID`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `notification`.`notification_user_preference`;

CREATE TABLE `notification`.`notification_user_preference` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Application identifier',
    `USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User identifier',
    `NOTIFICATION_TYPE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Reference to notification type',

    `IS_DISABLED` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to disable all notifications for this type',
    `IS_EMAIL_ENABLED` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to enable email notifications',
    `IS_IN_APP_ENABLED` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to enable in-app notifications',
    `IS_SMS_ENABLED` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to enable SMS notifications',
    `IS_PUSH_ENABLED` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to enable push notifications',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_USER_PREFERENCE_APP_ID_USER_ID_NOTI_TYPE` (`APP_ID`, `USER_ID`, `NOTIFICATION_TYPE_ID`),
    CONSTRAINT `FK1_USER_PREF_NOTIFICATION_TYPE` FOREIGN KEY (`NOTIFICATION_TYPE_ID`)
        REFERENCES `notification_type` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `notification`.`notification_app_preference`;

CREATE TABLE `notification`.`notification_app_preference` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Client identifier',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Application identifier',
    `NOTIFICATION_TYPE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Reference to notification type',

    `IS_DISABLED` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to disable all notifications for this type at app level',
    `IS_EMAIL_ENABLED` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to enable email notifications at app level',
    `IS_IN_APP_ENABLED` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to enable in-app notifications at app level',
    `IS_SMS_ENABLED` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to enable SMS notifications at app level',
    `IS_PUSH_ENABLED` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to enable push notifications at app level',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_USER_PREFERENCE_CLIENT_ID_APP_ID_NOTI_TYPE` (`CLIENT_ID`, `APP_ID`, `NOTIFICATION_TYPE_ID`),
    CONSTRAINT `FK1_APP_PREF_NOTIFICATION_TYPE` FOREIGN KEY (`NOTIFICATION_TYPE_ID`)
        REFERENCES `notification_type` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
