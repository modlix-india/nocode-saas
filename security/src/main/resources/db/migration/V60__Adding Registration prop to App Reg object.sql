ALTER TABLE `security`.`security_app_reg_access`
    ADD COLUMN `REGISTER` tinyint(1) NOT NULL DEFAULT '0' AFTER `WRITE_ACCESS`;