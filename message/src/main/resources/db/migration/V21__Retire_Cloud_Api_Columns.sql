-- Retires the Cloud API metadata columns on the session table.
--
-- Everything dropped here is metadata Meta reported about a number: how they rated its quality, what
-- messaging tier it sat in, whether its display name had passed review, and where their webhook
-- pointed. None of those concepts exist on the linked-device protocol. There is no review, no tier,
-- no webhook and nobody to report anything, so leaving the columns would mean every row shows a
-- permanent null that reads as "not synced yet" rather than "cannot exist".
--
-- The health of a number is computed now instead: reply rate, warm-up day and the caps all derive
-- from message history in entity-processor. That is strictly better than what is being dropped,
-- because Meta's quality rating only ever moved after the damage was done.
--
-- WHAT THIS DOES NOT DO, DELIBERATELY.
--
-- It does not drop message_whatsapp_messages, message_whatsapp_templates or
-- message_whatsapp_business_accounts. Those tables hold real pre-pivot rows. The conversation
-- history was migrated into entity_processor_whatsapp_messages, which is what the UI reads, so
-- nothing needs them, but "nothing needs them" and "nobody will ever want to look" are different
-- claims and only the first one is verified. They are already excluded from JOOQ generation, so no
-- code can reach them by accident; dropping them is a separate, deliberate act for whoever is
-- willing to confirm the backfill covered everything.
--
-- It also does not drop WHATSAPP_BUSINESS_ACCOUNT_ID on this table. V20 relaxed it to NULL, and it
-- is the only remaining link from a surviving row back to the account it came from. That is worth
-- keeping while pre-pivot rows exist, for exactly the same reason.

ALTER TABLE `message`.`message_whatsapp_phone_numbers`
    DROP COLUMN `QUALITY_RATING`,
    DROP COLUMN `QUALITY_SCORE`,
    DROP COLUMN `CODE_VERIFICATION_STATUS`,
    DROP COLUMN `NAME_STATUS`,
    DROP COLUMN `PLATFORM_TYPE`,
    DROP COLUMN `THROUGHPUT`,
    DROP COLUMN `STATUS`,
    DROP COLUMN `MESSAGING_LIMIT_TIER`,
    DROP COLUMN `WEBHOOK_CONFIG`;
