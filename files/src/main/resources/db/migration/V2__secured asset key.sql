CREATE TABLE `files_secured_access_key` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `path` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Path which needs to be secured.',
  `access_key` char(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Key used for securing the file.',
  `access_till` timestamp NOT NULL COMMENT 'Time which the path can be accessed',
  `access_limit` bigint unsigned DEFAULT NULL COMMENT 'Maximum times in which the file can be accessed',
  `accessed_count` bigint unsigned NOT NULL DEFAULT '0' COMMENT 'Tracks count of file accessed',
  `created_by` bigint unsigned DEFAULT NULL COMMENT 'User id who created this row.',
  `created_at` timestamp NULL DEFAULT NULL COMMENT 'Time at which this row was created',
  PRIMARY KEY (`id`),
  UNIQUE KEY `access_key_UNIQUE` (`access_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci