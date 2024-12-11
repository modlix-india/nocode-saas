ALTER TABLE security.security_client_password_policy
    DROP FOREIGN KEY `FK1_CLIENT_PWD_POL_CLIENT_ID`;

ALTER TABLE `security`.`security_client_password_policy`
    DROP INDEX `UK1_CLIENT_PWD_POL_ID`;

ALTER TABLE `security`.`security_client_password_policy`
    ADD COLUMN `APP_ID` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT 'Identifier for the application to which this OTP belongs. References security_app table' AFTER CLIENT_ID;

ALTER TABLE `security`.`security_client_password_policy`
    ADD CONSTRAINT `UK1_CLIENT_PWD_POL_CLIENT_ID_APP_ID` UNIQUE (`CLIENT_ID`, `APP_ID`),
    ADD CONSTRAINT `FK1_CLIENT_PWD_POL_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security`.`security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `FK2_CLIENT_PWD_POL_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security`.`security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT;
