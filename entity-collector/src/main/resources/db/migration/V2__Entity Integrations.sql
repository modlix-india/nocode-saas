USE `entity_collector`;

CREATE TABLE `entity_integrations` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each Entity Integration',
  `client_code` char(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Client Code',
  `app_code` char(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'App Code',
  `target` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Target',
  `secondary_target` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Secondary target',
  `in_source` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Source',
  `in_source_type` enum('FACEBOOK_FORM','GOOGLE_FORM','WEBSITE') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Type of source that integration is generated',
  `created_by` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `updated_by` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_source` (`client_code`, `in_source`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
