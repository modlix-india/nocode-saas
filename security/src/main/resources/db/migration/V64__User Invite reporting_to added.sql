use security;

ALTER TABLE `security`.`security_user_invite`
ADD COLUMN `REPORTING_TO` bigint unsigned DEFAULT NULL COMMENT 'Reporting to ID for which this user belongs to' AFTER `DESIGNATION_ID`;

ALTER TABLE `security`.`security_user_invite`
ADD CONSTRAINT `FK1_USER_INVITE_REPORTING_TO_ID` FOREIGN KEY (`REPORTING_TO`) REFERENCES `security_user` (`ID`) ON DELETE SET NULL ON UPDATE RESTRICT;
