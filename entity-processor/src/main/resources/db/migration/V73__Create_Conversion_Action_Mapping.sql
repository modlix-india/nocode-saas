USE `entity_processor`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_conversion_action_mapping`;

CREATE TABLE entity_processor_conversion_action_mapping
(
    `ID`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `CODE`                CHAR(22)        NOT NULL COMMENT 'Unique Code to identify this row.',
    `APP_CODE`            CHAR(64)        NOT NULL COMMENT 'AppCode this mapping belongs to.',
    `CLIENT_CODE`         CHAR(8)         NOT NULL COMMENT 'ClientCode this mapping belongs to.',
    `PRODUCT_TEMPLATE_ID` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'NULL = applies to all product templates for this client; otherwise scoped to one template.',
    `CAMPAIGN_PLATFORM`   ENUM ('GOOGLE','FACEBOOK','LINKEDIN','X') NOT NULL COMMENT 'Ad platform this mapping fires to.',
    `TRIGGER_STAGE_ID`    BIGINT UNSIGNED NOT NULL COMMENT 'Stage whose entry triggers this conversion event.',
    `TRIGGER_STATUS_ID`   BIGINT UNSIGNED          DEFAULT NULL COMMENT 'Sub-status (also a stage row) that further narrows the trigger. NULL = any status under TRIGGER_STAGE_ID.',
    `EVENT_NAME`          VARCHAR(64)     NOT NULL COMMENT 'Platform-specific event name (e.g. Meta: Lead/Contact/Schedule; Google: conversion action label).',
    `PLATFORM_ACTION_ID`  VARCHAR(128)    NOT NULL COMMENT 'Platform-side identifier: Meta custom_conversion_id; Google conversion action resource name.',
    `DEFAULT_VALUE`       DECIMAL(12, 2)           DEFAULT NULL COMMENT 'Default conversion value to send when ticket has no resolvable VALUE_FIELD_PATH.',
    `CURRENCY`            CHAR(3)                  DEFAULT 'INR' COMMENT 'ISO currency code for the conversion value.',
    `VALUE_FIELD_PATH`    VARCHAR(255)             DEFAULT NULL COMMENT 'Optional JSONPath into ticket fields to pull deal value (e.g. $.metaData.propertyPrice). Falls back to DEFAULT_VALUE if missing.',
    `TEST_EVENT_CODE`     VARCHAR(64)              DEFAULT NULL COMMENT 'Optional Meta Test Events code; when set, events are routed to Meta Test Events tool instead of production.',
    `TEMP_ACTIVE`         TINYINT         NOT NULL DEFAULT 0 COMMENT 'Temporary active flag.',
    `IS_ACTIVE`           TINYINT         NOT NULL DEFAULT 1 COMMENT 'Flag to check if this mapping is active.',
    `CREATED_BY`          BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`          BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_CONVERSION_ACTION_MAPPING_CODE` (`CODE`),
    UNIQUE KEY `UK2_CAM_AC_CC_PT_PLATFORM_STAGE_STATUS` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_TEMPLATE_ID`, `CAMPAIGN_PLATFORM`, `TRIGGER_STAGE_ID`, `TRIGGER_STATUS_ID`),
    INDEX `IDX1_CAM_AC_CC` (`APP_CODE`, `CLIENT_CODE`),
    INDEX `IDX2_CAM_TRIGGER` (`APP_CODE`, `CLIENT_CODE`, `TRIGGER_STAGE_ID`, `TRIGGER_STATUS_ID`, `IS_ACTIVE`),
    CONSTRAINT `FK1_CAM_TRIGGER_STAGE_ID` FOREIGN KEY (`TRIGGER_STAGE_ID`)
        REFERENCES `entity_processor`.`entity_processor_stages` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_CAM_TRIGGER_STATUS_ID` FOREIGN KEY (`TRIGGER_STATUS_ID`)
        REFERENCES `entity_processor`.`entity_processor_stages` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK3_CAM_PRODUCT_TEMPLATE_ID` FOREIGN KEY (`PRODUCT_TEMPLATE_ID`)
        REFERENCES `entity_processor`.`entity_processor_product_templates` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
