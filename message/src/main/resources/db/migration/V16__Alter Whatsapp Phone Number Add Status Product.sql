ALTER TABLE `message`.`message_whatsapp_phone_number`
    DROP COLUMN `THROUGHPUT_LEVEL_TYPE`,
    DROP COLUMN `NAME_STATUS`;

ALTER TABLE `message`.`message_whatsapp_phone_number`
    ADD COLUMN `PRODUCT_ID` BIGINT UNSIGNED NULL COMMENT 'Entity Processor Product Id' AFTER `USER_ID`,
    ADD COLUMN `QUALITY_SCORE` JSON NULL COMMENT 'Quality Score of Whatsapp Phone Number' AFTER `QUALITY_RATING`,
    ADD COLUMN `NAME_STATUS` ENUM ('APPROVED', 'AVAILABLE_WITHOUT_REVIEW', 'DECLINED', 'EXPIRED', 'NON_EXISTS', 'NONE', 'PENDING_REVIEW') NOT NULL DEFAULT 'NONE' COMMENT 'Status of the verified name.' AFTER `CODE_VERIFICATION_STATUS`,
    ADD COLUMN `THROUGHPUT` JSON NULL COMMENT 'Throughput of Whatsapp Phone Number' AFTER `PLATFORM_TYPE`,
    ADD COLUMN `STATUS` ENUM ('PENDING', 'DELETED', 'MIGRATED', 'BANNED', 'RESTRICTED', 'RATE_LIMITED', 'FLAGGED', 'CONNECTED', 'DISCONNECTED', 'UNKNOWN', 'UNVERIFIED') NOT NULL DEFAULT 'UNKNOWN' COMMENT 'Status of the Whatsapp Phone Number' AFTER `THROUGHPUT`,
    ADD COLUMN `MESSAGING_LIMIT_TIER` ENUM ('TIER_50', 'TIER_250', 'TIER_1K', 'TIER_10K', 'TIER_100K', 'TIER_UNLIMITED') NULL COMMENT 'Messaging Limit Tier' AFTER `STATUS`;

RENAME TABLE `message`.`message_whatsapp_phone_number` TO `message`.`message_whatsapp_phone_numbers`;

RENAME TABLE `message`.`message_whatsapp_business_account` TO `message`.`message_whatsapp_business_accounts`;
