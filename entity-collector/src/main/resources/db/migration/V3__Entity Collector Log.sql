USE `entity_collector`;

CREATE TABLE IF NOT EXISTS  `entity_collector_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `entity_integration_id` bigint unsigned NOT NULL COMMENT 'Entity integration ID',
  `incoming_entity_data` json DEFAULT NULL COMMENT 'Entity Data',
  `ip_address` varchar(320) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Ip Address',
  `outgoing_entity_data` json DEFAULT NULL COMMENT 'Entity Data Forwarded to CRM',
  `status` enum('REJECTED','SUCCESS','WITH_ERRORS') COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Status of the Entity Transfer',
  `status_message` text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Message given for the status',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  PRIMARY KEY (`id`),
  KEY `FK1_collector_entity_integration_id` (`entity_integration_id`),
  CONSTRAINT `FK1_collector_entity_integration_id` FOREIGN KEY (`entity_integration_id`)
    REFERENCES `entity_integrations` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
