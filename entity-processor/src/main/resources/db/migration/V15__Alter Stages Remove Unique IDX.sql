ALTER TABLE `entity_processor`.`entity_processor_stages`
    DROP KEY `UK2_STAGES_NAME`;

ALTER TABLE `entity_processor`.`entity_processor_stages`
    ADD INDEX `IDX1_STAGE_NAME` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_TEMPLATE_ID`, `NAME`);

ALTER TABLE `entity_processor`.`entity_processor_stages`
    ADD INDEX `IDX2_STAGE_NAME_PLATFORM` (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_TEMPLATE_ID`, `NAME`, `PLATFORM`);
