DROP TABLE IF EXISTS `entity_processor`.`entity_processor_activities`;

CREATE TABLE `entity_processor`.`entity_processor_activities` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this task type was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this task type.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(512) NOT NULL COMMENT 'Name of the Task Type.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Task Type.',
    `TICKET_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Ticket related to this task.',
    `ACTIVITY_DATE` TIMESTAMP NOT NULL COMMENT 'Date of the activity.',
    `ACTIVITY_ACTION` ENUM(
        'CREATE',
        'QUALIFY',
        'DISQUALIFY',
        'DISCARD',
        'IMPORT',
        'STATUS_CREATE',
        'STAGE_UPDATE',

        'TASK_CREATE',
        'TASK_COMPLETE',
        'TASK_DELETE',
        'REMINDER_SET',

        'DOCUMENT_UPLOAD',
        'DOCUMENT_DOWNLOAD',
        'DOCUMENT_DELETE',

        'NOTE_ADD',
        'NOTE_DELETE',

        'ASSIGN',
        'REASSIGN',
        'REASSIGN_SYSTEM',
        'OWNERSHIP_TRANSFER',

        'CALL_LOG',
        'WHATSAPP',
        'EMAIL_SENT',
        'SMS_SENT',

        'FIELD_UPDATE',
        'CUSTOM_FIELD_UPDATE',
        'LOCATION_UPDATE',

        'OTHER'
        )  NOT NULL COMMENT 'Action performed on the object.',
    `OBJECT_ENTITY_SERIES` ENUM (
        'XXX',
        'TICKET',
        'OWNER',
        'PRODUCT',
        'PRODUCT_TEMPLATE',
        'STAGE',
        'SIMPLE_RULE',
        'COMPLEX_RULE',
        'SIMPLE_COMPLEX_CONDITION_RELATION',
        'PRODUCT_STAGE_RULE',
        'PRODUCT_TEMPLATE_RULE',
        'TASK',
        'TASK_TYPE',
        'NOTE',
        'ACTIVITY'
        ) DEFAULT 'XXX' NOT NULL COMMENT 'Entity Series of the object.',
    `OBJECT_ID` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Object id of OBJECT_ENTITY_SERIES on which activity is performed',
    `OBJECT_DATA` JSON NULL COMMENT 'Object data of OBJECT_ENTITY_SERIES on which activity is performed',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this task type.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this task type is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_ACTIVITIES_CODE` (`CODE`),
    CONSTRAINT `FK2_ACTIVITIES_TICKET_ID` FOREIGN KEY (`TICKET_ID`)
        REFERENCES `entity_processor`.`entity_processor_tickets` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
