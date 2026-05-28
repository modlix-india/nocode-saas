USE `entity_processor`;

-- V69 created entity_processor_collector_log WITHOUT CREATED_BY / UPDATED_BY,
-- but EntityCollectorLog extends AbstractUpdatableDTO (and its DAO extends
-- AbstractUpdatableDAO) which assumes those audit columns and tries to write
-- them on every insert/update. Prod failed with JOOQ "Field CREATED_BY is
-- not contained in row type" on the /entry/website webhook path. Aligning the
-- table with the rest of the schema (and the base updatable DAO contract).
ALTER TABLE `entity_processor`.`entity_processor_collector_log`
    ADD COLUMN `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'ID of the user who created this row.' AFTER `STATUS_MESSAGE`,
    ADD COLUMN `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL
        COMMENT 'ID of the user who last updated this row.' AFTER `CREATED_AT`;
