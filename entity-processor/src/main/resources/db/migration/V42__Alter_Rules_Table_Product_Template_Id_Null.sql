ALTER TABLE `entity_processor`.`entity_processor_product_ticket_c_rules`
    MODIFY COLUMN `PRODUCT_TEMPLATE_ID` BIGINT UNSIGNED NULL COMMENT 'Product Template id related to this Rule Config.';

ALTER TABLE `entity_processor`.`entity_processor_product_ticket_ru_rules`
    MODIFY COLUMN `PRODUCT_TEMPLATE_ID` BIGINT UNSIGNED NULL COMMENT 'Product Template id related to this Rule Config.';

ALTER TABLE `entity_processor`.`entity_processor_ticket_duplication_rules`
    MODIFY COLUMN `PRODUCT_TEMPLATE_ID` BIGINT UNSIGNED NULL COMMENT 'Product Template id related to this Rule Config.';
