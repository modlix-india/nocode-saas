ALTER TABLE `entity_processor`.`entity_processor_products`
    ADD COLUMN `PRODUCT_WALK_IN_FORM_ID` BIGINT UNSIGNED NULL COMMENT 'Walk in form related to this product.' AFTER `PRODUCT_TEMPLATE_ID`;

ALTER TABLE `entity_processor`.`entity_processor_product_templates`
    ADD COLUMN `PRODUCT_TEMPLATE_WALK_IN_FORM_ID` BIGINT UNSIGNED NULL COMMENT 'Walk in form related to this product template.' AFTER `PRODUCT_TEMPLATE_TYPE`;

ALTER TABLE `entity_processor`.`entity_processor_products`
    ADD CONSTRAINT `FK2_PRODUCTS_PWIF_ID`
        FOREIGN KEY (`PRODUCT_WALK_IN_FORM_ID`)
            REFERENCES `entity_processor`.`entity_processor_product_walk_in_forms` (`ID`)
            ON DELETE RESTRICT
            ON UPDATE CASCADE;

ALTER TABLE `entity_processor`.`entity_processor_product_templates`
    ADD CONSTRAINT `FK2_PRODUCT_TEMPLATES_PTWIF_ID`
        FOREIGN KEY (`PRODUCT_TEMPLATE_WALK_IN_FORM_ID`)
            REFERENCES `entity_processor`.`entity_processor_product_template_walk_in_forms` (`ID`)
            ON DELETE RESTRICT
            ON UPDATE CASCADE;
