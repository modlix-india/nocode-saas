ALTER TABLE `entity_processor`.`entity_processor_activities`
    ADD COLUMN `NOTE_ID` BIGINT UNSIGNED NULL COMMENT 'Note related to this Activity.' AFTER `TASK_ID`,
    ADD CONSTRAINT `FK3_ACTIVITIES_NOTE_ID` FOREIGN KEY (`NOTE_ID`)
        REFERENCES `entity_processor`.`entity_processor_notes` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE;

ALTER TABLE `entity_processor`.`entity_processor_activities`
    DROP COLUMN `UPDATED_BY`,
    DROP COLUMN `UPDATED_AT`;
