ALTER TABLE `entity_processor`.`entity_processor_complex_rules`
    ADD COLUMN `HAS_COMPLEX_CHILD` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to tell weather this rule has complex children.' AFTER `LOGICAL_OPERATOR`,
    ADD COLUMN `HAS_SIMPLE_CHILD` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to tell weather this rule has simple children.' AFTER `HAS_COMPLEX_CHILD`;
