-- Anchors a WhatsApp message to the deal it belongs to.
--
-- Until now a message had no deal reference at all; the UI improvised the link at read time by
-- matching the deal's phone number against CUSTOMER_WA_ID. That breaks as soon as a deal carries a
-- WhatsApp number distinct from its phone number, and it cannot distinguish two deals held by the
-- same customer.
--
-- Nullable on purpose: an inbound message from a number that matches no ticket is still recorded,
-- and surfaces in the inbox as unassigned rather than being dropped.

ALTER TABLE `message`.`message_whatsapp_messages`
    ADD COLUMN `TICKET_ID` BIGINT UNSIGNED NULL COMMENT 'Entity Processor Ticket Id this message belongs to.' AFTER `WHATSAPP_PHONE_NUMBER_ID`;

ALTER TABLE `message`.`message_whatsapp_messages`
    ADD INDEX `IDX5_WHATSAPP_MESSAGES_TICKET` (`APP_CODE`, `CLIENT_CODE`, `TICKET_ID`);
