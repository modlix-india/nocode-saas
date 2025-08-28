ALTER TABLE `message`.`message_whatsapp_messages`
    ADD COLUMN `IN_MESSAGE` JSON NULL COMMENT 'Inbound message object' AFTER `MESSAGE`;
