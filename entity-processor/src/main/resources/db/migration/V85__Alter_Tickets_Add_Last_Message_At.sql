-- The WhatsApp inbox lists deals, not a separate conversation index, so the ordering key has to
-- live on the ticket. UPDATED_AT cannot serve: it is written by the framework alongside UPDATED_BY,
-- and an inbound webhook has no user, so bumping it would corrupt the audit trail on every message
-- and make message traffic read as an edit on the Deals screen.
ALTER TABLE `entity_processor`.`entity_processor_tickets`
    ADD COLUMN `LAST_MESSAGE_AT` DATETIME NULL COMMENT 'Time of the most recent WhatsApp message on this deal, inbound or outbound. Orders the conversation list. Null until the deal has any message.' AFTER `FORM_DATA`;

-- Inbound resolution and the bump both look a deal up by customer number, and the conversation list
-- groups by it. The table has never carried an index on PHONE_NUMBER.
ALTER TABLE `entity_processor`.`entity_processor_tickets`
    ADD INDEX `IDX7_TICKETS_AC_CC_PHONE` (`APP_CODE`, `CLIENT_CODE`, `PHONE_NUMBER`);

-- Supports the inbox's default ordering, which is COALESCE(LAST_MESSAGE_AT, UPDATED_AT) DESC.
ALTER TABLE `entity_processor`.`entity_processor_tickets`
    ADD INDEX `IDX8_TICKETS_AC_CC_LAST_MESSAGE_AT` (`APP_CODE`, `CLIENT_CODE`, `LAST_MESSAGE_AT`);
