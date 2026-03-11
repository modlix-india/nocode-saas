use security;

ALTER TABLE `security_ssl_request` ADD COLUMN `ORDER_URL` VARCHAR(1024) DEFAULT NULL COMMENT 'ACME order URL to reuse across trigger attempts' AFTER `FAILED_REASON`;
