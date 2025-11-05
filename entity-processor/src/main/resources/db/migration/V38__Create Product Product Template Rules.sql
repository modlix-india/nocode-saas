DROP TABLE IF EXISTS `entity_processor`.`entity_processor_product_template_ticket_c_rules`;

CREATE TABLE `entity_processor`.`entity_processor_product_template_ticket_c_rules` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Product Template Rule Config was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Product Template Rule Config.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the Product Template Rule Config.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Product Template Rule Config.',
    `PRODUCT_TEMPLATE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Product Template ID related to this Product Template Rule Config.',
    `STAGE_ID` BIGINT UNSIGNED NULL COMMENT 'Stage Id to which this Product Template Rule config is assigned',
    `ORDER` INT UNSIGNED NOT NULL COMMENT 'Order of execution of this Rule for a stage',
    `IS_DEFAULT` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to tell weather for this stage this is default Rule or not.',
    `BREAK_AT_FIRST_MATCH` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to check if execution should break at first match.',
    `IS_SIMPLE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to tell weather for this is a simple Rule or not.',
    `IS_COMPLEX` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to tell weather for this is a complex Rule or not.',
    `USER_DISTRIBUTION_TYPE` ENUM ('ROUND_ROBIN', 'PERCENTAGE', 'WEIGHTED', 'LOAD_BALANCED', 'PRIORITY_QUEUE', 'RANDOM', 'HYBRID') NOT NULL DEFAULT 'ROUND_ROBIN' COMMENT 'User distribution strategy for this Rule.',
    `USER_DISTRIBUTIONS` JSON NULL COMMENT 'User distributions for this Rule.',
    `LAST_ASSIGNED_USER_ID` BIGINT UNSIGNED NULL COMMENT 'Last User id used in this Rule.',
    `CONDITION` JSON NOT NULL COMMENT 'Condition to match for Rule',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this Product Template Rule config.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this Product Template Rule config is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PTTCR_CODE` (`CODE`),
    UNIQUE KEY `UK2_PTTCR_AC_CC_PRODUCT_TEMPLATE_ID_ORDER` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_TEMPLATE_ID`, `ORDER`),
    CONSTRAINT `FK1_PTTCR_PRODUCT_TEMPLATE_ID` FOREIGN KEY (`PRODUCT_TEMPLATE_ID`)
        REFERENCES `entity_processor_product_templates` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_PTTCR_STAGE_ID` FOREIGN KEY (`STAGE_ID`)
        REFERENCES `entity_processor_stages` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `UTF8MB4`
  COLLATE = `UTF8MB4_UNICODE_CI`;


DROP TABLE IF EXISTS `entity_processor`.`entity_processor_product_ticket_c_rules`;

CREATE TABLE `entity_processor`.`entity_processor_product_ticket_c_rules` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Product Rule Config was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Product Rule Config.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the Product Rule Config.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Product Rule Config.',
    `ADDED_BY_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this Product Rule Config.',
    `PRODUCT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Product Rule ID related to this Product Rule Config.',
    `STAGE_ID` BIGINT UNSIGNED NULL COMMENT 'Stage Id to which this product Rule config is assigned',
    `ORDER` INT UNSIGNED NOT NULL COMMENT 'Order of execution of this Rule for a stage',
    `IS_DEFAULT` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to tell weather for this stage this is default Rule or not.',
    `BREAK_AT_FIRST_MATCH` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to check if execution should break at first match.',
    `IS_SIMPLE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to tell weather for this is a simple Rule or not.',
    `IS_COMPLEX` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to tell weather for this is a complex Rule or not.',
    `USER_DISTRIBUTION_TYPE` ENUM ('ROUND_ROBIN', 'PERCENTAGE', 'WEIGHTED', 'LOAD_BALANCED', 'PRIORITY_QUEUE', 'RANDOM', 'HYBRID') NOT NULL DEFAULT 'ROUND_ROBIN' COMMENT 'User distribution strategy for this Rule.',
    `USER_DISTRIBUTIONS` JSON NULL COMMENT 'User distributions for this Rule.',
    `LAST_ASSIGNED_USER_ID` BIGINT UNSIGNED NULL COMMENT 'Last User id used in this Rule.',
    `CONDITION` JSON NOT NULL COMMENT 'Condition to match for Rule',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this product Rule config.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product Rule config is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PTCR_CODE` (`CODE`),
    UNIQUE KEY `UK2_PTCR_AC_CC_PRODUCT_TEMPLATE_ID_ORDER` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_ID`, `ORDER`),
    CONSTRAINT `FK1_PTCR_PRODUCT_TEMPLATE_ID` FOREIGN KEY (`PRODUCT_ID`)
        REFERENCES `entity_processor_products` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_PTCR_STAGE_ID` FOREIGN KEY (`STAGE_ID`)
        REFERENCES `entity_processor_stages` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `UTF8MB4`
  COLLATE = `UTF8MB4_UNICODE_CI`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_product_template_ticket_ru_rules`;

CREATE TABLE `entity_processor`.`entity_processor_product_template_ticket_ru_rules` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Product Template Rule Config was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Product Template Rule Config.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the Product Template Rule Config.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Product Template Rule Config.',
    `PRODUCT_TEMPLATE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Product Template ID related to this Product Template Rule Config.',
    `STAGE_ID` BIGINT UNSIGNED NULL COMMENT 'Stage Id to which this Product Template Rule config is assigned',
    `ORDER` INT UNSIGNED NOT NULL COMMENT 'Order of execution of this Rule for a stage',
    `IS_DEFAULT` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to tell weather for this stage this is default Rule or not.',
    `BREAK_AT_FIRST_MATCH` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to check if execution should break at first match.',
    `IS_SIMPLE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to tell weather for this is a simple Rule or not.',
    `IS_COMPLEX` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to tell weather for this is a complex Rule or not.',
    `USER_DISTRIBUTION_TYPE` ENUM ('ROUND_ROBIN', 'PERCENTAGE', 'WEIGHTED', 'LOAD_BALANCED', 'PRIORITY_QUEUE', 'RANDOM', 'HYBRID') NOT NULL DEFAULT 'ROUND_ROBIN' COMMENT 'User distribution strategy for this Rule.',
    `USER_DISTRIBUTIONS` JSON NULL COMMENT 'User distributions for this Rule.',
    `LAST_ASSIGNED_USER_ID` BIGINT UNSIGNED NULL COMMENT 'Last User id used in this Rule.',
    `CONDITION` JSON NOT NULL COMMENT 'Condition to match for Rule',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this Product Template Rule config.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this Product Template Rule config is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PTTRUR_CODE` (`CODE`),
    UNIQUE KEY `UK2_PTTRUR_AC_CC_PRODUCT_TEMPLATE_ID_ORDER` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_TEMPLATE_ID`, `ORDER`),
    CONSTRAINT `FK1_PTTRUR_PRODUCT_TEMPLATE_ID` FOREIGN KEY (`PRODUCT_TEMPLATE_ID`)
        REFERENCES `entity_processor_product_templates` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_PTTRUR_STAGE_ID` FOREIGN KEY (`STAGE_ID`)
        REFERENCES `entity_processor_stages` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `UTF8MB4`
  COLLATE = `UTF8MB4_UNICODE_CI`;


DROP TABLE IF EXISTS `entity_processor`.`entity_processor_product_ticket_ru_rules`;

CREATE TABLE `entity_processor`.`entity_processor_product_ticket_ru_rules` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Product Rule Config was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Product Rule Config.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the Product Rule Config.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Product Rule Config.',
    `ADDED_BY_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this Product Rule Config.',
    `PRODUCT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Product Rule ID related to this Product Rule Config.',
    `OVERRIDE_TEMPLATE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to tell if product template should be overridden or not.',
    `STAGE_ID` BIGINT UNSIGNED NULL COMMENT 'Stage Id to which this product Rule config is assigned',
    `ORDER` INT UNSIGNED NOT NULL COMMENT 'Order of execution of this Rule for a stage',
    `IS_DEFAULT` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to tell weather for this stage this is default Rule or not.',
    `BREAK_AT_FIRST_MATCH` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to check if execution should break at first match.',
    `IS_SIMPLE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to tell weather for this is a simple Rule or not.',
    `IS_COMPLEX` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to tell weather for this is a complex Rule or not.',
    `USER_DISTRIBUTION_TYPE` ENUM ('ROUND_ROBIN', 'PERCENTAGE', 'WEIGHTED', 'LOAD_BALANCED', 'PRIORITY_QUEUE', 'RANDOM', 'HYBRID') NOT NULL DEFAULT 'ROUND_ROBIN' COMMENT 'User distribution strategy for this Rule.',
    `USER_DISTRIBUTIONS` JSON NULL COMMENT 'User distributions for this Rule.',
    `LAST_ASSIGNED_USER_ID` BIGINT UNSIGNED NULL COMMENT 'Last User id used in this Rule.',
    `CONDITION` JSON NOT NULL COMMENT 'Condition to match for Rule',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this product Rule config.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product Rule config is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PTRUR_CODE` (`CODE`),
    UNIQUE KEY `UK2_PTRUR_AC_CC_PRODUCT_TEMPLATE_ID_ORDER` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_ID`, `ORDER`),
    CONSTRAINT `FK1_PTRUR_PRODUCT_TEMPLATE_ID` FOREIGN KEY (`PRODUCT_ID`)
        REFERENCES `entity_processor_products` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_PTRUR_STAGE_ID` FOREIGN KEY (`STAGE_ID`)
        REFERENCES `entity_processor_stages` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `UTF8MB4`
  COLLATE = `UTF8MB4_UNICODE_CI`;
