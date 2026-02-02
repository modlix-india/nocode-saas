ALTER TABLE `message`.`message_whatsapp_messages`
    ADD COLUMN `CUSTOMER_WA_ID` CHAR(20) NOT NULL COMMENT 'Customer Whatsapp ID' AFTER `TO`,
    ADD COLUMN `IN_MESSAGE` JSON NULL COMMENT 'Inbound message object' AFTER `MESSAGE`;
