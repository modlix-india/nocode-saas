ALTER TABLE `core`.`core_remote_repositories` 
CHANGE COLUMN `APP_CODE` `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code' ;

INSERT IGNORE INTO `core`.`core_remote_repositories` (`APP_CODE`, `REPO_NAME`) VALUES ('supportzump', 'PROCESSOR');