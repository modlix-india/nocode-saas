/* Creating Database */

DROP DATABASE IF EXISTS `notification`;

CREATE DATABASE IF NOT EXISTS `notification` DEFAULT CHARACTER SET `UTF8MB4` COLLATE `UTF8MB4_UNICODE_CI`;

USE `notification`;

DROP TABLE IF EXISTS `notification`.`notification_type`;

CREATE TABLE `notification`.`notification_type` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the client. References security_client table',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application. References security_app table',
    `CODE` CHAR(22) NOT NULL COMMENT 'Code',
    `NAME` CHAR(125) NOT NULL COMMENT 'Notification type name',
    `DESCRIPTION` TEXT DEFAULT NULL COMMENT 'Description of notification type',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_NOTIFICATION_TYPE_CODE` (`CODE`),
    INDEX `IDX1_NOTIFICATION_TYPE_CLIENT_ID_APP_ID` (`CLIENT_ID`, `APP_ID`),
    INDEX `IDX2_NOTIFICATION_TYPE_APP_ID` (`APP_ID`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `notification`.`notification_connection`;

CREATE TABLE `notification`.`notification_connection` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the client. References security_client table',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application. References security_app table',
    `CODE` CHAR(22) NOT NULL COMMENT 'Code',
    `NAME` CHAR(125) NOT NULL COMMENT 'Connection name',
    `DESCRIPTION` TEXT DEFAULT NULL COMMENT 'Description of notification connection',
    `CONNECTION_DETAILS` JSON NOT NULL COMMENT 'Connection details object',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_NOTIFICATION_TYPE_CODE` (`CODE`),
    INDEX `IDX1_NOTIFICATION_CONN_CLIENT_ID_APP_ID` (`CLIENT_ID`, `APP_ID`),
    INDEX `IDX2_NOTIFICATION_CONN_APP_ID` (`APP_ID`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `notification`.`notification_user_preference`;

CREATE TABLE `notification`.`notification_user_preference` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application. References security_app table',
    `USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the user. References security_user table',
    `NOTIFICATION_TYPE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the notification type. References notification_type table',

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
    `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the client. References security_client table',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application. References security_app table',
    `NOTIFICATION_TYPE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the notification type. References notification_type table',

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

DROP TABLE IF EXISTS `notification`.`notification_template`;

CREATE TABLE `notification`.`notification_template` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the client. References security_client table',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application. References security_app table',
    `CODE` CHAR(22) NOT NULL COMMENT 'Code',
    `NAME` CHAR(125) NOT NULL COMMENT 'Template name',
    `DESCRIPTION` TEXT DEFAULT NULL COMMENT 'Description of notification Template',
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
    UNIQUE KEY `UK1_NOTIFICATION_TYPE_CODE` (`CODE`),
    INDEX `IDX1_NOTIFICATION_TEMPLATE_CLIENT_ID_APP_ID` (`CLIENT_ID`, `APP_ID`),
    INDEX `IDX2_NOTIFICATION_TEMPLATE_APP_ID` (`APP_ID`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `notification`.`notification_notification`;

CREATE TABLE `notification`.`notification_notification` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the client. References security_client table',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application. References security_app table',
    `CODE` CHAR(22) NOT NULL COMMENT 'Code',
    `NAME` CHAR(125) NOT NULL COMMENT 'Template name',
    `DESCRIPTION` TEXT DEFAULT NULL COMMENT 'Description of notification Template',
    `NOTIFICATION_TYPE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the notification type. References notification_type table',

    `EMAIL_TEMPLATE_ID` BIGINT UNSIGNED NULL COMMENT 'Identifier for the email template. References notification_template table',
    `IN_APP_TEMPLATE_ID` BIGINT UNSIGNED NULL COMMENT 'Identifier for the inApp template. References notification_template table',
    `SMS_TEMPLATE_ID` BIGINT UNSIGNED NULL COMMENT 'Identifier for the sms template. References notification_template table',
    `PUSH_TEMPLATE_ID` BIGINT UNSIGNED NULL COMMENT 'Identifier for the push template. References notification_template table',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_NOTIFICATION_TYPE_CODE` (`CODE`),
    INDEX `IDX1_NOTIFICATION_TEMPLATE_CLIENT_ID_APP_ID` (`CLIENT_ID`, `APP_ID`),
    INDEX `IDX2_NOTIFICATION_TEMPLATE_APP_ID` (`APP_ID`),

    CONSTRAINT `FK1_NOTIFICATION_NOTIFICATION_TYPE` FOREIGN KEY (`NOTIFICATION_TYPE_ID`)
        REFERENCES `notification_type` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_NOTIFICATION_EMAIL_TEMPLATE` FOREIGN KEY (`EMAIL_TEMPLATE_ID`)
        REFERENCES `notification_template` (`ID`)
        ON DELETE SET NULL
        ON UPDATE CASCADE,
    CONSTRAINT `FK3_NOTIFICATION_IN_APP_TEMPLATE` FOREIGN KEY (`IN_APP_TEMPLATE_ID`)
        REFERENCES `notification_template` (`ID`)
        ON DELETE SET NULL
        ON UPDATE CASCADE,
    CONSTRAINT `FK4_NOTIFICATION_SMS_TEMPLATE` FOREIGN KEY (`SMS_TEMPLATE_ID`)
        REFERENCES `notification_template` (`ID`)
        ON DELETE SET NULL
        ON UPDATE CASCADE,
    CONSTRAINT `FK5_NOTIFICATION_PUSH_TEMPLATE` FOREIGN KEY (`PUSH_TEMPLATE_ID`)
        REFERENCES `notification_template` (`ID`)
        ON DELETE SET NULL
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
