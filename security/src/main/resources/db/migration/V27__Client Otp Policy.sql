DROP TABLE IF EXISTS `security`.`security_client_otp_policy`;

CREATE TABLE `security`.`security_client_otp_policy`
(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each OTP policy entry',
    `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the client to which this OTP policy belongs. References security_client table',
    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application to which this OTP policy belongs. References security_app table',
    `IS_NUMERIC` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag indicating if OTP should contain only numeric characters',
    `IS_ALPHANUMERIC` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag indicating if OTP should contain alphanumeric characters',
    `LENGTH` SMALLINT UNSIGNED NOT NULL DEFAULT 4 COMMENT 'Length of the OTP to be generated',
    `NO_FAILED_ATTEMPTS` SMALLINT UNSIGNED NOT NULL DEFAULT 3 COMMENT 'Maximum number of failed attempts allowed before OTP is invalidated',
    `EXPIRE_INTERVAL` BIGINT UNSIGNED NOT NULL DEFAULT 5 COMMENT 'Time interval in minutes after which OTP will expire',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who last updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is last updated',

    PRIMARY KEY (`ID`),
    CONSTRAINT `FK1_CLIENT_OTP_POL_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK2_CLIENT_OTP_POL_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
