ALTER TABLE `entity_processor`.`entity_processor_notes`
    ADD COLUMN `ATTACHMENT_FILE_DETAIL` JSON NULL COMMENT 'File Details if note has an attachment' AFTER `CONTENT`;