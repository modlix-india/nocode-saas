ALTER TABLE `entity_processor`.`entity_processor_activities`
    MODIFY COLUMN `OBJECT_ENTITY_SERIES` VARCHAR(64) DEFAULT 'XXX' NOT NULL
        COMMENT 'Entity Series of the object associated with this Activity. ';

UPDATE `entity_processor`.`entity_processor_activities`
SET `OBJECT_ENTITY_SERIES` = 'ACTIVITY'
WHERE `OBJECT_ENTITY_SERIES` = 'Activity';

ALTER TABLE `entity_processor`.`entity_processor_activities`
    MODIFY COLUMN `OBJECT_ENTITY_SERIES` ENUM (
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
        ) DEFAULT 'XXX' NOT NULL COMMENT 'Entity Series of the object associated with this Activity. ';

ALTER TABLE `entity_processor`.`entity_processor_activities`
    ADD INDEX `IDX1_ACTIVITIES_ACTIVITY_ACTION` (`ACTIVITY_ACTION`),
    ADD INDEX `IDX2_ACTIVITIES_OBJECT_ENTITY_SERIES` (`OBJECT_ENTITY_SERIES`);
