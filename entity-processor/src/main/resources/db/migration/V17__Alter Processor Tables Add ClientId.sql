ALTER TABLE `entity_processor`.`entity_processor_tickets`
    ADD COLUMN `CLIENT_ID` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Id of client who created this ticket.' AFTER `IS_ACTIVE`;

ALTER TABLE `entity_processor`.`entity_processor_owners`
    ADD COLUMN `CLIENT_ID` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Id of client who created this owner.' AFTER `IS_ACTIVE`;

ALTER TABLE `entity_processor`.`entity_processor_products`
    ADD COLUMN `CLIENT_ID` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Id of client who created this product.' AFTER `IS_ACTIVE`;
