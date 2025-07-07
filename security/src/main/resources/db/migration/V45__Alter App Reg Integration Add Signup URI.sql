USE `security`;

ALTER TABLE `security`.`security_app_reg_integration`
    ADD COLUMN `SIGNUP_URI` VARCHAR(2083) DEFAULT NULL COMMENT 'URI for signup' AFTER `LOGIN_URI`;

UPDATE `security`.`security_app_reg_integration`
SET `security_app_reg_integration`.`SIGNUP_URI` = `security_app_reg_integration`.`LOGIN_URI`
WHERE security_app_reg_integration.`SIGNUP_URI` IS NULL;

ALTER TABLE `security`.`security_app_reg_integration`
    MODIFY COLUMN `SIGNUP_URI` VARCHAR(2083) NOT NULL COMMENT 'URI for signup' AFTER `LOGIN_URI`;
