DROP TABLE IF EXISTS `entity_processor`.`entity_processor_task_types`;

CREATE TABLE `entity_processor`.`entity_processor_task_types` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this task type was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this task type.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(512) NOT NULL COMMENT 'Name of the Task Type.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Task Type.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this task type.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this task type is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_TASK_TYPES_CODE` (`CODE`),
    UNIQUE KEY `UK2_TASK_TYPES_NAME` (`APP_CODE`, `CLIENT_CODE`, `NAME`)
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_tasks`;

CREATE TABLE `entity_processor`.`entity_processor_tasks` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this task was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this task.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(512) NOT NULL COMMENT 'Name of the Task.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Task.',
    `VERSION` INT NOT NULL DEFAULT 1 COMMENT 'Version of this row.',
    `CONTENT` TEXT NULL COMMENT 'Content of the task.',
    `HAS_ATTACHMENT` VARCHAR(255) NULL COMMENT 'Whether this task has attachments.',
    `OWNER_ID` BIGINT UNSIGNED NULL COMMENT 'Owner related to this task.',
    `TICKET_ID` BIGINT UNSIGNED NULL COMMENT 'Ticket related to this task.',
    `TASK_TYPE_ID` BIGINT UNSIGNED NULL COMMENT 'Type of the task.',
    `DUE_DATE` TIMESTAMP NULL COMMENT 'Due date for this task.',
    `TASK_PRIORITY` ENUM ('LOW', 'MEDIUM', 'HIGH', 'URGENT') NOT NULL DEFAULT 'MEDIUM' COMMENT 'Priority level of the task.',
    `HAS_REMINDER` TINYINT NOT NULL DEFAULT 0 COMMENT 'Whether this task has a reminder set.',
    `NEXT_REMINDER` TIMESTAMP NULL COMMENT 'Next reminder date and time for this task.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this task.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this task is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_TASKS_CODE` (`CODE`),
    CONSTRAINT `FK1_TASKS_TASK_TYPE_ID` FOREIGN KEY (`TASK_TYPE_ID`)
        REFERENCES `entity_processor`.`entity_processor_task_types` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_TASKS_OWNER_ID` FOREIGN KEY (`OWNER_ID`)
        REFERENCES `entity_processor`.`entity_processor_owners` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK3_TASKS_TICKET_ID` FOREIGN KEY (`TICKET_ID`)
        REFERENCES `entity_processor`.`entity_processor_tickets` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_notes`;

CREATE TABLE `entity_processor`.`entity_processor_notes` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this note was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this note.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(512) NOT NULL COMMENT 'Name of the Note.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Note.',
    `VERSION` INT NOT NULL DEFAULT 1 COMMENT 'Version of this row.',
    `CONTENT` TEXT NULL COMMENT 'Content of the note.',
    `HAS_ATTACHMENT` TINYINT DEFAULT 0 NOT NULL COMMENT 'Whether this note has attachments.',
    `OWNER_ID` BIGINT UNSIGNED NULL COMMENT 'Owner related to this note.',
    `TICKET_ID` BIGINT UNSIGNED NULL COMMENT 'Ticket related to this note.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this note.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this note is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_NOTES_CODE` (`CODE`),
    CONSTRAINT `FK1_NOTES_OWNER_ID` FOREIGN KEY (`OWNER_ID`)
        REFERENCES `entity_processor`.`entity_processor_owners` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK3_NOTES_TICKET_ID` FOREIGN KEY (`TICKET_ID`)
        REFERENCES `entity_processor`.`entity_processor_tickets` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
