-- NUMBER_MISMATCH exists in the bridge's state machine and in WhatsappSessionState, but not in the
-- column, so it could never be stored. When a handset other than the one that was declared scanned
-- the code, the bridge unlinked the device and reported NUMBER_MISMATCH; applySessionState then
-- failed on the enum and applySnapshots swallowed it per row, exactly as it is designed to so that
-- one bad row cannot stop the fleet's state being recorded. The row kept whatever state it had
-- before, LINKED_AT was already stamped, and the number then refused every later link attempt with
-- a conflict while showing nothing that explained why.
--
-- Appended rather than reordered: MySQL stores an ENUM as the ordinal, so inserting a value in the
-- middle would silently rewrite the meaning of every existing row.
ALTER TABLE `message`.`message_whatsapp_phone_numbers`
    MODIFY COLUMN `SESSION_STATE` ENUM ('PAIRING', 'CONNECTED', 'DISCONNECTED', 'LOGGED_OUT', 'BANNED', 'COUNTRY_MISMATCH', 'NUMBER_MISMATCH') NULL COMMENT 'Lifecycle state as the bridge reports it, surfaced verbatim to the UI. COUNTRY_MISMATCH and NUMBER_MISMATCH are separate from LOGGED_OUT because they are the only failures here a customer can fix themselves in seconds, and only if told which one it is.';
