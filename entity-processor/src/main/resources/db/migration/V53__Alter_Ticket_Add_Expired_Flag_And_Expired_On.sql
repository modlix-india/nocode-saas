ALTER TABLE `entity_processor`.`entity_processor_tickets`
    ADD COLUMN `IS_EXPIRED` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to indicate if ticket is expired due to no activity within configured days.' AFTER `IS_ACTIVE`,
    ADD COLUMN `EXPIRED_ON` TIMESTAMP NULL DEFAULT NULL COMMENT 'Timestamp when the ticket was marked as expired.' AFTER `IS_EXPIRED`;
