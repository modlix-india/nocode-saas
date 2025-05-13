/* Creating Database. */

DROP DATABASE IF EXISTS `entity_processor`;

CREATE DATABASE IF NOT EXISTS `entity_processor` DEFAULT CHARACTER SET `UTF8MB4` COLLATE `UTF8MB4_UNICODE_CI`;

USE `entity_processor`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_value_templates`;

CREATE TABLE `entity_processor`.`entity_processor_value_templates` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Value Template was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Value Template.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` CHAR(32) NOT NULL COMMENT 'Name of the Value Template. Value Template are like value type for product, Entities, model.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Value Template.',
    `ADDED_BY_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this Value Template.',
    `VALUE_TEMPLATE_TYPE` ENUM ('ENTITY', 'PRODUCT') NOT NULL DEFAULT 'PRODUCT' COMMENT 'Type of Value Template.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag fro this product.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_VALUE_TEMPLATES_CODE` (`CODE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_models`;

CREATE TABLE `entity_processor_models` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this notification was sent.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code to whom this notification we sent.',
    `VERSION` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Version of this row.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(512) NOT NULL COMMENT 'Name of the Model. Model can be anything which will have entities. For Example, Lead and opportunity, Epic and Task, Account and lead.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Model.',
    `ADDED_BY_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this Model.',
    `CURRENT_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User to which this Model is assigned.',
    `DIAL_CODE` SMALLINT NULL DEFAULT 91 COMMENT 'Dial code of the phone number this model has.',
    `PHONE_NUMBER` CHAR(15) NULL COMMENT 'Phone number related to this model.',
    `EMAIL` VARCHAR(512) NULL COMMENT 'Email related to this model.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag fro this product.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_MODELS_CODE` (`CODE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_products`;

CREATE TABLE `entity_processor_products` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this notification was sent.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code to whom this notification we sent.',
    `VERSION` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Version of this row.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(512) NOT NULL COMMENT 'Name of the Product. Product can be anything for which Entities will be created. For Example, Projects can be product for Opportunities, Board can be product for Epic.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Product.',
    `ADDED_BY_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this product.',
    `CURRENT_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User to which this Product is assigned.',
    `VALUE_TEMPLATE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Value Template related to this Product.',
    `DEFAULT_SOURCE` CHAR(32) NULL COMMENT 'Default source for this product. This will be value for entity source if source in not inferred.',
    `DEFAULT_STAGE` CHAR(32) NULL COMMENT 'Default stage for this product. This will be value for entity stage if stage in not inferred.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag fro this product.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PRODUCTS_CODE` (`CODE`),
    CONSTRAINT `FK1_PRODUCTS_VALUE_TEMPLATE_ID` FOREIGN KEY (`VALUE_TEMPLATE_ID`)
        REFERENCES `entity_processor_value_templates` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_entities`;

CREATE TABLE `entity_processor_entities` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this notification was sent.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code to whom this notification we sent.',
    `VERSION` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Version of this row.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(512) NOT NULL COMMENT 'Name of the Entity. Entity can be anything which will have a single model. For Example, Opportunity is a entity of Lead , Task is a entity of Epic, Lead is entity of Account.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the entity.',
    `ADDED_BY_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this entity.',
    `CURRENT_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User to which this entity is assigned.',
    `STATUS` CHAR(32) NULL COMMENT 'Status for this entity.',
    `SUB_STATUS` CHAR(32) NULL COMMENT 'Sub Status for this entity.',
    `MODEL_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Model related to this entity.',
    `DIAL_CODE` SMALLINT NULL DEFAULT 91 COMMENT 'Dial code of the phone number this model has.',
    `PHONE_NUMBER` CHAR(15) NULL COMMENT 'Phone number related to this model.',
    `EMAIL` VARCHAR(512) NULL COMMENT 'Email related to this entity.',
    `SOURCE` CHAR(32) NOT NULL COMMENT 'Name of source from where we get this entity.',
    `SUB_SOURCE` CHAR(32) NULL COMMENT 'Name of sub source of source from where we get this entity.',
    `PRODUCT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Product related to this entity.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag fro this product.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_ENTITIES_CODE` (`CODE`),
    CONSTRAINT `FK1_ENTITIES_MODEL_ID` FOREIGN KEY (`MODEL_ID`)
        REFERENCES `entity_processor_models` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_ENTITIES_PRODUCT_ID` FOREIGN KEY (`PRODUCT_ID`)
        REFERENCES `entity_processor_products` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_sources`;

CREATE TABLE `entity_processor`.`entity_processor_sources` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this source was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this source.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` CHAR(32) NOT NULL COMMENT 'Name of the Source. Source are like Social Media, Website, Hoarding.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Source.',
    `ADDED_BY_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this Source.',
    `PLATFORM` ENUM ('GLOBAL', 'PRE_QUALIFICATION', 'QUALIFICATION', 'MAIN') DEFAULT 'GLOBAL' NOT NULL COMMENT 'Platform is where this source will be displayed in CRM, can be GLOBAL, PRE_QUALIFICATION, QUALIFICATION or MAIN.',
    `VALUE_TEMPLATE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Value Template related to this Source.',
    `IS_PARENT` TINYINT NOT NULL DEFAULT 1 NOT NULL COMMENT 'Is this the main Source or not.',
    `PARENT_LEVEL_0` BIGINT UNSIGNED NULL COMMENT 'Parent source for this source.',
    `PARENT_LEVEL_1` BIGINT UNSIGNED NULL COMMENT 'Parent source level 1 for this source.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag fro this product.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_SOURCES_CODE` (`CODE`),
    UNIQUE KEY `UK2_SOURCES_NAME` (`APP_CODE`, `CLIENT_CODE`, `VALUE_TEMPLATE_ID`, `NAME`, `PLATFORM`),
    CONSTRAINT `FK1_SOURCES_VALUE_TEMPLATE_ID` FOREIGN KEY (`VALUE_TEMPLATE_ID`)
        REFERENCES `entity_processor_value_templates` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_SOURCES_PARENT_LEVEL_0` FOREIGN KEY (`PARENT_LEVEL_0`)
        REFERENCES `entity_processor_sources` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT `FK3_SOURCES_PARENT_LEVEL_1` FOREIGN KEY (`PARENT_LEVEL_1`)
        REFERENCES `entity_processor_sources` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_stages`;

CREATE TABLE `entity_processor`.`entity_processor_stages` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Stage was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Stage.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` CHAR(32) NOT NULL COMMENT 'Name of the Stage.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Stage.',
    `ADDED_BY_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this Stage.',
    `VALUE_TEMPLATE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Value Template related to this Stage.',
    `IS_PARENT` TINYINT NOT NULL DEFAULT 1 NOT NULL COMMENT 'Is this the main Source or not.',
    `PARENT_LEVEL_0` BIGINT UNSIGNED NULL COMMENT 'Parent Stage for this stage.',
    `PARENT_LEVEL_1` BIGINT UNSIGNED NULL COMMENT 'Parent stage level 1 for this stage.',
    `PLATFORM` ENUM ('GLOBAL', 'PRE_QUALIFICATION', 'QUALIFICATION', 'MAIN') DEFAULT 'GLOBAL' NOT NULL COMMENT 'Platform is where this stage will be displayed in CRM, can be GLOBAL, PRE_QUALIFICATION, QUALIFICATION or MAIN.',
    `STAGE_TYPE` ENUM ('OPEN', 'CLOSED') DEFAULT 'OPEN' NOT NULL COMMENT 'Stage type can be Open or Closed.',
    `IS_SUCCESS` TINYINT NOT NULL DEFAULT 0 NOT NULL COMMENT 'This flag will tell weather this stage will end in a success or not.',
    `IS_FAILURE` TINYINT NOT NULL DEFAULT 0 NOT NULL COMMENT 'This flag will tell weather this stage will end in a failure or not.',
    `ORDER` SMALLINT NOT NULL DEFAULT 0 NOT NULL COMMENT 'Order in which this Stage will occur for a give client app.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag fro this product.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_STAGES_CODE` (`CODE`),
    UNIQUE KEY `UK2_STAGES_NAME` (`APP_CODE`, `CLIENT_CODE`, `VALUE_TEMPLATE_ID`, `NAME`, `PLATFORM`),
    CONSTRAINT `FK1_STAGES_VALUE_TEMPLATE_ID` FOREIGN KEY (`VALUE_TEMPLATE_ID`)
        REFERENCES `entity_processor_value_templates` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_STAGES_PARENT_LEVEL_0` FOREIGN KEY (`PARENT_LEVEL_0`)
        REFERENCES `entity_processor_stages` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT `FK3_STAGES_PARENT_LEVEL_1` FOREIGN KEY (`PARENT_LEVEL_1`)
        REFERENCES `entity_processor_stages` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_rules`;

CREATE TABLE `entity_processor`.`entity_processor_rules` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Rule was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Rule.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the Rule.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Rule.',
    `ADDED_BY_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this Rule.',
    `VERSION` INT NOT NULL DEFAULT 1 COMMENT 'Version of this rule.',
    `IS_SIMPLE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this rule is simple.',
    `IS_COMPLEX` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to check if this rule is complex.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this rule.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this rule is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_RULES_CODE` (`CODE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_simple_rules`;

CREATE TABLE `entity_processor`.`entity_processor_simple_rules` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Simple Rule was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Simple Rule.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the Simple Rule.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Simple Rule.',
    `ADDED_BY_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this Simple Rule.',
    `VERSION` INT NOT NULL DEFAULT 1 COMMENT 'Version of this Simple Rule.',
    `RULE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Rule ID related to this Simple Rule.',
    `NEGATE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to check if this Simple Rule is negative.',
    `FIELD` VARCHAR(255) NULL COMMENT 'Field name for this Simple Rule.',
    `COMPARISON_OPERATOR` ENUM ('EQUALS', 'LESS_THAN', 'GREATER_THAN', 'LESS_THAN_EQUAL', 'GREATER_THAN_EQUAL', 'IS_TRUE', 'IS_FALSE', 'IS_NULL', 'BETWEEN', 'IN', 'LIKE', 'STRING_LOOSE_EQUAL', 'MATCH', 'MATCH_ALL', 'TEXT_SEARCH') NOT NULL DEFAULT 'EQUALS' COMMENT 'Operator for this Simple Rule.',
    `VALUE` JSON NULL COMMENT 'Value for this Simple Rule.',
    `TO_VALUE` JSON NULL COMMENT 'To value for this Simple Rule.',
    `IS_VALUE_FIELD` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to check if value is a field.',
    `IS_TO_VALUE_FIELD` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to check if to value is a field.',
    `MATCH_OPERATOR` ENUM ('EQUALS', 'LESS_THAN', 'GREATER_THAN', 'LESS_THAN_EQUAL', 'GREATER_THAN_EQUAL', 'IS_TRUE', 'IS_FALSE', 'IS_NULL', 'BETWEEN', 'IN', 'LIKE', 'STRING_LOOSE_EQUAL', 'MATCH', 'MATCH_ALL', 'TEXT_SEARCH') NOT NULL DEFAULT 'EQUALS' COMMENT 'Operator for this Simple Rule.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this Simple Rule.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this Simple Rule is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_SIMPLE_RULES_CODE` (`CODE`),
    CONSTRAINT `FK1_SIMPLE_RULES_RULE_ID` FOREIGN KEY (`RULE_ID`)
        REFERENCES `ENTITY_PROCESSOR_RULES` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_complex_rules`;

CREATE TABLE `entity_processor`.`entity_processor_complex_rules` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Complex Rule was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Complex Rule.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the Complex Rule.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Complex Rule.',
    `ADDED_BY_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this Complex Rule.',
    `VERSION` INT NOT NULL DEFAULT 1 COMMENT 'Version of this Complex Rule.',
    `RULE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Rule ID related to this Complex Rule.',
    `NEGATE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to check if this Complex Rule is negative.',
    `PARENT_CONDITION_ID` BIGINT UNSIGNED NULL COMMENT 'Parent Rule ID for this Complex Rule.',
    `LOGICAL_OPERATOR` ENUM ('AND', 'OR') NOT NULL COMMENT 'Logical operator for this Complex Rule.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this Complex Rule.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this Complex Rule is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_COMPLEX_RULES_CODE` (`CODE`),
    CONSTRAINT `FK1_COMPLEX_RULES_RULE_ID` FOREIGN KEY (`RULE_ID`)
        REFERENCES `ENTITY_PROCESSOR_RULES` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_COMPLEX_RULES_PARENT_ID` FOREIGN KEY (`PARENT_CONDITION_ID`)
        REFERENCES `ENTITY_PROCESSOR_COMPLEX_RULES` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_simple_complex_rule_relations`;

CREATE TABLE `entity_processor`.`entity_processor_simple_complex_rule_relations` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Relation was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Relation.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the Relation.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Relation.',
    `ADDED_BY_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this Relation.',
    `VERSION` INT NOT NULL DEFAULT 1 COMMENT 'Version of this Complex Rule.',
    `COMPLEX_CONDITION_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Complex Rule ID related to this Relation.',
    `SIMPLE_CONDITION_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Simple Rule ID related to this Relation.',
    `ORDER` INT NULL COMMENT 'Order of the Simple Rule in the Complex Rule.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this Relation.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this Relation is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_SIMPLE_COMPLEX_CONDITION_RELATIONS_CODE` (`CODE`),
    CONSTRAINT `FK1_RELATIONS_COMPLEX_CONDITION_ID` FOREIGN KEY (`COMPLEX_CONDITION_ID`)
        REFERENCES `ENTITY_PROCESSOR_COMPLEX_RULES` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_RELATIONS_SIMPLE_CONDITION_ID` FOREIGN KEY (`SIMPLE_CONDITION_ID`)
        REFERENCES `ENTITY_PROCESSOR_SIMPLE_RULES` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_product_rules`;

CREATE TABLE `entity_processor`.`entity_processor_product_rules` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Product Rule Config was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Product Rule Config.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the Product Rule Config.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Product Rule Config.',
    `ADDED_BY_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this Product Rule Config.',
    `PRODUCT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Product Rule ID related to this Product Rule Config.',
    `RULE_TYPE` ENUM ('ENTITY_ASSIGNMENT', 'STAGE_ENTITY_ASSIGNMENT', 'PRODUCT_ASSIGNMENT') NOT NULL COMMENT 'Rule type for this Product Rule Config.',
    `BREAK_AT_FIRST_MATCH` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to check if execution should break at first match.',
    `EXECUTE_ONLY_IF_ALL_PREVIOUS_MATCH` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to check if execution should only happen if all previous rules match.',
    `EXECUTE_ONLY_IF_ALL_PREVIOUS_NOT_MATCH` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to check if execution should only happen if all previous rules do not match.',
    `CONTINUE_ON_NO_MATCH` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if execution should continue on no match.',
    `RULES` JSON NULL COMMENT 'Rules for this Product Rule Config.',
    `USER_DISTRIBUTION_TYPE` ENUM ('ROUND_ROBIN', 'PERCENTAGE', 'WEIGHTED', 'LOAD_BALANCED', 'PRIORITY_QUEUE', 'RANDOM', 'HYBRID') NOT NULL DEFAULT 'ROUND_ROBIN' COMMENT 'User distribution strategy for this rule.',
    `USER_DISTRIBUTIONS` JSON NULL COMMENT 'User distributions for this rule.',
    `LAST_USED_USER_ID` BIGINT UNSIGNED NULL COMMENT 'Last User id used in this rule.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this product rule config.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product rule config is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PRODUCT_RULES_CODE` (`CODE`),
    CONSTRAINT `FK1_PRODUCT_RULES_PRODUCT_ID` FOREIGN KEY (`PRODUCT_ID`)
        REFERENCES `ENTITY_PROCESSOR_PRODUCTS` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
