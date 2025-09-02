ALTER TABLE `entity_processor`.`entity_processor_products`
    ADD COLUMN `FOR_PARTNER` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to tell weather Partner has access to this product or not.' AFTER `CLIENT_ID`;
