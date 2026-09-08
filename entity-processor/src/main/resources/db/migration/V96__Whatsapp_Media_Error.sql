USE `entity_processor`;

-- Why an attachment never arrived.
--
-- The bridge already reports this. It abandons a fetch that cannot succeed - the media is too large,
-- WhatsApp no longer serves it, or the retries are exhausted - and sends `mediaError` on the
-- MEDIA_READY event. The message service forwards it. Then it was only written to a log, so nothing
-- the reader can see ever said the attachment was not coming.
--
-- The visible consequence is a bubble with nothing in it. A media message whose bytes never arrived
-- has no body either, so the thread drew a correctly dated bubble containing only its timestamp,
-- with no error anywhere and no way to tell it from a message that was never delivered. It is the
-- ordinary outcome of re-linking a number: WhatsApp will not serve media for messages sent while no
-- device was attached, so every one of those comes back as an empty bubble.
--
-- Text rather than a flag, because "too large" and "no longer available" call for different answers
-- from the person reading: one is worth asking the customer to resend, the other is not.
ALTER TABLE `entity_processor_whatsapp_messages`
    ADD COLUMN `MEDIA_ERROR` VARCHAR(512) NULL
        COMMENT 'Why the attachment never arrived; null means it did, or is still coming'
        AFTER `MEDIA_IS_VOICE_NOTE`;
