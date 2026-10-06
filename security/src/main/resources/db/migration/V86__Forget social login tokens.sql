-- Social sign-in no longer keeps anything the provider hands over except the verified identity
-- (USERNAME, USER_METADATA). The privacy policy says we keep no Google or Facebook token, so the
-- ones already stored go too: the spent auth code, the access token, Google's refresh token,
-- the expiry and the raw token response (which also carried the id_token).
--
-- UPDATED_AT is pinned to itself on purpose. The column is ON UPDATE CURRENT_TIMESTAMP, and a
-- state's ten-minute spending window is measured from it
-- (AppRegistrationIntegrationTokenService.isStateExpired), so letting this statement bump it
-- would hand every verified-but-unspent state a fresh window.

UPDATE `security`.`security_app_reg_integration_tokens`
SET `AUTH_CODE`      = NULL,
    `TOKEN`          = NULL,
    `REFRESH_TOKEN`  = NULL,
    `EXPIRES_AT`     = NULL,
    `TOKEN_METADATA` = NULL,
    `UPDATED_AT`     = `UPDATED_AT`
WHERE `AUTH_CODE` IS NOT NULL
   OR `TOKEN` IS NOT NULL
   OR `REFRESH_TOKEN` IS NOT NULL
   OR `EXPIRES_AT` IS NOT NULL
   OR `TOKEN_METADATA` IS NOT NULL;
