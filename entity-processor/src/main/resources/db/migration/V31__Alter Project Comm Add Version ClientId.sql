ALTER TABLE `entity_processor`.`entity_processor_product_comms`
    ADD COLUMN `VERSION` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Version of this row.' AFTER `DESCRIPTION`,
    ADD COLUMN `CLIENT_ID` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Id of client who created this product.' AFTER `IS_ACTIVE`;
