USE `entity_processor`;

-- A separate WhatsApp number on the deal.
--
-- Until now a deal had exactly one number and every channel used it. That held only because nobody
-- had asked the question this column answers: the number a lead is reachable at is not always the
-- number they are on WhatsApp. A lead gives a landline or a work mobile at intake, messaging it goes
-- nowhere, someone rings them, and they say "message me on this other one instead". There was
-- nowhere to put that answer, so it was written into a note and lost.
--
-- Nullable, and the null is the normal case rather than missing data: most leads never need a second
-- number, and forcing one would mean copying the phone number into two columns that then drift.
-- Everything that sends reads `WHATSAPP_NUMBER` and falls back to `PHONE_NUMBER` when it is null, so
-- an untouched deal behaves exactly as it does today.
--
-- The intake path already collects this. Meta lead forms have a WhatsApp-number field and the
-- collector already parses it, but `Ticket.of(CampaignTicketRequest)` could only fold it into the
-- `FORM_DATA` JSON blob, which nothing queries and no screen shows. With this column that value
-- finally lands somewhere it is used.
ALTER TABLE `entity_processor_tickets`
    ADD COLUMN `WHATSAPP_DIAL_CODE` SMALLINT NULL DEFAULT NULL
        COMMENT 'Calling code for WHATSAPP_NUMBER. Null when the deal has no separate WhatsApp number.'
        AFTER `PHONE_NUMBER`,
    -- CHAR(15) to match PHONE_NUMBER exactly. The two are compared against each other and against the
    -- customer number on inbound messages, and a width mismatch is the kind of thing that silently
    -- stops an index being used.
    ADD COLUMN `WHATSAPP_NUMBER` CHAR(15) NULL DEFAULT NULL
        COMMENT 'The number this deal is messaged on. Null means use PHONE_NUMBER.'
        AFTER `WHATSAPP_DIAL_CODE`;

-- Inbound matching is the reason this index is not optional.
--
-- A message arrives carrying only the customer's number. Resolving it to a deal searches
-- PHONE_NUMBER, and now WHATSAPP_NUMBER as well, because a lead who was told to message the second
-- number will arrive on it. Without the second search their reply matches no deal and the inbound
-- path creates a brand new one, which is the exact duplicate this column exists to prevent. That
-- search runs on every inbound message, so it is indexed the same way the phone search is.
CREATE INDEX `IDX10_TICKETS_APP_CLIENT_WHATSAPP`
    ON `entity_processor_tickets` (`APP_CODE`, `CLIENT_CODE`, `WHATSAPP_NUMBER`);
