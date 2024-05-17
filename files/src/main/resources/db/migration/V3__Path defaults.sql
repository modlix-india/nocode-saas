use files;

ALTER TABLE `files`.`files_access_path` 
CHANGE COLUMN `PATH` `PATH` VARCHAR(1024) NOT NULL DEFAULT '' COMMENT 'Path to the resource' ;
