use security;

ALTER TABLE `security`.`security_profile`
    ADD COLUMN `Title` VARCHAR(512) NULL DEFAULT NULL AFTER `NAME`;
