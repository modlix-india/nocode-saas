DROP TABLE IF EXISTS `entity_processor`.`entity_processor_flow_schema`;

CREATE TABLE `entity_processor`.`entity_processor_flow_schema` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this flow Schema was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who added this flow Schema.',
    `ENTITY_NAME` CHAR(128) NOT NULL COMMENT 'Name of the entity for which this flow Schema is created.',
    `DB_SCHEMA_NAME` CHAR(22) NOT NULL DEFAULT 'entity_processor' COMMENT 'Schema of DB for this flow Schema.',
    `DB_TABLE_NAME` CHAR(128) NOT NULL COMMENT 'Name of the table in this this entity is present for flow Schema.',
    `DB_ENTITY_PK_FIELD_NAME` CHAR(128) NOT NULL COMMENT 'Name of the field in this table which is primary key.',
    `DB_ENTITY_PK_ID` BIGINT UNSIGNED NULL COMMENT 'ID for Related entity of table for which this flow schema is created.',
    `SCHEMA_JSON` JSON NOT NULL COMMENT 'Schema for this flow Schema.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this partner.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this partner is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_FLOW_SCHEMA_AC_CC_S_TN_EPK` (`APP_CODE`, `CLIENT_CODE`, `DB_SCHEMA_NAME`, `DB_TABLE_NAME`, DB_ENTITY_PK_FIELD_NAME, DB_ENTITY_PK_ID)

) ENGINE = InnoDB
  DEFAULT CHARSET = `UTF8MB4`
  COLLATE = `UTF8MB4_UNICODE_CI`;
