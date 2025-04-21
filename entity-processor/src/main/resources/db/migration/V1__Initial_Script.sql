/* Creating Database */

DROP DATABASE IF EXISTS `entity_processor`;

CREATE DATABASE IF NOT EXISTS `entity_processor` DEFAULT CHARACTER SET `UTF8MB4` COLLATE `UTF8MB4_UNICODE_CI`;

USE `entity_processor`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_models`;

CREATE TABLE `entity_processor_models` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this notification was sent. References security_app table',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code to whom this notification we sent. References security_user table',
    `VERSION` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Version of this row',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row',
    `NAME` VARCHAR(512) NOT NULL COMMENT 'Name of the Model. Model can be anything which will have entities. For Example, Lead and opportunity, Epic and Task, Account and lead',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Model',
    `ADDED_BY_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this Model',
    `CURRENT_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User to which this Model is assigned',
    `STATUS` CHAR(22) NULL COMMENT 'Status for this model',
    `SUB_STATUS` CHAR(22) NULL COMMENT 'Sub Status for this model',
    `DIAL_CODE` SMALLINT NULL DEFAULT 91 COMMENT 'Dial code of the phone number this model has',
    `PHONE_NUMBER` CHAR(15) NULL COMMENT 'Phone number related to this model',
    `EMAIL` VARCHAR(512) NULL COMMENT 'Email related to this model',
    `SOURCE` CHAR(32) NOT NULL COMMENT 'Name of source from where we get this model',
    `SUB_SOURCE` CHAR(32) NOT NULL COMMENT 'Name of sub source of source from where we get this model',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag fro this product',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product is active or not',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_modelS_CODE` (`CODE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_products`;

CREATE TABLE `entity_processor_products` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this notification was sent. References security_app table',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code to whom this notification we sent. References security_user table',
    `VERSION` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Version of this row',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row',
    `NAME` VARCHAR(512) NOT NULL COMMENT 'Name of the Product. Product can be anything for which Entities will be created. For Example, Projects can be product for Opportunities, Board can be product for Epic',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Product',
    `ADDED_BY_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this product',
    `CURRENT_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User to which this Product is assigned',
    `STATUS` CHAR(32) NULL COMMENT 'Status for this product',
    `SUB_STATUS` CHAR(32) NULL COMMENT 'Sub Status for this product',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag fro this product',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product is active or not',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PRODUCTS_CODE` (`CODE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_entities`;

CREATE TABLE `entity_processor_entities` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this notification was sent. References security_app table',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code to whom this notification we sent. References security_user table',
    `VERSION` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Version of this row',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row',
    `NAME` VARCHAR(512) NOT NULL COMMENT 'Name of the Entity. Entity can be anything which will have a single model. For Example, Opportunity is a entity of Lead , Task is a entity of Epic, Lead is entity of Account',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the entity',
    `ADDED_BY_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User which added this entity',
    `CURRENT_USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User to which this entity is assigned',
    `STATUS` CHAR(32) NULL COMMENT 'Status for this entity',
    `SUB_STATUS` CHAR(32) NULL COMMENT 'Sub Status for this entity',
    `MODEL_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Model related to this entity',
    `DIAL_CODE` SMALLINT NULL DEFAULT 91 COMMENT 'Dial code of the phone number this model has',
    `PHONE_NUMBER` CHAR(15) NULL COMMENT 'Phone number related to this model',
    `EMAIL` VARCHAR(512) NULL COMMENT 'Email related to this entity',
    `SOURCE` CHAR(32) NOT NULL COMMENT 'Name of source from where we get this entity',
    `SUB_SOURCE` CHAR(32) NOT NULL COMMENT 'Name of sub source of source from where we get this entity',
    `PRODUCT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Product related to this entity',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag fro this product',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product is active or not',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

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
