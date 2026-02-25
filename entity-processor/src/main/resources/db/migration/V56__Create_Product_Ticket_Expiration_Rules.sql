CREATE TABLE `entity_processor`.`entity_processor_product_ticket_ex_rules`
(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Rule was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Rule.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the expiration Rule.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Rule.',
    `PRODUCT_ID` BIGINT UNSIGNED NULL COMMENT 'Product id when rule is product-level override; NULL for template-level.',
    `PRODUCT_TEMPLATE_ID` BIGINT UNSIGNED NULL COMMENT 'Product Template id; required for template-level, same as product template when product-level.',
    `SOURCE` CHAR(32) NOT NULL COMMENT 'Source name to which this expiration rule applies.',
    `EXPIRY_DAYS` INT UNSIGNED NOT NULL COMMENT 'Number of days without activity after which ticket is marked expired.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this rule.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this rule is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PTER_CODE` (`CODE`),
    UNIQUE KEY `UK2_PTER_AC_CC_PTID_SOURCE` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_TEMPLATE_ID`, `SOURCE`),
    UNIQUE KEY `UK3_PTER_AC_CC_PID_SOURCE` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_ID`, `SOURCE`),
    INDEX `IDX1_PTER_AC_CC_PTID` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_TEMPLATE_ID`),
    INDEX `IDX2_PTER_AC_CC_PID` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_ID`),
    CONSTRAINT `FK1_PTER_PID` FOREIGN KEY (`PRODUCT_ID`)
        REFERENCES `entity_processor_products` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK1_PTER_PTID` FOREIGN KEY (`PRODUCT_TEMPLATE_ID`)
        REFERENCES `entity_processor_product_templates` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
