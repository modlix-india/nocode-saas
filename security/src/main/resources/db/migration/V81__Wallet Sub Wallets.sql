use `security`;

-- ============================================================================
-- V81: Wallet hierarchy (sub-wallets).
-- A wallet is now scoped per (CLIENT_ID, APP_ID):
--   APP_ID NULL -> the client's parent (client-level) wallet  [the default]
--   APP_ID set  -> an app sub-wallet (ring-fenced tokens for that app)
-- Charge resolution for (clientId, appId): use the app sub-wallet if it exists,
-- else fall back to the parent wallet.
--
-- MySQL treats NULLs as distinct in a UNIQUE index, which would allow many
-- parent wallets per client. To guarantee "one parent + one sub-wallet per app
-- per client" we add a STORED generated column IFNULL(APP_ID,0) and make the
-- uniqueness span (CLIENT_ID, APP_ID_KEY). JOOQ detects the generated column
-- and excludes it from INSERT/UPDATE.
-- ============================================================================

ALTER TABLE `security`.`security_wallet`
  ADD COLUMN `APP_ID` bigint unsigned DEFAULT NULL
    COMMENT 'App sub-wallet scope; NULL = client-level parent wallet' AFTER `CLIENT_ID`,
  ADD COLUMN `APP_ID_KEY` bigint unsigned GENERATED ALWAYS AS (IFNULL(`APP_ID`, 0)) STORED
    COMMENT 'Normalized APP_ID for uniqueness (0 = parent wallet)' AFTER `APP_ID`,
  DROP INDEX `UK1_WALLET_CLIENT_ID`,
  ADD UNIQUE KEY `UK1_WALLET_CLIENT_APP` (`CLIENT_ID`, `APP_ID_KEY`),
  ADD KEY `FK2_WALLET_APP_ID` (`APP_ID`),
  ADD CONSTRAINT `FK2_WALLET_APP_ID` FOREIGN KEY (`APP_ID`)
    REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT;
