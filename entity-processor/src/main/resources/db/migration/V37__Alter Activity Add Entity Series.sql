ALTER TABLE `entity_processor`.`entity_processor_activities`
    MODIFY COLUMN `OBJECT_ENTITY_SERIES` ENUM (
        'XXX',
        'TICKET',
        'OWNER',
        'PRODUCT',
        'PRODUCT_TEMPLATE',
        'PRODUCT_COMM',
        'STAGE',
        'SIMPLE_RULE',
        'COMPLEX_RULE',
        'SIMPLE_COMPLEX_CONDITION_RELATION',
        'PRODUCT_STAGE_RULE',
        'PRODUCT_TEMPLATE_RULE',
        'TASK',
        'TASK_TYPE',
        'NOTE',
        'ACTIVITY',
        'CAMPAIGN',
        'PARTNER',
        'PRODUCT_TEMPLATE_WALK_IN_FORMS',
        'PRODUCT_WALK_IN_FORMS'
        ) NOT NULL DEFAULT 'XXX' COMMENT 'Entity Series of the object associated with this Activity.';
