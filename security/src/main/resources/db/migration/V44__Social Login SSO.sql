USE security;

ALTER TABLE `security_app_reg_integration_tokens`
    ADD COLUMN `REQUEST_PARAM` JSON DEFAULT NULL COMMENT 'app metadata from request url' AFTER `STATE`;