-- Message editing needs a handoff kind of its own.
--
-- WhatsApp lets either party rewrite a message after sending it. That is not a delivery state, so
-- it cannot ride MESSAGE_STATUS - a status handoff is defined to leave the body alone, and an edit
-- exists precisely to change it. Without this value the outbox insert fails on the enum and the
-- edit is lost at the first hop, before the consumer ever sees it.
--
-- MESSAGE_EDIT is 12 characters, inside the column's existing VARCHAR(15) as jOOQ generates it
-- (PROFILE_PICTURE, at 15, still sets that length), so no generated code changes with this.

ALTER TABLE `message`.`message_dispatch_outbox`
    MODIFY COLUMN `EVENT_TYPE` ENUM (
        'INBOUND_MESSAGE',
        'MESSAGE_STATUS',
        'CALL_STATUS',
        'MEDIA_READY',
        'PROFILE_PICTURE',
        'MESSAGE_EDIT'
        )
        NOT NULL COMMENT 'What happened. Every kind rides the same outbox on the same key.';
