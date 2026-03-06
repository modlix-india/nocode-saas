DROP TABLE IF EXISTS `entity_processor`.`entity_processor_product_message_configs`;

CREATE TABLE `entity_processor`.`entity_processor_product_message_configs`
(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this config was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this config.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the Product Message Config.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Product Message Config.',

    `PRODUCT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Product id related to this config.',
    `STAGE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Stage id to which this config is assigned.',
    `STATUS_ID` BIGINT UNSIGNED NULL COMMENT 'Status id to which this config is assigned.',

    `ORDER` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Order of execution of this config.',

    `MESSAGE_CHANNEL_TYPE` ENUM (
        'DISABLED',
        'CALL',
        'WHATS_APP',
        'WHATS_APP_TEMPLATE',
        'IN_APP',
        'TEXT'
        ) NOT NULL COMMENT 'Message channel.',

    `MESSAGE_TEMPLATE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'ID of the message/template in message service.',

    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this config.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this config is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PMC_CODE` (`CODE`),
    UNIQUE KEY `UK2_PMC_AC_CC_PID_S_S_MCT_O`
        (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_ID`, `STAGE_ID`, `STATUS_ID`, `MESSAGE_CHANNEL_TYPE`, `ORDER`),

    CONSTRAINT `FK1_PMC_PRODUCT_ID` FOREIGN KEY (`PRODUCT_ID`)
        REFERENCES `entity_processor_products` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    CONSTRAINT `FK2_PMC_STAGE_ID` FOREIGN KEY (`STAGE_ID`)
        REFERENCES `entity_processor_stages` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    CONSTRAINT `FK3_PMC_STATUS_ID` FOREIGN KEY (`STATUS_ID`)
        REFERENCES `entity_processor_stages` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `UTF8MB4`
  COLLATE = `UTF8MB4_UNICODE_CI`;
