/* Creating Database. */

-- DROP DATABASE IF EXISTS `entity_processor`;

CREATE DATABASE IF NOT EXISTS `entity_processor` DEFAULT CHARACTER SET `UTF8MB4` COLLATE `UTF8MB4_UNICODE_CI`;

USE `entity_processor`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_product_templates`;

CREATE TABLE `entity_processor`.`entity_processor_product_templates`
(

    `ID`                    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`              CHAR(64)         NOT NULL COMMENT 'App Code on which this Product Template was created.',
    `CLIENT_CODE`           CHAR(8)          NOT NULL COMMENT 'Client Code who created this Product Template.',
    `CODE`                  CHAR(22)         NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME`                  VARCHAR(512)     NOT NULL COMMENT 'Name of the Product Template.',
    `DESCRIPTION`           TEXT             NULL COMMENT 'Description for the Product Template.',
    `PRODUCT_TEMPLATE_TYPE` ENUM ('GENERAL') NOT NULL DEFAULT 'GENERAL' COMMENT 'Type of Product Template.',
    `TEMP_ACTIVE`           TINYINT          NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this product.',
    `IS_ACTIVE`             TINYINT          NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product is active or not.',
    `CREATED_BY`            BIGINT UNSIGNED           DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`            TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`            BIGINT UNSIGNED           DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`            TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PRODUCT_TEMPLATES_CODE` (`CODE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;


DROP TABLE IF EXISTS `entity_processor`.`entity_processor_stages`;

CREATE TABLE `entity_processor`.`entity_processor_stages`
(

    `ID`                  BIGINT UNSIGNED                                  NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`            CHAR(64)                                         NOT NULL COMMENT 'App Code on which this Stage was created.',
    `CLIENT_CODE`         CHAR(8)                                          NOT NULL COMMENT 'Client Code who created this Stage.',
    `CODE`                CHAR(22)                                         NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME`                VARCHAR(512)                                     NOT NULL COMMENT 'Name of the Stage.',
    `DESCRIPTION`         TEXT                                             NULL COMMENT 'Description for the Stage.',
    `PLATFORM`            ENUM ('PRE_QUALIFICATION', 'POST_QUALIFICATION') NOT NULL DEFAULT 'PRE_QUALIFICATION' COMMENT 'Platform is where this stage will be displayed in CRM, can be PRE_QUALIFICATION, POST_QUALIFICATION.',
    `PRODUCT_TEMPLATE_ID` BIGINT UNSIGNED                                  NOT NULL COMMENT 'Product Template related to this Stage.',
    `IS_PARENT`           TINYINT                                          NOT NULL DEFAULT 1 COMMENT 'Is this the main Source or not.',
    `PARENT_LEVEL_0`      BIGINT UNSIGNED                                  NULL COMMENT 'Parent Stage for this stage.',
    `PARENT_LEVEL_1`      BIGINT UNSIGNED                                  NULL COMMENT 'Parent stage level 1 for this stage.',
    `ORDER`               INTEGER                                          NOT NULL DEFAULT 0 COMMENT 'Order in which this Stage will occur for a given client app.',
    `STAGE_TYPE`          ENUM ('OPEN', 'CLOSED')                          NOT NULL DEFAULT 'OPEN' COMMENT 'Stage type can be Open or Closed.',
    `IS_SUCCESS`          TINYINT                                          NULL COMMENT 'This flag will tell whether this stage will end in a success or not.',
    `IS_FAILURE`          TINYINT                                          NULL COMMENT 'This flag will tell whether this stage will end in a failure or not.',
    `TEMP_ACTIVE`         TINYINT                                          NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this product.',
    `IS_ACTIVE`           TINYINT                                          NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product is active or not.',
    `CREATED_BY`          BIGINT UNSIGNED                                           DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`          TIMESTAMP                                        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`          BIGINT UNSIGNED                                           DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`          TIMESTAMP                                        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_STAGES_CODE` (`CODE`),
    UNIQUE KEY `UK2_STAGES_NAME` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_TEMPLATE_ID`, `NAME`, `PLATFORM`),
    CONSTRAINT `FK1_STAGES_PRODUCT_TEMPLATE_ID` FOREIGN KEY (`PRODUCT_TEMPLATE_ID`)
        REFERENCES `entity_processor_product_templates` (`ID`)
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


DROP TABLE IF EXISTS `entity_processor`.`entity_processor_owners`;

CREATE TABLE `entity_processor_owners`
(

    `ID`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`     CHAR(64)        NOT NULL COMMENT 'App Code on which this notification was sent.',
    `CLIENT_CODE`  CHAR(8)         NOT NULL COMMENT 'Client Code to whom this notification we sent.',
    `CODE`         CHAR(22)        NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME`         VARCHAR(512)    NOT NULL COMMENT 'Name of the Owner. Owner can be anything which will have entities. For Example, Lead and opportunity, Epic and Task, Account and lead.',
    `DESCRIPTION`  TEXT            NULL COMMENT 'Description for the Owner.',
    `VERSION`      BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Version of this row.',
    `DIAL_CODE`    SMALLINT        NULL     DEFAULT 91 COMMENT 'Dial code of the phone number this owner has.',
    `PHONE_NUMBER` CHAR(15)        NULL COMMENT 'Phone number related to this owner.',
    `EMAIL`        VARCHAR(512)    NULL COMMENT 'Email related to this owner.',
    `SOURCE`       CHAR(64)        NOT NULL COMMENT 'Source of this owner',
    `SUB_SOURCE`   CHAR(64)        NULL COMMENT 'Sub Source of this owner.',
    `TEMP_ACTIVE`  TINYINT         NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this product.',
    `IS_ACTIVE`    TINYINT         NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product is active or not.',
    `CREATED_BY`   BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`   BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_OWNERS_CODE` (`CODE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;


DROP TABLE IF EXISTS `entity_processor`.`entity_processor_products`;

CREATE TABLE `entity_processor_products`
(

    `ID`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`            CHAR(64)        NOT NULL COMMENT 'App Code on which this notification was sent.',
    `CLIENT_CODE`         CHAR(8)         NOT NULL COMMENT 'Client Code to whom this notification we sent.',
    `CODE`                CHAR(22)        NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME`                VARCHAR(512)    NOT NULL COMMENT 'Name of the Product. Product can be anything for which Tickets will be created. For Example, Projects can be product for Opportunities, Board can be product for Epic.',
    `DESCRIPTION`         TEXT            NULL COMMENT 'Description for the Product.',
    `VERSION`             BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Version of this row.',
    `PRODUCT_TEMPLATE_ID` BIGINT UNSIGNED NULL COMMENT 'Product Template related to this Product.',
    `TEMP_ACTIVE`         TINYINT         NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this product.',
    `IS_ACTIVE`           TINYINT         NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product is active or not.',
    `CREATED_BY`          BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`          BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PRODUCTS_CODE` (`CODE`),
    CONSTRAINT `FK1_PRODUCTS_PRODUCT_TEMPLATE_ID` FOREIGN KEY (`PRODUCT_TEMPLATE_ID`)
        REFERENCES `entity_processor_product_templates` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;


DROP TABLE IF EXISTS `entity_processor`.`entity_processor_tickets`;

CREATE TABLE `entity_processor_tickets`
(

    `ID`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`         CHAR(64)        NOT NULL COMMENT 'App Code on which this notification was sent.',
    `CLIENT_CODE`      CHAR(8)         NOT NULL COMMENT 'Client Code to whom this notification we sent.',
    `CODE`             CHAR(22)        NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME`             VARCHAR(512)    NOT NULL COMMENT 'Name of the Ticket. Ticket can be anything which will have a single owner. For Example, Opportunity is a ticket of Lead , Task is a ticket of Epic, Lead is ticket of Account.',
    `DESCRIPTION`      TEXT            NULL COMMENT 'Description for the ticket.',
    `VERSION`          BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Version of this row.',
    `OWNER_ID`         BIGINT UNSIGNED NOT NULL COMMENT 'Owner related to this ticket.',
    `ASSIGNED_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this ticket or user who is assigned to this ticket.',
    `DIAL_CODE`        SMALLINT        NULL     DEFAULT 91 COMMENT 'Dial code of the phone number this owner has.',
    `PHONE_NUMBER`     CHAR(15)        NULL COMMENT 'Phone number related to this owner.',
    `EMAIL`            VARCHAR(512)    NULL COMMENT 'Email related to this ticket.',
    `PRODUCT_ID`       BIGINT UNSIGNED NOT NULL COMMENT 'Product related to this ticket.',
    `STAGE`            BIGINT UNSIGNED NULL COMMENT 'Status for this ticket.',
    `STATUS`           BIGINT UNSIGNED NULL COMMENT 'Sub Status for this ticket.',
    `SOURCE`           CHAR(32)        NOT NULL COMMENT 'Name of source form where we get this ticket.',
    `SUB_SOURCE`       CHAR(32)        NULL COMMENT 'Name of sub source of source form where we get this ticket.',
    `TEMP_ACTIVE`      TINYINT         NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this product.',
    `IS_ACTIVE`        TINYINT         NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product is active or not.',
    `CREATED_BY`       BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`       BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_TICKETS_CODE` (`CODE`),
    CONSTRAINT `FK1_TICKETS_OWNER_ID` FOREIGN KEY (`OWNER_ID`)
        REFERENCES `entity_processor_owners` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_TICKETS_PRODUCT_ID` FOREIGN KEY (`PRODUCT_ID`)
        REFERENCES `entity_processor_products` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT `FK3_TICKETS_STAGE_ID` FOREIGN KEY (`STAGE`)
        REFERENCES `entity_processor_stages` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT `FK4_TICKETS_STATUS_ID` FOREIGN KEY (`STATUS`)
        REFERENCES `entity_processor_stages` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;


DROP TABLE IF EXISTS `entity_processor`.`entity_processor_product_template_rules`;

CREATE TABLE `entity_processor`.`entity_processor_product_template_rules`
(

    `ID`                     BIGINT UNSIGNED                                                                                       NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`               CHAR(64)                                                                                              NOT NULL COMMENT 'App Code on which this Product Template Rule Config was created.',
    `CLIENT_CODE`            CHAR(8)                                                                                               NOT NULL COMMENT 'Client Code who created this Product Template Rule Config.',
    `CODE`                   CHAR(22)                                                                                              NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME`                   VARCHAR(64)                                                                                           NOT NULL COMMENT 'Name of the Product Template Rule Config.',
    `DESCRIPTION`            TEXT                                                                                                  NULL COMMENT 'Description for the Product Template Rule Config.',
    `PRODUCT_TEMPLATE_ID`    BIGINT UNSIGNED                                                                                       NOT NULL COMMENT 'Product Template ID related to this Product Template Rule Config.',
    `STAGE_ID`               BIGINT UNSIGNED                                                                                       NULL COMMENT 'Stage Id to which this Product Template rule config is assigned',
    `ORDER`                  INT UNSIGNED                                                                                          NOT NULL COMMENT 'Order of execution of this rule for a stage',
    `IS_DEFAULT`             TINYINT                                                                                               NOT NULL DEFAULT 0 COMMENT 'Flag to tell weather for this stage this is default rule or not.',
    `BREAK_AT_FIRST_MATCH`   TINYINT                                                                                               NOT NULL DEFAULT 0 COMMENT 'Flag to check if execution should break at first match.',
    `IS_SIMPLE`              TINYINT                                                                                               NOT NULL DEFAULT 1 COMMENT 'Flag to tell weather for this is a simple rule or not.',
    `IS_COMPLEX`             TINYINT                                                                                               NOT NULL DEFAULT 0 COMMENT 'Flag to tell weather for this is a complex rule or not.',
    `USER_DISTRIBUTION_TYPE` ENUM ('ROUND_ROBIN', 'PERCENTAGE', 'WEIGHTED', 'LOAD_BALANCED', 'PRIORITY_QUEUE', 'RANDOM', 'HYBRID') NOT NULL DEFAULT 'ROUND_ROBIN' COMMENT 'User distribution strategy for this rule.',
    `USER_DISTRIBUTIONS`     JSON                                                                                                  NULL COMMENT 'User distributions for this rule.',
    `LAST_ASSIGNED_USER_ID`  BIGINT UNSIGNED                                                                                       NULL COMMENT 'Last User id used in this rule.',
    `TEMP_ACTIVE`            TINYINT                                                                                               NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this Product Template rule config.',
    `IS_ACTIVE`              TINYINT                                                                                               NOT NULL DEFAULT 1 COMMENT 'Flag to check if this Product Template rule config is active or not.',
    `CREATED_BY`             BIGINT UNSIGNED                                                                                                DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`             TIMESTAMP                                                                                             NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`             BIGINT UNSIGNED                                                                                                DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`             TIMESTAMP                                                                                             NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PRODUCT_TEMPLATE_RULES_CODE` (`CODE`),
    UNIQUE KEY `UK2_PRODUCT_TEMPLATE_RULES_PRODUCT_TEMPLATE_ID_STAGE_ID` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_TEMPLATE_ID`, `STAGE_ID`),
    CONSTRAINT `FK1_PRODUCT_TEMPLATE_RULES_PRODUCT_TEMPLATE_ID` FOREIGN KEY (`PRODUCT_TEMPLATE_ID`)
        REFERENCES `entity_processor_product_templates` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_PRODUCT_TEMPLATE_RULES_STAGE_ID` FOREIGN KEY (`STAGE_ID`)
        REFERENCES `entity_processor_stages` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;


DROP TABLE IF EXISTS `entity_processor`.`entity_processor_product_rules`;

CREATE TABLE `entity_processor`.`entity_processor_product_rules`
(

    `ID`                     BIGINT UNSIGNED                                                                                       NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`               CHAR(64)                                                                                              NOT NULL COMMENT 'App Code on which this Product Rule Config was created.',
    `CLIENT_CODE`            CHAR(8)                                                                                               NOT NULL COMMENT 'Client Code who created this Product Rule Config.',
    `CODE`                   CHAR(22)                                                                                              NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME`                   VARCHAR(64)                                                                                           NOT NULL COMMENT 'Name of the Product Rule Config.',
    `DESCRIPTION`            TEXT                                                                                                  NULL COMMENT 'Description for the Product Rule Config.',
    `ADDED_BY_USER_ID`       BIGINT UNSIGNED                                                                                       NOT NULL COMMENT 'User which added this Product Rule Config.',
    `PRODUCT_ID`             BIGINT UNSIGNED                                                                                       NOT NULL COMMENT 'Product Rule ID related to this Product Rule Config.',
    `STAGE_ID`               BIGINT UNSIGNED                                                                                       NULL COMMENT 'Stage Id to which this product rule config is assigned',
    `ORDER`                  INT UNSIGNED                                                                                          NOT NULL COMMENT 'Order of execution of this rule for a stage',
    `IS_DEFAULT`             TINYINT                                                                                               NOT NULL DEFAULT 0 COMMENT 'Flag to tell weather for this stage this is default rule or not.',
    `BREAK_AT_FIRST_MATCH`   TINYINT                                                                                               NOT NULL DEFAULT 0 COMMENT 'Flag to check if execution should break at first match.',
    `IS_SIMPLE`              TINYINT                                                                                               NOT NULL DEFAULT 1 COMMENT 'Flag to tell weather for this is a simple rule or not.',
    `IS_COMPLEX`             TINYINT                                                                                               NOT NULL DEFAULT 0 COMMENT 'Flag to tell weather for this is a complex rule or not.',
    `USER_DISTRIBUTION_TYPE` ENUM ('ROUND_ROBIN', 'PERCENTAGE', 'WEIGHTED', 'LOAD_BALANCED', 'PRIORITY_QUEUE', 'RANDOM', 'HYBRID') NOT NULL DEFAULT 'ROUND_ROBIN' COMMENT 'User distribution strategy for this rule.',
    `USER_DISTRIBUTIONS`     JSON                                                                                                  NULL COMMENT 'User distributions for this rule.',
    `LAST_ASSIGNED_USER_ID`  BIGINT UNSIGNED                                                                                       NULL COMMENT 'Last User id used in this rule.',
    `TEMP_ACTIVE`            TINYINT                                                                                               NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this product rule config.',
    `IS_ACTIVE`              TINYINT                                                                                               NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product rule config is active or not.',
    `CREATED_BY`             BIGINT UNSIGNED                                                                                                DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`             TIMESTAMP                                                                                             NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`             BIGINT UNSIGNED                                                                                                DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`             TIMESTAMP                                                                                             NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PRODUCT_RULES_CODE` (`CODE`),
    UNIQUE KEY `UK2_PRODUCT_RULES_PRODUCT_TEMPLATE_ID_STAGE_ID` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_ID`, `STAGE_ID`, `ORDER`),
    CONSTRAINT `FK1_PRODUCT_RULES_PRODUCT_ID` FOREIGN KEY (`PRODUCT_ID`)
        REFERENCES `entity_processor_products` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_PRODUCT_RULES_STAGE_ID` FOREIGN KEY (`STAGE_ID`)
        REFERENCES `entity_processor_stages` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_simple_rules`;

CREATE TABLE `entity_processor`.`entity_processor_simple_rules`
(

    `ID`                       BIGINT UNSIGNED                                                                                                                                                                                             NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`                 CHAR(64)                                                                                                                                                                                                    NOT NULL COMMENT 'App Code on which this Simple Rule was created.',
    `CLIENT_CODE`              CHAR(8)                                                                                                                                                                                                     NOT NULL COMMENT 'Client Code who created this Simple Rule.',
    `CODE`                     CHAR(22)                                                                                                                                                                                                    NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME`                     VARCHAR(64)                                                                                                                                                                                                 NOT NULL COMMENT 'Name of the Simple Rule.',
    `DESCRIPTION`              TEXT                                                                                                                                                                                                        NULL COMMENT 'Description for the Simple Rule.',
    `VERSION`                  INT                                                                                                                                                                                                         NOT NULL DEFAULT 1 COMMENT 'Version of this Simple Rule.',
    `PRODUCT_TEMPLATE_RULE_ID` BIGINT UNSIGNED                                                                                                                                                                                             NULL COMMENT 'Product Template Rule ID related to this Simple Rule.',
    `PRODUCT_STAGE_RULE_ID`    BIGINT UNSIGNED                                                                                                                                                                                             NULL COMMENT 'Product Stage Rule ID related to this Simple Rule.',
    `NEGATE`                   TINYINT                                                                                                                                                                                                     NOT NULL DEFAULT 0 COMMENT 'Flag to check if this Simple Rule is negative.',
    `FIELD`                    VARCHAR(255)                                                                                                                                                                                                NULL COMMENT 'Field name for this Simple Rule.',
    `COMPARISON_OPERATOR`      ENUM ('EQUALS', 'LESS_THAN', 'GREATER_THAN', 'LESS_THAN_EQUAL', 'GREATER_THAN_EQUAL', 'IS_TRUE', 'IS_FALSE', 'IS_NULL', 'BETWEEN', 'IN', 'LIKE', 'STRING_LOOSE_EQUAL', 'MATCH', 'MATCH_ALL', 'TEXT_SEARCH') NOT NULL DEFAULT 'EQUALS' COMMENT 'Operator for this Simple Rule.',
    `VALUE`                    JSON                                                                                                                                                                                                        NULL COMMENT 'Value for this Simple Rule.',
    `TO_VALUE`                 JSON                                                                                                                                                                                                        NULL COMMENT 'To value for this Simple Rule.',
    `IS_VALUE_FIELD`           TINYINT                                                                                                                                                                                                     NOT NULL DEFAULT 0 COMMENT 'Flag to check if value is a field.',
    `IS_TO_VALUE_FIELD`        TINYINT                                                                                                                                                                                                     NOT NULL DEFAULT 0 COMMENT 'Flag to check if to value is a field.',
    `MATCH_OPERATOR`           ENUM ('EQUALS', 'LESS_THAN', 'GREATER_THAN', 'LESS_THAN_EQUAL', 'GREATER_THAN_EQUAL', 'IS_TRUE', 'IS_FALSE', 'IS_NULL', 'BETWEEN', 'IN', 'LIKE', 'STRING_LOOSE_EQUAL', 'MATCH', 'MATCH_ALL', 'TEXT_SEARCH') NOT NULL DEFAULT 'EQUALS' COMMENT 'Operator for this Simple Rule.',
    `TEMP_ACTIVE`              TINYINT                                                                                                                                                                                                     NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this Simple Rule.',
    `IS_ACTIVE`                TINYINT                                                                                                                                                                                                     NOT NULL DEFAULT 1 COMMENT 'Flag to check if this Simple Rule is active or not.',
    `CREATED_BY`               BIGINT UNSIGNED                                                                                                                                                                                                      DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`               TIMESTAMP                                                                                                                                                                                                   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`               BIGINT UNSIGNED                                                                                                                                                                                                      DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`               TIMESTAMP                                                                                                                                                                                                   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_SIMPLE_RULES_CODE` (`CODE`),
    CONSTRAINT `FK1_SIMPLE_RULES_PRODUCT_TEMPLATE_RULE_ID` FOREIGN KEY (`PRODUCT_TEMPLATE_RULE_ID`)
        REFERENCES `entity_processor_product_template_rules` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_SIMPLE_RULES_PRODUCT_STAGE_RULE_ID` FOREIGN KEY (`PRODUCT_STAGE_RULE_ID`)
        REFERENCES `entity_processor_product_rules` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;


DROP TABLE IF EXISTS `entity_processor`.`entity_processor_complex_rules`;

CREATE TABLE `entity_processor`.`entity_processor_complex_rules`
(

    `ID`                       BIGINT UNSIGNED    NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`                 CHAR(64)           NOT NULL COMMENT 'App Code on which this Complex Rule was created.',
    `CLIENT_CODE`              CHAR(8)            NOT NULL COMMENT 'Client Code who created this Complex Rule.',
    `CODE`                     CHAR(22)           NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME`                     VARCHAR(64)        NOT NULL COMMENT 'Name of the Complex Rule.',
    `DESCRIPTION`              TEXT               NULL COMMENT 'Description for the Complex Rule.',
    `ADDED_BY_USER_ID`         BIGINT UNSIGNED    NOT NULL COMMENT 'User which added this Complex Rule.',
    `VERSION`                  INT                NOT NULL DEFAULT 1 COMMENT 'Version of this Complex Rule.',
    `PRODUCT_TEMPLATE_RULE_ID` BIGINT UNSIGNED    NULL COMMENT 'Product Template Rule ID related to this Complex Rule.',
    `PRODUCT_STAGE_RULE_ID`    BIGINT UNSIGNED    NULL COMMENT 'Product Stage Rule ID related to this Complex Rule.',
    `NEGATE`                   TINYINT            NOT NULL DEFAULT 0 COMMENT 'Flag to check if this Complex Rule is negative.',
    `PARENT_CONDITION_ID`      BIGINT UNSIGNED    NULL COMMENT 'Parent Rule ID for this Complex Rule.',
    `LOGICAL_OPERATOR`         ENUM ('AND', 'OR') NOT NULL COMMENT 'Logical operator for this Complex Rule.',
    `TEMP_ACTIVE`              TINYINT            NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this Complex Rule.',
    `IS_ACTIVE`                TINYINT            NOT NULL DEFAULT 1 COMMENT 'Flag to check if this Complex Rule is active or not.',
    `CREATED_BY`               BIGINT UNSIGNED             DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`               TIMESTAMP          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`               BIGINT UNSIGNED             DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`               TIMESTAMP          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_COMPLEX_RULES_CODE` (`CODE`),
    CONSTRAINT `FK1_COMPLEX_RULES_PRODUCT_TEMPLATE_RULE_ID` FOREIGN KEY (`PRODUCT_TEMPLATE_RULE_ID`)
        REFERENCES `entity_processor_product_template_rules` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_COMPLEX_RULES_PRODUCT_STAGE_RULE_ID` FOREIGN KEY (`PRODUCT_STAGE_RULE_ID`)
        REFERENCES `entity_processor_product_rules` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK3_COMPLEX_RULES_PARENT_ID` FOREIGN KEY (`PARENT_CONDITION_ID`)
        REFERENCES `entity_processor_complex_rules` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;


DROP TABLE IF EXISTS `entity_processor`.`entity_processor_simple_complex_rule_relations`;

CREATE TABLE `entity_processor`.`entity_processor_simple_complex_rule_relations`
(

    `ID`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`             CHAR(64)        NOT NULL COMMENT 'App Code on which this Relation was created.',
    `CLIENT_CODE`          CHAR(8)         NOT NULL COMMENT 'Client Code who created this Relation.',
    `CODE`                 CHAR(22)        NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME`                 VARCHAR(64)     NOT NULL COMMENT 'Name of the Relation.',
    `DESCRIPTION`          TEXT            NULL COMMENT 'Description for the Relation.',
    `ADDED_BY_USER_ID`     BIGINT UNSIGNED NOT NULL COMMENT 'User which added this Relation.',
    `VERSION`              INT             NOT NULL DEFAULT 1 COMMENT 'Version of this Complex Rule.',
    `COMPLEX_CONDITION_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Complex Rule ID related to this Relation.',
    `SIMPLE_CONDITION_ID`  BIGINT UNSIGNED NOT NULL COMMENT 'Simple Rule ID related to this Relation.',
    `ORDER`                INT             NULL COMMENT 'Order of the Simple Rule in the Complex Rule.',
    `TEMP_ACTIVE`          TINYINT         NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this Relation.',
    `IS_ACTIVE`            TINYINT         NOT NULL DEFAULT 1 COMMENT 'Flag to check if this Relation is active or not.',
    `CREATED_BY`           BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`           BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_SIMPLE_COMPLEX_CONDITION_RELATIONS_CODE` (`CODE`),
    CONSTRAINT `FK1_RELATIONS_COMPLEX_CONDITION_ID` FOREIGN KEY (`COMPLEX_CONDITION_ID`)
        REFERENCES `entity_processor_complex_rules` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_RELATIONS_SIMPLE_CONDITION_ID` FOREIGN KEY (`SIMPLE_CONDITION_ID`)
        REFERENCES `entity_processor_simple_rules` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
