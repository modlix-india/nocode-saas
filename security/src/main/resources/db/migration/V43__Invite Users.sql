use security;

DROP TABLE IF EXISTS `security`.`security_user_invite`;

CREATE TABLE `security`.`security_user_invite` (
   `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

   `CLIENT_ID` bigint UNSIGNED NOT NULL COMMENT 'Client id for the user to be created in',
   `USER_NAME` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'User Name to login',
   `EMAIL_ID` varchar(320) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Email ID to login',
   `PHONE_NUMBER` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Phone Number to login',
   `FIRST_NAME` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'First name',
   `LAST_NAME` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Last name',

   `INVITE_CODE` char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Unique Invitation Code',
   `PROFILE_ID` bigint UNSIGNED DEFAULT NULL COMMENT 'Profile Id to assign by default',
   `DESIGNATION_ID` bigint UNSIGNED DEFAULT NULL COMMENT 'Designation Id to assign by default',

   `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
   `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

   PRIMARY KEY (`ID`),
   UNIQUE KEY (`INVITE_CODE`),
   
   CONSTRAINT `fk_security_user_invite_client` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security`.`security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
   CONSTRAINT `fk_security_user_invite_profile` FOREIGN KEY (`PROFILE_ID`) REFERENCES `security`.`security_profile` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
   CONSTRAINT `fk_security_user_invite_designation` FOREIGN KEY (`DESIGNATION_ID`) REFERENCES `security`.`security_designation` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;