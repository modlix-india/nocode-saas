USE `entity_processor`;

-- The customer's WhatsApp profile picture, on the deal.
--
-- Kept here rather than on the message rows because it belongs to the person, not to anything they
-- said. It is written for every deal that shares the phone number, so a customer holding several
-- deals shows the same face on all of them rather than whichever one they last wrote about.
--
-- Deliberately NOT covered by attachment retention. Media in a thread expires after thirty days,
-- which is honest - the conversation says an attachment expired. An avatar quietly disappearing on
-- the same schedule would read as a rendering fault, and the file it points at lives outside the
-- swept tree (`/whatsapp/{app}/avatars`, not `.../incoming`) so the sweep cannot reach it even by
-- accident.
ALTER TABLE `entity_processor_tickets`
    ADD COLUMN `WHATSAPP_PROFILE_PIC_FILE_DETAIL` JSON NULL
        COMMENT 'Stored WhatsApp avatar for this deal''s phone number. Not subject to media retention.',
    -- WhatsApp's own id for the image. Stored so the bridge can be told what we already have and be
    -- answered with nothing when it has not changed, which is what keeps profile lookups near zero.
    ADD COLUMN `WHATSAPP_PROFILE_PIC_ID` VARCHAR(255) NULL
        COMMENT 'WhatsApp picture id, so an unchanged avatar is never re-fetched';

-- The update is by phone number within a tenant, and there was no index for that shape.
CREATE INDEX `IDX9_TICKETS_APP_CLIENT_PHONE`
    ON `entity_processor_tickets` (`APP_CODE`, `CLIENT_CODE`, `PHONE_NUMBER`);
