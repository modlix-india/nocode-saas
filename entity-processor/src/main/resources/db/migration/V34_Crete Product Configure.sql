CREATE TABLE `entity_processor`.`entity_processor_product_walk_in_form`
(

    `ID`                    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`              CHAR(64)         NOT NULL COMMENT 'App Code on which this Product configure was created.',
    `CLIENT_CODE`           CHAR(8)          NOT NULL COMMENT 'Client Code who created this Product configure.',
    `CODE`                  CHAR(22)         NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME`                  VARCHAR(512)     NOT NULL COMMENT 'Name of the Product Template.',
    `DESCRIPTION`           TEXT             NULL COMMENT 'Description for the Product Template.',
    `TEMP_ACTIVE`           TINYINT          NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this product.',
    `PRODUCT_ID`   	        BIGINT UNSIGNED NOT NULL COMMENT 'Product related to this configure.',
    `IS_ACTIVE`             TINYINT          NOT NULL DEFAULT 1 COMMENT 'Flag to check if this product is active or not.',
    `STAGE_ID`              BIGINT UNSIGNED NULL COMMENT 'Status for this product configure.',
    `STATUS_ID`             BIGINT UNSIGNED NULL COMMENT 'Sub Status for this product configure.',
    `ASSIGNMENT_TYPE`       ENUM ('DEAL_FLOW','MANUAL') NOT NULL DEFAULT 'DEAL_FLOW' COMMENT 'Assign type can be Deal flow or Manual',
    `CREATED_BY`            BIGINT UNSIGNED           DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`            TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`            BIGINT UNSIGNED           DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`            TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PRODUCT_CONFIGURE_CODE` (`CODE`),
    CONSTRAINT `FK1_PRODUCT_CONFIGURE_PRODUCT_ID` FOREIGN KEY (`PRODUCT_ID`)
        REFERENCES `entity_processor_products` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
     CONSTRAINT `FK2_PRODUCT_CONFIGURE_STAGE_ID` FOREIGN KEY (`STAGE_ID`)
        REFERENCES `entity_processor_stages` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT `FK3_PRODUCT_CONFIGURE_STATUS_ID` FOREIGN KEY (`STATUS_ID`)
        REFERENCES `entity_processor_stages` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
