USE `entity_collector`;

CREATE TABLE `entity_integrations` (
  `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each entity integration',
  `CLIENT_CODE` CHAR(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Client Code',
  `APP_CODE` CHAR(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'App Code',
  `PRIMARY_TARGET` VARCHAR(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Primary target to forwarded entity',
  `SECONDARY_TARGET` VARCHAR(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Secondary target to forwarded entity',
  `IN_SOURCE` VARCHAR(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Source',
  `IN_SOURCE_TYPE` ENUM('FACEBOOK_FORM','GOOGLE_FORM','WEBSITE') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Type of source that integration is generated',
  `PRIMARY_VERIFY_TOKEN` VARCHAR(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Primary verification token is verify the primary target',
  `SECONDARY_VERIFY_TOKEN` VARCHAR(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Secondary verification token is to verify the secondary target',
  `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK_CLIENT_SOURCE` (`CLIENT_CODE`, `IN_SOURCE`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
