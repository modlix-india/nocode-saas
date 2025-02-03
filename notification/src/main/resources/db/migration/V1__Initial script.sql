/* Creating Database */

-- DROP DATABASE IF EXISTS `notification`;

CREATE DATABASE IF NOT EXISTS `NOTIFICATION` DEFAULT CHARACTER SET `UTF8MB4` COLLATE `UTF8MB4_UNICODE_CI`;

USE `NOTIFICATION`;

CREATE TABLE IF NOT EXISTS `NOTIFICATION`.`NOTIFICATION_TYPE`
(
    `ID`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID`   BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the client to which this OTP policy belongs. References security_client table',
    `APP_ID`      BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application to which this OTP policy belongs. References security_app table',
    `CODE`        CHAR(36)        NOT NULL COMMENT 'Code',
    `NAME`        CHAR(125)       NOT NULL COMMENT 'notification type name',
    `DESCRIPTION` TEXT                     DEFAULT NULL COMMENT 'description of notification type',
    `CREATED_BY`  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_NOTIFICATION_TYPE_CODE` (`CODE`)
)

