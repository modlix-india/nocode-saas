-- A customer's WhatsApp profile picture.
--
-- The one event on this channel that belongs to a person rather than to a message. It carries no
-- message id, and is keyed on the customer's number alone, because a picture is not something that
-- happened in a conversation: it is a fact about whoever is on the other end of it.
--
-- Same three-places rule as V22, and the same failure if this one is missed: the ingest service
-- writes the outbox row before anything reads the payload, so an unlisted value is a rejected insert
-- on an event the bridge believes it delivered, and its retry fails identically. Worse here than for
-- media, because the bridge retries the whole batch: one unlisted event type stops every message
-- behind it.
--
-- Appended rather than inserted, because ENUM ordinals are positional and putting a new member
-- anywhere but the end renumbers the rows already stored.
ALTER TABLE `message`.`message_dispatch_outbox`
    MODIFY COLUMN `EVENT_TYPE`
        ENUM ('INBOUND_MESSAGE','MESSAGE_STATUS','CALL_STATUS','MEDIA_READY','PROFILE_PICTURE') NOT NULL
        COMMENT 'What happened. PROFILE_PICTURE belongs to a customer number rather than to any one message.';
