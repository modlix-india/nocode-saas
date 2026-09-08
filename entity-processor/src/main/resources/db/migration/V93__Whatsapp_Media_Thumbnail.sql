USE `entity_processor`;

-- WhatsApp embeds a small JPEG inside the message itself. It arrives with the message rather than
-- after it, so a preview can be drawn before the attachment has been fetched at all, and it spares
-- the reader the original: a photo thumbnail is a few KB against several MB, and the inbox draws it
-- at a couple of hundred pixels either way.
--
-- Stored as file details rather than as bytes on the row, for the same reason MEDIA_FILE_DETAIL is.
-- The thread refetches its whole loaded window on every event, so bytes here would be re-sent on
-- each of those; a URL is fetched once and then served from browser cache.
ALTER TABLE `entity_processor_whatsapp_messages`
    ADD COLUMN `MEDIA_THUMBNAIL_FILE_DETAIL` JSON NULL COMMENT 'Stored inline preview of the attachment' AFTER `MEDIA_FILE_DETAIL`,
    -- Only ever set for documents. Lets a reader tell a one-page letter from a long contract
    -- without opening it, which is the one thing a first-page preview cannot say by itself.
    ADD COLUMN `MEDIA_PAGE_COUNT` INT UNSIGNED NULL COMMENT 'Page count, documents only' AFTER `MEDIA_DURATION_SECONDS`;
