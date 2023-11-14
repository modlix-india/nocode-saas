use security;


ALTER TABLE `security`.`security_package` 
ADD COLUMN `APP_ID` BIGINT UNSIGNED NULL DEFAULT NULL AFTER `CLIENT_ID`;

ALTER TABLE `security`.`security_package` 
ADD CONSTRAINT `FK2_PACKAGE_APP_ID`
  FOREIGN KEY (`APP_ID`)
  REFERENCES `security`.`security_app` (`ID`)
  ON DELETE RESTRICT
  ON UPDATE RESTRICT;
