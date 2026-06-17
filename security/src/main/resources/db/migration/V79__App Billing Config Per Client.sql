use `security`;

-- ============================================================================
-- V79: Make app billing config per (app, client) instead of per app.
--
-- Billing is resolved by the URL/exposing client context (app, urlClient):
-- the same app exposed via different clients' URLs is billed by that client's
-- config and wallet. No config row for a client means no enforcement for that
-- client's users (no fallback to SYSTEM).
-- ============================================================================

ALTER TABLE `security`.`security_app_billing_config`
  ADD COLUMN `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client (URL/exposing client) this config applies to; billing resolves per (app, client)' AFTER `APP_ID`,
  DROP INDEX `UK1_APP_BILLING_CONFIG_APP_ID`,
  ADD UNIQUE KEY `UK1_APP_BILLING_CONFIG_APP_ID_CLIENT_ID` (`APP_ID`,`CLIENT_ID`),
  ADD KEY `FK2_APP_BILLING_CONFIG_CLIENT_ID` (`CLIENT_ID`),
  ADD CONSTRAINT `FK2_APP_BILLING_CONFIG_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT;
