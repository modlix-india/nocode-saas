-- Organization details a tenant keeps about itself, edited from the shared Organization page's
-- Company tab (leadzump, sitezump, marketingai, modlix).
--
-- All nullable with no default: an empty value means "not filled in yet", and a default would be
-- indistinguishable from something the tenant actually entered. Contact columns here are for
-- display only; they are not login identifiers, so unlike security_user.EMAIL_ID / PHONE_NUMBER
-- they carry no 'NONE' sentinel and no index. Widths follow security_user (EMAIL_ID 320, the RFC
-- maximum).

ALTER TABLE `security`.`security_client`
    ADD COLUMN `WEBSITE` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Company website URL' AFTER `TIME_ZONE`,
    ADD COLUMN `EMAIL_ID` varchar(320) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Company contact email' AFTER `WEBSITE`,
    ADD COLUMN `PHONE_NUMBER` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Company contact phone number' AFTER `EMAIL_ID`,
    ADD COLUMN `ALTERNATE_PHONE_NUMBER` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Company alternate phone number' AFTER `PHONE_NUMBER`,
    ADD COLUMN `LINKEDIN_URL` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Company LinkedIn page URL' AFTER `ALTERNATE_PHONE_NUMBER`,
    ADD COLUMN `DESCRIPTION` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'About the company' AFTER `LINKEDIN_URL`;
