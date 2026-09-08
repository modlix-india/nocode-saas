-- A second event type for attachments arriving after the message they belong to.
--
-- An inbound photo produces two things on very different timescales: the message, which is small
-- and must appear now, and the bytes, which may be sixty megabytes over a mobile link. The bridge
-- sends the message immediately and fetches the file afterwards, then reports where it stored it.
-- That report is this event type.
--
-- It has to exist in three places or the chain breaks silently at whichever one is missed: the Go
-- side's event string, DispatchEventType on the Java side, and this column. The column is the one
-- that fails worst - the row is written by the ingest service before anything reads the payload, so
-- an unlisted value is a rejected insert on an event the bridge believes it delivered, and the
-- retry never succeeds either.
--
-- MySQL appends the new member, which matters: ENUM ordinals are positional, and inserting the
-- value anywhere but the end would renumber the existing rows underneath the data already stored.
ALTER TABLE `message`.`message_dispatch_outbox`
    MODIFY COLUMN `EVENT_TYPE`
        ENUM ('INBOUND_MESSAGE','MESSAGE_STATUS','CALL_STATUS','MEDIA_READY') NOT NULL
        COMMENT 'What happened. MEDIA_READY carries the stored-file details for a message already delivered under its own id.';
