ALTER TABLE `entity_processor`.`entity_processor_products`
    ADD COLUMN `OVERRIDE_C_TEMPLATE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to tell weather to override the Create (C) template rules' AFTER `FOR_PARTNER`,
    ADD COLUMN `OVERRIDE_RU_TEMPLATE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to tell weather to override the Read Update (RU) template rules' AFTER `OVERRIDE_C_TEMPLATE`;
