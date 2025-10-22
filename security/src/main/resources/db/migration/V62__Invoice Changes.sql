ALTER TABLE `security`.`security_invoice`
    ADD COLUMN `INVOICE_REASON` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Invoice reason';
