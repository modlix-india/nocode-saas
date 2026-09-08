-- What a media message is, beyond the file itself.
--
-- MEDIA_FILE_DETAIL has been here since V86 and has never been written on the inbound path. The
-- bridge classifies an incoming photo correctly and then has nowhere to put it: its wire struct
-- carries a type and a body string and nothing else, so the mimetype, the filename, the duration
-- and the bytes are all discarded before they reach this schema. These columns are the other half
-- of that pipe.
--
-- They sit beside MEDIA_FILE_DETAIL rather than inside it on purpose. The FileDetail JSON is the
-- files service's own shape, returned verbatim by its upload API; it describes where bytes live,
-- not what the message was. Duration and voice-note-ness are properties of the WhatsApp message,
-- they have no meaning to the files service, and burying them in a foreign payload would mean
-- reaching into a JSON column to answer "was this a voice note" - which the UI has to ask for every
-- audio bubble, since a voice note and an attached mp3 render differently.

ALTER TABLE `entity_processor`.`entity_processor_whatsapp_messages`
    ADD COLUMN `MEDIA_MIME_TYPE` VARCHAR(255) NULL
        COMMENT 'As WhatsApp reported it, not as guessed from the extension. Decides which player the UI mounts.'
        AFTER `MEDIA_FILE_DETAIL`,

    ADD COLUMN `MEDIA_SIZE` BIGINT UNSIGNED NULL
        COMMENT 'Bytes, as declared by the sender. Kept after the file expires so the thread can still say how large the attachment was.'
        AFTER `MEDIA_MIME_TYPE`,

    ADD COLUMN `MEDIA_DURATION_SECONDS` INT NULL
        COMMENT 'Audio and video only. Lets the UI show a length before the media has loaded.'
        AFTER `MEDIA_SIZE`,

    -- WhatsApp draws these as two different things: a voice note is a waveform recorded in the
    -- chat, an audio file is an attachment that happens to be playable. Both arrive as AudioMessage
    -- and only the PTT flag separates them, so if we do not keep it here the distinction is gone.
    ADD COLUMN `MEDIA_IS_VOICE_NOTE` TINYINT NOT NULL DEFAULT 0
        COMMENT 'AudioMessage.PTT. True for a recorded voice note, false for an attached audio file.'
        AFTER `MEDIA_DURATION_SECONDS`,

    -- Null until the retention sweep runs. Stamping it rather than deleting the row is deliberate:
    -- the message, its caption and its place in the conversation survive the file. A thread that
    -- silently loses bubbles is worse than one that says an attachment expired.
    ADD COLUMN `MEDIA_EXPIRED_AT` DATETIME NULL
        COMMENT 'When the retention sweep removed the bytes. Non-null means the file is gone on purpose, not missing by accident.'
        AFTER `MEDIA_IS_VOICE_NOTE`,

    -- A reaction is not a message in the thread, it is an annotation on one. Stored as the provider
    -- message id rather than our own row id because the reaction can arrive before the message it
    -- refers to has finished being written, and because the provider id is the only identifier both
    -- sides agree on.
    ADD COLUMN `REACTION_TO_MESSAGE_ID` VARCHAR(255) NULL
        COMMENT 'MESSAGE_ID of the message this reaction applies to. Set only on REACTION rows.'
        AFTER `MEDIA_EXPIRED_AT`;

-- The retention sweep asks for rows with bytes still attached and no expiry stamp, oldest first.
-- Leading with MEDIA_EXPIRED_AT keeps the scan off the rows it has already dealt with, which is
-- almost all of them once the feature has been running for a month.
CREATE INDEX `IDX7_WA_MESSAGES_MEDIA_EXPIRY`
    ON `entity_processor`.`entity_processor_whatsapp_messages` (`MEDIA_EXPIRED_AT`, `CREATED_AT`);

-- Rendering a thread means asking "which reactions point at these messages", once per page of
-- messages, within a tenant.
CREATE INDEX `IDX8_WA_MESSAGES_REACTION_TARGET`
    ON `entity_processor`.`entity_processor_whatsapp_messages` (`APP_CODE`, `CLIENT_CODE`, `REACTION_TO_MESSAGE_ID`);
