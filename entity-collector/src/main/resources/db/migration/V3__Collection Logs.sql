CREATE TABLE IF NOT EXISTS collection_logs
(

`id`                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
`entity_integration_id`    BIGINT UNSIGNED NOT NULL COMMENT 'Entity integration ID',
`incoming_lead_data`       JSON NOT NULL COMMENT 'Lead Data',
`ip_address`               VARCHAR(320) COMMENT 'Ip Address',
`outgoing_lead_data`       JSON NOT NULL COMMENT 'Lead Data Forwarded to CRM',
`status`                   ENUM ('REJECTED', 'SUCCESS', 'WITH_ERRORS') NOT NULL COMMENT 'Status of the Lead Transfer',
`status_message`           TEXT DEFAULT NULL COMMENT "Message given for the status",
`created_at`               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  PRIMARY KEY (`id`),
  KEY `FK1_collection_entity_integration_id` (`entity_integration_id`),
  CONSTRAINT `FK1_collection_entity_integration_id`
         FOREIGN KEY (`entity_integration_id`)
         REFERENCES `entity_integrations` (`id`)
         ON DELETE RESTRICT
         ON UPDATE RESTRICT
 )
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;