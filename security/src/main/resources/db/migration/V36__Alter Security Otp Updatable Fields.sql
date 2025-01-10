ALTER TABLE `security`.`security_otp`
    ADD COLUMN `VERIFY_LEGS_COUNTS` SMALLINT DEFAULT 0 NOT NULL COMMENT 'Number of legs in otp verification. If 0 this otp will be completely verified and deleted' AFTER `IP_ADDRESS`,
    ADD COLUMN `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who last updated this row' AFTER `CREATED_AT`,
    ADD COLUMN `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is last updated' AFTER `UPDATED_BY`;
