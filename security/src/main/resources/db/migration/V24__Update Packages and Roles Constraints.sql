use security;

ALTER TABLE `security`.`security_package` DROP CONSTRAINT `UK2_PACKAGE_NAME`;

ALTER TABLE `security`.`security_package` ADD CONSTRAINT `UK2_PACKAGE_NAME_APP_ID` UNIQUE (`NAME`, `APP_ID`);

ALTER TABLE `security`.`security_role` DROP CONSTRAINT `UK1_ROLE_NAME`;

ALTER TABLE `security`.`security_role` ADD CONSTRAINT  `UK1_ROLE_NAME_APP_ID` UNIQUE (`NAME`, `APP_ID`);