ALTER TABLE `message`.`message_whatsapp_phone_number`
    ADD COLUMN `WEBHOOK_CONFIG` JSON NULL COMMENT 'Phone Number webhook config' AFTER `THROUGHPUT_LEVEL_TYPE`;
