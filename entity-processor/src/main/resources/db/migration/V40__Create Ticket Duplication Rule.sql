DROP TABLE IF EXISTS `entity_processor`.`entity_processor_ticket_duplication_rules`;

CREATE TABLE `entity_processor`.`entity_processor_ticket_duplication_rules` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Rule Config was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Rule Config.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the Rule Config.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Rule Config.',
    `PRODUCT_ID` BIGINT UNSIGNED NULL COMMENT 'Product id related to this Rule Config.',
    `PRODUCT_TEMPLATE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Product Template id related to this Rule Config.',
    `SOURCE` CHAR(64) NOT NULL COMMENT 'Source on which this rule will be applied.',
    `SUB_SOURCE` CHAR(64) NULL COMMENT 'Sub Source on which this rule will be applied.',
    `MAX_STAGE_ID` BIGINT UNSIGNED NULL COMMENT 'Max Stage Id to which this Rule config is assigned. Max is based on order of stage.',
    `MAX_ENTITY_CREATION` BIGINT UNSIGNED NULL COMMENT 'Max number of duplicate entities to be created for a single ticket according to this rule.',
    `ORDER` INT UNSIGNED NOT NULL COMMENT 'Order of execution of this Rule for a stage.',
    `CONDITION` JSON NOT NULL COMMENT 'Condition to match for Rule of incoming object.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this product Rule config.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product Rule config is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_TDR_CODE` (`CODE`),
    UNIQUE KEY `UK2_TDR_AC_CC_PID_PTID_ORDER` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_ID`, `PRODUCT_TEMPLATE_ID`, `ORDER`),
    INDEX `IDX1_TDR_AC_CC_PID_PTID_S_SS` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_ID`, `PRODUCT_TEMPLATE_ID`, `SOURCE`, `SUB_SOURCE`),
    INDEX `IDX2_TDR_AC_CC_PTID_PT_S_SS` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_TEMPLATE_ID`, `SOURCE`, `SUB_SOURCE`),
    CONSTRAINT `FK1_TDR_PID` FOREIGN KEY (`PRODUCT_ID`)
        REFERENCES `entity_processor_products` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK1_TDR_PTID` FOREIGN KEY (`PRODUCT_TEMPLATE_ID`)
        REFERENCES `entity_processor_product_templates` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_TDR_MSID` FOREIGN KEY (`MAX_STAGE_ID`)
        REFERENCES `entity_processor_stages` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `UTF8MB4`
  COLLATE = `UTF8MB4_UNICODE_CI`;
