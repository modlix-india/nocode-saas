ALTER TABLE `security`.`security_client_password_policy`
    ADD COLUMN `APP_ID` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT 'Identifier for the application to which this OTP belongs. References security_app table' AFTER CLIENT_ID;

ALTER TABLE `security`.`security_client_password_policy`
    ADD CONSTRAINT `FK2_CLIENT_PWD_POL_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security`.`security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT;
