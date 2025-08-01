USE `security`;

ALTER TABLE `security`.`security_client`
    ADD COLUMN `BUSINESS_SIZE` VARCHAR(128) DEFAULT NULL COMMENT 'client business size input',
    ADD COLUMN `INDUSTRY`      VARCHAR(128) DEFAULT NULL COMMENT 'client business industry';