ALTER TABLE `core`.`core_remote_repositories` 
CHANGE COLUMN `APP_CODE` `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code' ;

ALTER TABLE `core`.`core_remote_repositories` 
ADD UNIQUE INDEX `K1_APP_UNIQUE` (`APP_CODE` ASC, `REPO_NAME` ASC) VISIBLE;

INSERT IGNORE INTO `core`.`core_remote_repositories` (`APP_CODE`, `REPO_NAME`) VALUES ('supportzump', 'PROCESSOR');