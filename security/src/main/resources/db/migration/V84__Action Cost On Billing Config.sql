use `security`;

-- ============================================================================
-- V84: Ditch the global action catalog. Charges are inherently per (app, client)
-- = per app_billing_config row, so per-action costs become children of the
-- billing config (FK), and the list of valid action keys lives in code, not a
-- seeded table. Nothing to seed: a config with no cost row for an action = free.
-- (Tables are empty / not in production, so we drop and recreate cleanly.)
-- ============================================================================

DROP TABLE IF EXISTS `security`.`security_action_catalog`;
DROP TABLE IF EXISTS `security`.`security_app_action_cost`;

CREATE TABLE `security`.`security_app_action_cost` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `BILLING_CONFIG_ID` bigint unsigned NOT NULL COMMENT 'Billing config (app, client) that owns this rate',
  `ACTION_KEY` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Action key, e.g. core.email.send, security.app.rent',
  `CREDIT_COST` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Credits charged per unit',
  `ACTION_CLASS` enum('ENGAGEMENT','METERED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'METERED' COMMENT 'Zero-balance behaviour class',
  `FREE_QUOTA` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Free units per period before charging',
  `STATUS` enum('ACTIVE','INACTIVE','DELETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_APP_ACTION_COST_CONFIG_ACTION` (`BILLING_CONFIG_ID`,`ACTION_KEY`),
  KEY `FK1_APP_ACTION_COST_CONFIG_ID` (`BILLING_CONFIG_ID`),
  CONSTRAINT `FK1_APP_ACTION_COST_CONFIG_ID` FOREIGN KEY (`BILLING_CONFIG_ID`) REFERENCES `security_app_billing_config` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
