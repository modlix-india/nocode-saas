ALTER TABLE `security`.`security_invoice`
    ADD COLUMN `INVOICE_REASON` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Invoice reason';

ALTER TABLE `security`.`security_sox_log`
    CHANGE COLUMN `OBJECT_NAME` `OBJECT_NAME` ENUM('USER', 'ROLE', 'PERMISSION', 'PACKAGE', 'CLIENT', 'CLIENT_TYPE', 'APP', 'PROFILE', 'INVOICE') CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_unicode_ci' NOT NULL COMMENT 'Operation on the object' ;

ALTER TABLE security.security_client
  ADD COLUMN BILLING_TIMEZONE varchar(64) NOT NULL DEFAULT 'Asia/Calcutta' COMMENT 'IANA timezone for billing';
