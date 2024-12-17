DROP TABLE IF EXISTS  `security`.`security_client_pin_policy`;

CREATE TABLE `security`.`security_client_pin_policy`
(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each PIN policy entry',
    `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the client to which this PIN policy belongs. References security_client table',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application to which this PIN policy belongs. References security_app table',
    `LENGTH` SMALLINT UNSIGNED NOT NULL DEFAULT 4 COMMENT 'Length of the PIN to be generated',
    `RE_LOGIN_AFTER_INTERVAL` BIGINT UNSIGNED NOT NULL DEFAULT 120 COMMENT 'Time interval in minutes after which re-login is required',
    `EXPIRY_IN_DAYS` SMALLINT UNSIGNED NOT NULL DEFAULT 30 COMMENT 'Number of days after which the PIN expires',
    `EXPIRY_WARN_IN_DAYS` SMALLINT UNSIGNED NOT NULL DEFAULT 25 COMMENT 'Number of days before expiry to warn the user',
    `PIN_HISTORY_COUNT` SMALLINT UNSIGNED NOT NULL DEFAULT 3 COMMENT 'Remember how many pin',
    `NO_FAILED_ATTEMPTS` SMALLINT UNSIGNED NOT NULL DEFAULT 3 COMMENT 'Maximum number of failed attempts allowed before PIN login is blocked',
    `USER_LOCK_TIME` BIGINT UNSIGNED NOT NULL DEFAULT 30 COMMENT 'Time in minutes for which user need to be locked it policy violates',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who last updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is last updated',

    PRIMARY KEY (`ID`),
    CONSTRAINT `UK1_CLIENT_PIN_POL_CLIENT_ID_APP_ID` UNIQUE (`CLIENT_ID`, `APP_ID`),
    CONSTRAINT `FK1_CLIENT_PIN_POL_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK2_CLIENT_PIN_POL_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
