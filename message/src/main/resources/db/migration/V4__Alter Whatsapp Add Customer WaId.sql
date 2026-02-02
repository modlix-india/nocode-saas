ALTER TABLE `message`.`message_whatsapp_messages`
    DROP COLUMN `CUSTOMER_WA_ID`;

ALTER TABLE `message`.`message_whatsapp_messages`
    ADD COLUMN `CUSTOMER_WA_ID` CHAR(20) NULL COMMENT 'Customer Whatsapp ID' AFTER `TO`;

ALTER TABLE `message`.`message_whatsapp_messages`
    ADD COLUMN `CUSTOMER_DIAL_CODE` SMALLINT NOT NULL DEFAULT 91 COMMENT 'Dial code of the customer phone number.' AFTER `CUSTOMER_WA_ID`,
    ADD COLUMN `CUSTOMER_PHONE_NUMBER` CHAR(15) NULL COMMENT 'Phone number of the customer.' AFTER `CUSTOMER_DIAL_CODE`;

DELETE
  FROM `message`.`message_whatsapp_phone_number`
 WHERE `PLATFORM_TYPE` = 'CLOUD_API';

ALTER TABLE `message`.`message_whatsapp_phone_number`
    ADD COLUMN `WHATSAPP_BUSINESS_ACCOUNT_ID` VARCHAR(255) NOT NULL COMMENT 'WhatsApp Business Account ID.' AFTER `CODE`;

ALTER TABLE `message`.`message_whatsapp_templates`
    ADD UNIQUE KEY `UK2_MESSAGE_WHATSAPP_TEMPLATES_ACCOUNT_ID_NAME` (`WHATSAPP_BUSINESS_ACCOUNT_ID`, `TEMPLATE_NAME`);

ALTER TABLE `message`.`message_whatsapp_templates`
    ADD COLUMN `HEADER_FILE_DETAIL` JSON NULL COMMENT 'File Details if header component has a media file' AFTER `MONTHLY_EDIT_COUNT`;
