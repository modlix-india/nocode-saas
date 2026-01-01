USE `entity_collector`;

ALTER TABLE `entity_collector`.`entity_collector_log`
    MODIFY COLUMN `STATUS` ENUM('IN_PROGRESS','REJECTED','SUCCESS','WITH_ERRORS','RESPONSE_CREATED') COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Status of the entity transfer';