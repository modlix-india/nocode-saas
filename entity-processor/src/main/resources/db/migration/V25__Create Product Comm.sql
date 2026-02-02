DROP TABLE IF EXISTS `entity_processor`.`entity_processor_product_comm`;

CREATE TABLE `entity_processor`.`entity_processor_product_comm` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Product Comm was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who added this Product Comm.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(512) NOT NULL COMMENT 'Name of the Product Comm.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Product Comm.',
    `CONNECTION_NAME` VARCHAR(255) NOT NULL COMMENT 'Name of the connection used for the productComm.',
    `CONNECTION_TYPE` CHAR(50) NOT NULL COMMENT 'Type of the connection used for the productComm.',
    `PRODUCT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Product ID for which this Comm is created.',
    `DIAL_CODE` SMALLINT DEFAULT 91 NULL COMMENT 'Dial code of the phone number this Product Comm has.',
    `PHONE_NUMBER` CHAR(15) NULL COMMENT 'Phone number related to this Product Comm.',
    `EMAIL` VARCHAR(512) NULL COMMENT 'Email related to this Product Comm.',
    `IS_DEFAULT` TINYINT NOT NULL DEFAULT 0 COMMENT 'Flag to check if this Product Comm is default or not.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this Product Comm.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this Product Comm is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PRODUCT_COMMS_CODE` (`CODE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
