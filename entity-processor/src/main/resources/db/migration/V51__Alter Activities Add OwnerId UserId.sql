ALTER TABLE `entity_processor`.`entity_processor_activities`
    MODIFY COLUMN `TICKET_ID` BIGINT UNSIGNED NULL COMMENT 'Ticket related to this Activity.';

ALTER TABLE `entity_processor`.`entity_processor_activities`
    ADD COLUMN `OWNER_ID` BIGINT UNSIGNED NULL COMMENT 'Owner related to this Activity.' AFTER `TICKET_ID`,
    ADD COLUMN `USER_ID` BIGINT UNSIGNED NULL COMMENT 'User related to this Activity.' AFTER `OWNER_ID`;

ALTER TABLE `entity_processor`.`entity_processor_activities`
    ADD CONSTRAINT `FK_ACTIVITIES_OWNER_ID`
        FOREIGN KEY (`OWNER_ID`)
            REFERENCES `entity_processor`.`entity_processor_owners` (`ID`)
            ON DELETE CASCADE
            ON UPDATE CASCADE;
