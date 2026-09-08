-- Message editing. WhatsApp lets either party rewrite a message after sending it, and until now
-- the edit was stored as a brand new row typed SYSTEM with no body, leaving the original's stale
-- wording in the thread and an empty bubble beside it. 63 such rows existed on local, every one of
-- them empty.
--
-- The current wording stays in BODY_TEXT, so every existing reader - the thread, the searchable
-- projection, roughly 150 dealProfile expressions - keeps working untouched and unaware. What is
-- new is the trail of what the message used to say.

ALTER TABLE `entity_processor`.`entity_processor_whatsapp_messages`
    -- When the message was last rewritten. Non-null is what marks a message as edited, so the
    -- thread can label it without having to inspect the revisions blob.
    ADD COLUMN `EDITED_AT` DATETIME NULL DEFAULT NULL
        COMMENT 'When this message was last edited by its sender. Null means never edited.'
        AFTER `BODY_TEXT`,

    -- Every earlier wording, oldest first, as
    --   {"revisions": [{"version": 1, "text": "...", "replacedAt": "2026-09-04T14:08:40"}, ...]}
    --
    -- version is 1-based, so version 1 is always the sender's original wording.
    --
    -- An object wrapping the list rather than a bare array, so this column uses the same
    -- Map + JSONtoClassConverter forcedType as MESSAGE, IN_MESSAGE and MESSAGE_RESPONSE on this
    -- table. That converter writes "{}" for null, which is valid for an object and would not be
    -- for an array.
    --
    -- So the first entry is always what the sender originally wrote, later entries are the
    -- intermediate versions in order, and BODY_TEXT is what it says now. A message edited five
    -- times therefore keeps all six wordings, which is the point: in a CRM a lead revising
    -- "50L works" down to "40L" is evidence, and a column that only kept the original would lose
    -- everything in between.
    --
    -- JSON rather than a child table because these are read only with their message, never queried
    -- across messages, and are written by the same upsert that owns the row - a child table would
    -- add a join to every thread fetch to hold data nothing searches.
    ADD COLUMN `BODY_REVISIONS` JSON NULL DEFAULT NULL
        COMMENT 'Earlier wordings of this message, oldest first. Current text stays in BODY_TEXT.'
        AFTER `EDITED_AT`;
