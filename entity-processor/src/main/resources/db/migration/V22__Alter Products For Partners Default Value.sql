ALTER TABLE `entity_processor`.`entity_processor_products`
    MODIFY COLUMN `FOR_PARTNER` TINYINT NOT NULL DEFAULT 1
        COMMENT 'Flag to tell whether Partner has access to this product or not.';
