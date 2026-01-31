use security;

ALTER TABLE `security_one_time_token`
    ADD COLUMN `REMEMBER_ME` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Flag to indicate if token should be permanent' AFTER `IP_ADDRESS`;
