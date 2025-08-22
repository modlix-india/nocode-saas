ALTER TABLE `entity_processor`.`entity_processor_tasks`
    ADD COLUMN `USER_ID` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Id of user for which this task was created.' AFTER `TICKET_ID`,
    ADD COLUMN `CLIENT_ID` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Id of client for which this task was created.' AFTER `USER_ID`;

ALTER TABLE `entity_processor`.`entity_processor_notes`
    ADD COLUMN `USER_ID` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Id of user for which this task was created.' AFTER `TICKET_ID`,
    ADD COLUMN `CLIENT_ID` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Id of client for which this task was created.' AFTER `USER_ID`;
