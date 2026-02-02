use security;

ALTER TABLE `security`.`security_app_sso_token`
    DROP FOREIGN KEY `FK1_APP_SSO_BUNDLE_USER_ID`;
ALTER TABLE `security`.`security_app_sso_token`
    DROP COLUMN `BUNDLE_ID`,
    DROP INDEX `FK1_APP_SSO_TOKEN_BUNDLE_ID`;
;
