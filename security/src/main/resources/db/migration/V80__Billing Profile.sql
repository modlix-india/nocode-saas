USE `security`;

-- Buyer billing profile (customer-of-record details) per (client M, app).
-- Collected on the order-summary screen, reused/pre-filled on later purchases,
-- and snapshotted onto each invoice. Seller-of-record details stay on
-- security_app_billing_config; this is the BUYER side.

CREATE TABLE IF NOT EXISTS `security`.`security_billing_profile` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Buyer client M this profile belongs to',
  `APP_ID` bigint unsigned NOT NULL COMMENT 'App this billing profile applies to',
  `LEGAL_NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Buyer legal / business name for the tax invoice',
  `GSTIN` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Buyer GSTIN (optional, for B2B input credit)',
  `ADDRESS_LINE` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Buyer street / building address',
  `CITY` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Buyer city',
  `STATE` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Buyer state / province (basis for India GST intra/inter-state)',
  `COUNTRY` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Buyer country (basis for per-country tax)',
  `POSTAL_CODE` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Buyer postal / ZIP code',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_BP_CLIENT_ID_APP_ID` (`CLIENT_ID`,`APP_ID`),
  KEY `FK1_BP_CLIENT_ID` (`CLIENT_ID`),
  KEY `FK2_BP_APP_ID` (`APP_ID`),
  CONSTRAINT `FK1_BP_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_BP_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
