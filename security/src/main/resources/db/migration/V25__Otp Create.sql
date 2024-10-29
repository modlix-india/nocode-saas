USE `security`;

CREATE TABLE `security_otp`
(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each OTP entry',

    `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application to which this OTP belongs. References security_app table',
    `USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the user for whom this OTP is generated. References security_user table',

    `PURPOSE` VARCHAR(255) NOT NULL COMMENT 'Purpose or reason for the OTP (e.g., authentication, password reset, etc.)',
    `TARGET_TYPE` ENUM ('EMAIL', 'PHONE', 'BOTH') DEFAULT 'PHONE' NOT NULL COMMENT 'The target medium for the OTP delivery: EMAIL, PHONE, or BOTH',

    `UNIQUE_CODE` CHAR(64) NOT NULL COMMENT 'The hashed OTP code used for verification',
    `ATTEMPTS` INT DEFAULT 0 NOT NULL COMMENT 'Number of attempts made to use this OTP for verification',
    `EXPIRES_AT` TIMESTAMP NOT NULL COMMENT 'Timestamp indicating when the OTP expires and becomes invalid',
    `IP_ADDRESS` CHAR(45) COMMENT 'IP address of the user to track OTP generation or use, supports both IPv4 and IPv6',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL,
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`ID`),
    CONSTRAINT `FK1_OTP_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK2_OTP_USER_ID` FOREIGN KEY (`USER_ID`) REFERENCES `security_user` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,

    INDEX (`UNIQUE_CODE`),
    INDEX (`EXPIRES_AT`),
    INDEX (`CREATED_AT` DESC),
    INDEX (`APP_ID`, `USER_ID`, `PURPOSE`)
);
