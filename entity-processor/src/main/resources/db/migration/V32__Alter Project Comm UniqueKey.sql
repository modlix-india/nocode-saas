TRUNCATE `entity_processor`.`entity_processor_product_comms`;

ALTER TABLE `entity_processor`.`entity_processor_product_comms`
    MODIFY `PRODUCT_ID` BIGINT UNSIGNED NULL COMMENT 'Product ID for which this Comm is created.';

ALTER TABLE `entity_processor`.`entity_processor_product_comms`
    ADD COLUMN `CONNECTION_SUB_TYPE` CHAR(50) NOT NULL COMMENT 'Sub Type of the connection used for the productComm.' AFTER `CONNECTION_TYPE`;

ALTER TABLE `entity_processor`.`entity_processor_product_comms`
    DROP INDEX `UK2_PRODUCT_COMMS_AC_CC_P_C_CT_S_SS`;

ALTER TABLE `entity_processor`.`entity_processor_product_comms`
    ADD CONSTRAINT `UK2_PRODUCT_COMMS_AC_CC_CT_CST_PH`
        UNIQUE (`APP_CODE`, `CLIENT_CODE`, `CONNECTION_TYPE`, `CONNECTION_SUB_TYPE`, `DIAL_CODE`, `PHONE_NUMBER`);

ALTER TABLE `entity_processor`.`entity_processor_product_comms`
    ADD CONSTRAINT `UK3_PRODUCT_COMMS_AC_CC_CT_CST_E`
        UNIQUE (`APP_CODE`, `CLIENT_CODE`, `CONNECTION_TYPE`, CONNECTION_SUB_TYPE, `EMAIL`);
