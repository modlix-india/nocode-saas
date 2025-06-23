ALTER TABLE `entity_processor`.`entity_processor_activities`
    ADD COLUMN `ACTOR_ID` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who performed this activity.' AFTER `ACTIVITY_ACTION`;
