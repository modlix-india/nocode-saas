DROP TABLE IF EXISTS `entity_processor`.`entity_processor_ticket_c_user_distributions`;
DROP TABLE IF EXISTS `entity_processor`.`entity_processor_product_ticket_c_rules`;

CREATE TABLE `entity_processor`.`entity_processor_product_ticket_c_rules`
(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Rule Config was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Rule Config.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the Rule Config.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Rule Config.',
    `PRODUCT_ID` BIGINT UNSIGNED NULL COMMENT 'Product id related to this Rule Config.',
    `PRODUCT_TEMPLATE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Product Template id related to this Rule Config.',
    `STAGE_ID` BIGINT UNSIGNED NULL COMMENT 'Stage Id to which this Rule config is assigned',
    `LAST_ASSIGNED_USER_ID` BIGINT UNSIGNED NULL COMMENT 'Last user id used in this Rule.',
    `ORDER` INT UNSIGNED NOT NULL COMMENT 'Order of execution of this Rule for a stage',
    `USER_DISTRIBUTION_TYPE` ENUM ('ROUND_ROBIN', 'PERCENTAGE', 'WEIGHTED', 'LOAD_BALANCED', 'PRIORITY_QUEUE', 'RANDOM', 'HYBRID') NOT NULL DEFAULT 'ROUND_ROBIN' COMMENT 'User distribution strategy for this Rule.',
    `CONDITION` JSON NOT NULL COMMENT 'Condition to match for Rule',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this product Rule config.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product Rule config is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PTCR_CODE` (`CODE`),
    UNIQUE KEY `UK2_TDR_AC_CC_PID_ORDER` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_ID`, `ORDER`),
    UNIQUE KEY `UK2_TDR_AC_CC_PTID_ORDER` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_TEMPLATE_ID`, `ORDER`),
    CONSTRAINT `FK1_PTCR_PID` FOREIGN KEY (`PRODUCT_ID`)
        REFERENCES `entity_processor_products` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK1_PTCR_PTID` FOREIGN KEY (`PRODUCT_TEMPLATE_ID`)
        REFERENCES `entity_processor_product_templates` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_PTCR_SID` FOREIGN KEY (`STAGE_ID`)
        REFERENCES `entity_processor_stages` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `UTF8MB4`
  COLLATE = `UTF8MB4_UNICODE_CI`;

CREATE TABLE `entity_processor`.`entity_processor_ticket_c_user_distributions`
(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Rule Config was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Rule Config.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the Rule Config.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Rule Config.',
    `RULE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Rule id related to this User Distribution.',
    `USER_ID` BIGINT UNSIGNED NULL COMMENT 'User id related to this User Distribution.',
    `ROLE_ID` BIGINT UNSIGNED NULL COMMENT 'Role id related to this User Distribution.',
    `PROFILE_ID` BIGINT UNSIGNED NULL COMMENT 'Profile id related to this User Distribution.',
    `DESIGNATION_ID` BIGINT UNSIGNED NULL COMMENT 'Designation id related to this User Distribution.',
    `DEPARTMENT_ID` BIGINT UNSIGNED NULL COMMENT 'Department id related to this User Distribution.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this product Rule config.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product Rule config is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_TCUD_CODE` (`CODE`),
    UNIQUE KEY `UK2_TCUD_CODE_USER_ID` (`RULE_ID`, `USER_ID`),
    UNIQUE KEY `UK3_TCUD_CODE_ROLE_ID` (`RULE_ID`, `ROLE_ID`),
    UNIQUE KEY `UK4_TCUD_CODE_PROFILE_ID` (`RULE_ID`, `PROFILE_ID`),
    UNIQUE KEY `UK5_TCUD_CODE_DESIGNATION_ID` (`RULE_ID`, `DESIGNATION_ID`),
    UNIQUE KEY `UK6_TCUD_CODE_DEPARTMENT_ID` (`RULE_ID`, `DEPARTMENT_ID`),
    CONSTRAINT `FK1_TCUD_RULE_ID` FOREIGN KEY (`RULE_ID`)
        REFERENCES `entity_processor_product_ticket_c_rules` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `UTF8MB4`
  COLLATE = `UTF8MB4_UNICODE_CI`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_ticket_ru_user_distributions`;
DROP TABLE IF EXISTS `entity_processor`.`entity_processor_product_ticket_ru_rules`;

CREATE TABLE `entity_processor`.`entity_processor_product_ticket_ru_rules`
(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Rule Config was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Rule Config.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the Rule Config.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Rule Config.',
    `PRODUCT_ID` BIGINT UNSIGNED NULL COMMENT 'Product id related to this Rule Config.',
    `PRODUCT_TEMPLATE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Product Template id related to this Rule Config.',
    `CAN_EDIT` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to check if for this rule ticket can be edited or not.',
    `ORDER` INT UNSIGNED NOT NULL COMMENT 'Order of execution of this Rule for a stage',
    `USER_DISTRIBUTION_TYPE` ENUM ('ROUND_ROBIN', 'PERCENTAGE', 'WEIGHTED', 'LOAD_BALANCED', 'PRIORITY_QUEUE', 'RANDOM', 'HYBRID') NOT NULL DEFAULT 'ROUND_ROBIN' COMMENT 'User distribution strategy for this Rule.',
    `CONDITION` JSON NOT NULL COMMENT 'Condition to match for Rule',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this product Rule config.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product Rule config is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PTRUR_CODE` (`CODE`),
    UNIQUE KEY `UK2_TDR_AC_CC_PID_ORDER` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_ID`, `ORDER`),
    UNIQUE KEY `UK2_TDR_AC_CC_PTID_ORDER` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_TEMPLATE_ID`, `ORDER`),
    CONSTRAINT `FK1_PTRUR_PID` FOREIGN KEY (`PRODUCT_ID`)
        REFERENCES `entity_processor_products` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK1_PTRUR_PTID` FOREIGN KEY (`PRODUCT_TEMPLATE_ID`)
        REFERENCES `entity_processor_product_templates` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `UTF8MB4`
  COLLATE = `UTF8MB4_UNICODE_CI`;

CREATE TABLE `entity_processor`.`entity_processor_ticket_ru_user_distributions`
(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Rule Config was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Rule Config.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the Rule Config.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Rule Config.',
    `RULE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Rule id related to this User Distribution.',
    `USER_ID` BIGINT UNSIGNED NULL COMMENT 'User id related to this User Distribution.',
    `ROLE_ID` BIGINT UNSIGNED NULL COMMENT 'Role id related to this User Distribution.',
    `PROFILE_ID` BIGINT UNSIGNED NULL COMMENT 'Profile id related to this User Distribution.',
    `DESIGNATION_ID` BIGINT UNSIGNED NULL COMMENT 'Designation id related to this User Distribution.',
    `DEPARTMENT_ID` BIGINT UNSIGNED NULL COMMENT 'Department id related to this User Distribution.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this product Rule config.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product Rule config is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_TRUUD_CODE` (`CODE`),
    UNIQUE KEY `UK2_TRUUD_CODE_USER_ID` (`RULE_ID`, `USER_ID`),
    UNIQUE KEY `UK3_TRUUD_CODE_ROLE_ID` (`RULE_ID`, `ROLE_ID`),
    UNIQUE KEY `UK4_TRUUD_CODE_PROFILE_ID` (`RULE_ID`, `PROFILE_ID`),
    UNIQUE KEY `UK5_TRUUD_CODE_DESIGNATION_ID` (`RULE_ID`, `DESIGNATION_ID`),
    UNIQUE KEY `UK6_TRUUD_CODE_DEPARTMENT_ID` (`RULE_ID`, `DEPARTMENT_ID`),
    CONSTRAINT `FK1_TRUUD_RULE_ID` FOREIGN KEY (`RULE_ID`)
        REFERENCES `entity_processor_product_ticket_ru_rules` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `UTF8MB4`
  COLLATE = `UTF8MB4_UNICODE_CI`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_ticket_duplication_rules`;

CREATE TABLE `entity_processor`.`entity_processor_ticket_duplication_rules`
(
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
    UNIQUE KEY `UK2_TDR_AC_CC_PID_ORDER` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_ID`, `ORDER`),
    UNIQUE KEY `UK2_TDR_AC_CC_PTID_ORDER` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_TEMPLATE_ID`, `ORDER`),
    INDEX `IDX1_TDR_AC_CC_PID_PTID_S_SS` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_ID`, `PRODUCT_TEMPLATE_ID`, `SOURCE`,
                                          `SUB_SOURCE`),
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
