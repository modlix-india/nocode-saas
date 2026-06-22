use `security`;

-- ============================================================================
-- V83: The suspend app to serve when a tenant app's owner is suspended lives on
-- the builder's billing config (one config governs all of that client's apps).
-- Serving a non-exempt tenant app whose owner is SUSPENDED renders this app in
-- place. Resolving a Modlix app needs both an app code and an owning client
-- code, so we store both. NULL falls back to the platform default suspend app.
-- ============================================================================

ALTER TABLE `security`.`security_app_billing_config`
  ADD COLUMN `SUSPEND_APP_CODE` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT 'App code served in place of a tenant app whose owner is suspended' AFTER `ENFORCED`,
  ADD COLUMN `SUSPEND_CLIENT_CODE` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT 'Client code that owns the suspend app (e.g. SYSTEM)' AFTER `SUSPEND_APP_CODE`;
