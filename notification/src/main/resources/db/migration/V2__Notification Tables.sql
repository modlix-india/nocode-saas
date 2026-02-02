use notification;

DROP TABLE IF EXISTS `notification`.`notification_inapp`;

CREATE TABLE `notification`.`notification_inapp`
(
    `ID`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each notification entry',
    `USER_ID`           BIGINT UNSIGNED NOT NULL COMMENT 'User ID',
    `APP_CODE`          CHAR(64)        NOT NULL COMMENT 'App Code',
    `NOTIFICATION_NAME` VARCHAR(256)    NOT NULL COMMENT 'Notification name',
    `NOTIFICATION_ID`   CHAR(32)        NOT NULL COMMENT 'Notification ID',
    `TITLE`             VARCHAR(256)    NOT NULL COMMENT 'Notification title',
    `MESSAGE`           TEXT            NOT NULL COMMENT 'Notification message',
    `MIME_URL`          VARCHAR(1024)            DEFAULT NULL COMMENT 'Mime URL',
    `NOTIFICATION_TYPE` VARCHAR(256)    NOT NULL COMMENT 'Notification type',
    `READ_AT`           TIMESTAMP                DEFAULT NULL COMMENT 'Read at',
    `CREATED_AT`        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    PRIMARY KEY (`ID`)
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `notification`.`notification_preference`;

CREATE TABLE `notification`.`notification_preference`
(
    `ID`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each notification preference entry',
    `USER_ID`    BIGINT UNSIGNED NOT NULL COMMENT 'User ID',
    `APP_CODE`   CHAR(64)        NOT NULL COMMENT 'App Code',
    `PREFERENCE` JSON                     DEFAULT NULL COMMENT 'Preference',
    `CREATED_AT` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_AT` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    `CREATED_BY` BIGINT UNSIGNED NOT NULL COMMENT 'Created by',
    `UPDATED_BY` BIGINT UNSIGNED NOT NULL COMMENT 'Updated by',
    PRIMARY KEY (`ID`)
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;