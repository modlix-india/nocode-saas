ALTER TABLE `entity_processor`.`entity_processor_simple_rules` DROP FOREIGN KEY `FK2_SIMPLE_RULES_PRODUCT_STAGE_RULE_ID`;
ALTER TABLE `entity_processor`.`entity_processor_complex_rules` DROP FOREIGN KEY `FK2_COMPLEX_RULES_PRODUCT_STAGE_RULE_ID`;

ALTER TABLE `entity_processor`.`entity_processor_product_rules`
    RENAME COLUMN `USER_DISTRIBUTIONS` TO `USER_DISTRIBUTION`;

RENAME TABLE `entity_processor`.`entity_processor_product_rules` TO `entity_processor_product_stage_rules`;

ALTER TABLE `entity_processor`.`entity_processor_simple_rules`
    ADD CONSTRAINT `FK2_SIMPLE_RULES_PRODUCT_STAGE_RULE_ID` FOREIGN KEY (`PRODUCT_STAGE_RULE_ID`)
        REFERENCES `entity_processor_product_stage_rules` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE;

ALTER TABLE `entity_processor`.`entity_processor_complex_rules`
    ADD CONSTRAINT `FK2_COMPLEX_RULES_PRODUCT_STAGE_RULE_ID` FOREIGN KEY (`PRODUCT_STAGE_RULE_ID`)
        REFERENCES `entity_processor_product_stage_rules` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE;

ALTER TABLE `entity_processor`.`entity_processor_simple_complex_rule_relations`
    DROP COLUMN ADDED_BY_USER_ID;

ALTER TABLE `entity_processor`.`entity_processor_complex_rules`
    DROP COLUMN ADDED_BY_USER_ID;

ALTER TABLE `entity_processor`.`entity_processor_product_template_rules`
    RENAME COLUMN `USER_DISTRIBUTIONS` TO `USER_DISTRIBUTION`;

ALTER TABLE `entity_processor`.`entity_processor_product_template_rules`
    DROP INDEX `UK2_PRODUCT_TEMPLATE_RULES_PRODUCT_TEMPLATE_ID_STAGE_ID`,
    ADD UNIQUE KEY `UK2_PRODUCT_TEMPLATE_RULES_PRODUCT_TEMPLATE_ID_STAGE_ID_order` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_TEMPLATE_ID`, `STAGE_ID`, `ORDER`);
