USE `message`;

ALTER TABLE `message`.`message_exotel_calls`
    ADD INDEX `IDX0_EXOTEL_CALLS_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `message`.`message_calls`
    ADD INDEX `IDX0_CALLS_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `message`.`message_whatsapp_messages`
    ADD INDEX `IDX0_WHATSAPP_MESSAGES_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `message`.`message_whatsapp_templates`
    ADD INDEX `IDX0_WHATSAPP_TEMPLATES_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `message`.`message_messages`
    ADD INDEX `IDX0_MESSAGES_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `message`.`message_whatsapp_phone_number`
    ADD INDEX `IDX0_WHATSAPP_PHONE_NUMBER_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `message`.`message_message_webhooks`
    ADD INDEX `IDX0_MESSAGE_WEBHOOKS_AC_CC` (`APP_CODE`, `CLIENT_CODE`);
