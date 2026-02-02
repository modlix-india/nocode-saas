ALTER TABLE `entity_processor`.`entity_processor_tasks`
    ADD COLUMN `CONTENT_ENTITY_SERIES` ENUM ('OWNER', 'TICKET', 'USER') DEFAULT 'TICKET' COMMENT 'Type of entity for which this content was created' AFTER `HAS_ATTACHMENT`;

ALTER TABLE `entity_processor`.`entity_processor_notes`
    ADD COLUMN `CONTENT_ENTITY_SERIES` ENUM ('OWNER', 'TICKET', 'USER') DEFAULT 'TICKET' COMMENT 'Type of entity for which this content was created' AFTER `HAS_ATTACHMENT`;
