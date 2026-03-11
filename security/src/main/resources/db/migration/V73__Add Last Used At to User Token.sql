use security;
ALTER TABLE `security_user_token` ADD COLUMN `LAST_USED_AT` TIMESTAMP NULL DEFAULT NULL COMMENT 'Last time the token was used' AFTER `IP_ADDRESS`;