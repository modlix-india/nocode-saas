ALTER TABLE `entity_processor`.`entity_processor_activities`
    DROP FOREIGN KEY `FK2_ACTIVITIES_TASK_ID`;
ALTER TABLE `entity_processor`.`entity_processor_activities`
    DROP FOREIGN KEY `FK3_ACTIVITIES_NOTE_ID`;

ALTER TABLE `entity_processor`.`entity_processor_activities`
    ADD CONSTRAINT `FK2_ACTIVITIES_TASK_ID`
        FOREIGN KEY (`TASK_ID`)
            REFERENCES `entity_processor_tasks` (`ID`)
            ON UPDATE CASCADE
            ON DELETE SET NULL;

ALTER TABLE `entity_processor`.`entity_processor_activities`
    ADD CONSTRAINT `FK3_ACTIVITIES_NOTE_ID`
        FOREIGN KEY (`NOTE_ID`)
            REFERENCES `entity_processor_notes` (`ID`)
            ON UPDATE CASCADE
            ON DELETE SET NULL;
