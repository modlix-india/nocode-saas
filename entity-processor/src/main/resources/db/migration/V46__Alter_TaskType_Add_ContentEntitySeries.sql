ALTER TABLE `entity_processor`.`entity_processor_task_types`
    DROP INDEX `UK2_TASK_TYPES_NAME`;

ALTER TABLE `entity_processor`.`entity_processor_task_types`
    ADD COLUMN `CONTENT_ENTITY_SERIES` ENUM ('OWNER', 'TICKET', 'USER') NOT NULL DEFAULT 'TICKET' COMMENT 'Type of entity for which this task type is applicable' AFTER `DESCRIPTION`,
    ADD UNIQUE KEY `UK2_TASK_TYPES_NAME` (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CONTENT_ENTITY_SERIES`);
