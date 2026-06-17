use `security`;

-- ============================================================================
-- V80: Make per-action credit costs per (app, client) too, so the exposing
-- client owns the full billing definition (rates + config) for the app it
-- exposes. Resolution falls back to the platform action catalog default when a
-- client has not set a custom cost for an action.
-- ============================================================================

ALTER TABLE `security`.`security_app_action_cost`
  ADD COLUMN `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Exposing client that owns this rate; cost resolves per (app, client, action)' AFTER `APP_ID`,
  DROP INDEX `UK1_APP_ACTION_COST_APP_ID_ACTION_KEY`,
  ADD UNIQUE KEY `UK1_APP_ACTION_COST_APP_CLIENT_ACTION` (`APP_ID`,`CLIENT_ID`,`ACTION_KEY`),
  ADD KEY `FK2_APP_ACTION_COST_CLIENT_ID` (`CLIENT_ID`),
  ADD CONSTRAINT `FK2_APP_ACTION_COST_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT;
