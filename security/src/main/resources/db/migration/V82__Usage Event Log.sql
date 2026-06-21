use `security`;

-- ============================================================================
-- V82: Durable consumption log.
-- Every metered consumption (site/app/page/schema/storage/file creation,
-- email/SMS send, etc.) writes one append-only row here on the hot path. The
-- write is dumb and fast: raw dimensions only, no wallet resolution and no
-- pricing. A worker job consolidates closed time windows every 15 minutes:
-- it groups rows by (CLIENT_ID, APP_ID, ACTION_KEY), resolves the wallet
-- (app sub-wallet -> parent), prices via the action catalog, writes one
-- idempotent ledger debit per (wallet, action, window), flips the wallet to
-- SUSPENDED if the balance crosses the floor, then purges the consumed rows.
--
-- This replaces the earlier Redis-counter / usage-MQ-exchange design: the
-- table is the durable source of truth, nothing lives in a cache, and the
-- ledger debit row retains action + quantity + rate for audit after purge.
-- AI stays the one synchronous exception (reserve/settle per turn).
-- Function execution is NOT metered.
-- ============================================================================

CREATE TABLE IF NOT EXISTS `security`.`security_usage_event` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Consumer client whose wallet is debited',
  `URL_CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Exposing/URL client that owns the billing config and rates',
  `APP_ID` bigint unsigned NOT NULL COMMENT 'App the action happened in',
  `USER_ID` bigint unsigned DEFAULT NULL COMMENT 'User who performed the action',
  `ACTION_KEY` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Catalog action key e.g. core.page.create, message.email.send',
  `QUANTITY` decimal(19,4) NOT NULL DEFAULT 1 COMMENT 'Units consumed (count, GB, segments, ...)',
  `CONSOLIDATED` tinyint(1) NOT NULL DEFAULT 0 COMMENT '1 once a consolidation run has debited this row',
  `CONSOLIDATED_AT` timestamp NULL DEFAULT NULL COMMENT 'When this row was consolidated',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  PRIMARY KEY (`ID`),
  KEY `IDX1_USAGE_EVENT_SCAN` (`CONSOLIDATED`, `CREATED_AT`),
  KEY `IDX2_USAGE_EVENT_GROUP` (`CLIENT_ID`, `APP_ID`, `ACTION_KEY`),
  KEY `FK1_USAGE_EVENT_CLIENT_ID` (`CLIENT_ID`),
  KEY `FK2_USAGE_EVENT_APP_ID` (`APP_ID`),
  CONSTRAINT `FK1_USAGE_EVENT_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_USAGE_EVENT_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
