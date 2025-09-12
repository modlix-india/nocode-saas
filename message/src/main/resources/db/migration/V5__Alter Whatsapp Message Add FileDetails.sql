ALTER TABLE `message`.`message_whatsapp_messages`
    ADD COLUMN `MEDIA_FILE_DETAIL` JSON NULL COMMENT 'File Details if message has a media file' AFTER `MESSAGE`;
