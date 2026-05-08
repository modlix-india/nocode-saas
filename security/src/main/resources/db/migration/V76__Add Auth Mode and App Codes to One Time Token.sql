use security;

ALTER TABLE `security_one_time_token`
    ADD COLUMN `AUTH_MODE` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BEARER' COMMENT 'How the user authenticated when minting: COOKIE or BEARER' AFTER `REMEMBER_ME`,
    ADD COLUMN `ORIGIN_APP_CODE` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'App where the token was minted' AFTER `AUTH_MODE`,
    ADD COLUMN `TARGET_APP_CODE` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'Intended consumer app; null for beacon-establishment tokens' AFTER `ORIGIN_APP_CODE`;
