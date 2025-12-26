CREATE TABLE IF NOT EXISTS core.core_remote_repositories
(
    ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    CREATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    CREATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    APP_CODE CHAR(8) NOT NULL COMMENT 'App Code',
    REPO_NAME ENUM('PROCESSOR') NOT NULL COMMENT 'Repository name',

    PRIMARY KEY (ID)
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

insert into core.core_remote_repositories (APP_CODE, REPO_NAME) values ('leadzump', 'PROCESSOR');
