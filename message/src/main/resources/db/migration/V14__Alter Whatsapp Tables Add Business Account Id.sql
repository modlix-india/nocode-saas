TRUNCATE `message`.`message_whatsapp_phone_number`;

ALTER TABLE `message`.`message_whatsapp_phone_number`
    DROP COLUMN `WHATSAPP_BUSINESS_ACCOUNT_ID`;

ALTER TABLE `message`.`message_whatsapp_phone_number`
    ADD COLUMN `WHATSAPP_BUSINESS_ACCOUNT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'WhatsApp Business Account ID.' AFTER `CODE`,
    ADD CONSTRAINT `FK1_WHATSAPP_PHONE_NUMBERS_WHATSAPP_BUSINESS_ACCOUNT_ID`
        FOREIGN KEY (`WHATSAPP_BUSINESS_ACCOUNT_ID`)
            REFERENCES `message`.`message_whatsapp_business_account` (`id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE;

TRUNCATE `message`.`message_whatsapp_templates`;

ALTER TABLE `message`.`message_whatsapp_templates`
    DROP COLUMN `WHATSAPP_BUSINESS_ACCOUNT_ID`;

ALTER TABLE `message`.`message_whatsapp_templates`
    ADD COLUMN `WHATSAPP_BUSINESS_ACCOUNT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'WhatsApp Business Account ID.' AFTER `CODE`,
    ADD CONSTRAINT `FK1_WHATSAPP_TEMPLATES_WHATSAPP_BUSINESS_ACCOUNT_ID`
        FOREIGN KEY (`WHATSAPP_BUSINESS_ACCOUNT_ID`)
            REFERENCES `message`.`message_whatsapp_business_account` (`id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE;

ALTER TABLE `message`.`message_whatsapp_messages`
    ADD COLUMN `WHATSAPP_BUSINESS_ACCOUNT_ID` BIGINT UNSIGNED NULL COMMENT 'WhatsApp Business Account ID.' AFTER `CODE`,
    ADD CONSTRAINT `FK1_WHATSAPP_MESSAGES_WHATSAPP_BUSINESS_ACCOUNT_ID`
        FOREIGN KEY (`WHATSAPP_BUSINESS_ACCOUNT_ID`)
            REFERENCES `message`.`message_whatsapp_business_account` (`id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE;
