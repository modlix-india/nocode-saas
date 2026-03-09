ALTER TABLE `entity_processor`.`entity_processor_tickets`
    ADD INDEX `IDX5_TICKETS_AC_CC_CREATED_AT` (`APP_CODE`, `CLIENT_CODE`, `CREATED_AT`);
