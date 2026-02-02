ALTER TABLE `entity_processor`.`entity_processor_product_comms`
    DROP INDEX `IDX4_PRODUCT_COMMS_AC_CC_PRODUCT_DIAL_PHONE`,
    DROP INDEX `IDX5_PRODUCT_COMMS_AC_CC_PRODUCT_EMAIL`;

ALTER TABLE `entity_processor_product_comms`
    ADD COLUMN `SOURCE` CHAR(32) NULL COMMENT 'Name of source form where we get this ticket.' AFTER `IS_DEFAULT`,
    ADD COLUMN `SUB_SOURCE` CHAR(32) NULL COMMENT 'Name of sub source of source form where we get this ticket.' AFTER `SOURCE`;

ALTER TABLE `entity_processor_product_comms`
    ADD CONSTRAINT `UK2_PRODUCT_COMMS_AC_CC_P_S_SS_DIAL_PHONE`
        UNIQUE (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_ID`, `SOURCE`, `SUB_SOURCE`, `DIAL_CODE`, `PHONE_NUMBER`);

ALTER TABLE `entity_processor_product_comms`
    ADD CONSTRAINT `UK2_PRODUCT_COMMS_AC_CC_P_S_SS_EMAIL`
        UNIQUE (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_ID`, `SOURCE`, `SUB_SOURCE`, `EMAIL`);

CREATE INDEX `IDX4_PRODUCT_COMMS_AC_CC_P_S_SS_DIAL_PHONE`
    ON `entity_processor_product_comms` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_ID`, `SOURCE`, `SUB_SOURCE`, `DIAL_CODE`,
                                         `PHONE_NUMBER`);

CREATE INDEX `IDX5_PRODUCT_COMMS_AC_CC_P_S_SS_EMAIL`
    ON `entity_processor_product_comms` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_ID`, `SOURCE`, `SUB_SOURCE`, `EMAIL`);

ALTER TABLE `entity_processor`.`entity_processor_product_comms`
    ADD COLUMN `VERSION` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Version of this row.' AFTER `DESCRIPTION`,
    ADD COLUMN `CLIENT_ID` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Id of client who created this product.' AFTER `IS_ACTIVE`;


