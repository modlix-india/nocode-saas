use security;

DROP TABLE IF EXISTS `security_ssl_challenge`;
DROP TABLE IF EXISTS `security_ssl_request`;
DROP TABLE IF EXISTS `security_ssl_certificate`;

CREATE TABLE `security_ssl_certificate` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `URL_ID` BIGINT UNSIGNED NOT NULL COMMENT 'URL ID for which this SSL certificate belongs to',
    `CRT` TEXT NOT NULL COMMENT 'SSL certificate',
    `CRT_CHAIN` TEXT NOT NULL COMMENT 'SSL certificate chain',
    `CRT_KEY` TEXT NOT NULL COMMENT 'SSL certificate key',
    `CSR` TEXT NOT NULL COMMENT 'SSL certificate signing request',
    `DOMAINS` VARCHAR(1024) NOT NULL COMMENT 'Domains for which this SSL certificate is valid',
    `ORGANIZATION` VARCHAR(1024) NOT NULL COMMENT 'Organization for which this SSL certificate is valid',
    `EXPIRY_DATE` TIMESTAMP NOT NULL COMMENT 'Expiry date of this SSL certificate',
    `ISSUER` VARCHAR(1024) NOT NULL COMMENT 'Issuer of this SSL certificate',
    `CURRENT` BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Is this the current SSL certificate for the URL',
    `AUTO_RENEW_TILL` TIMESTAMP NULL DEFAULT NULL COMMENT 'Time till which this SSL certificate is auto renewed',
    
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
	`UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    CONSTRAINT `FK1_SSL_CRT_CLNT_URL_ID` FOREIGN KEY (`URL_ID`) REFERENCES `security_client_url` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE `security_ssl_request` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `URL_ID` BIGINT UNSIGNED NOT NULL COMMENT 'URL ID for which this SSL certificate belongs to',
    `DOMAINS` VARCHAR(1024) NOT NULL COMMENT 'Domains for which this SSL certificate is valid',
    `ORGANIZATION` VARCHAR(1024) NOT NULL COMMENT 'Organization for which this SSL certificate is valid',
    `CRT_KEY` TEXT NOT NULL COMMENT 'SSL certificate key',
    `CSR` TEXT NOT NULL COMMENT 'SSL certificate signing request',
    `VALIDITY` INT UNSIGNED NOT NULL COMMENT 'Validity of the SSL certificate in months',
    `FAILED_REASON` TEXT DEFAULT NULL COMMENT 'Reason for challenge failure',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
	`UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    
    PRIMARY KEY (`ID`),
    UNIQUE KEY (`URL_ID`),
    CONSTRAINT `FK1_SSL_REQ_CLNT_URL_ID` FOREIGN KEY (`URL_ID`) REFERENCES `security_client_url` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE `security_ssl_challenge` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `REQUEST_ID` BIGINT UNSIGNED NOT NULL COMMENT 'SSL request ID for which this challenge belongs to',
    `CHALLENGE_TYPE` VARCHAR(32) NOT NULL COMMENT 'Challenge type',
    `DOMAIN` VARCHAR(1024) NOT NULL COMMENT 'Domain for which this challenge is valid',
    `TOKEN` VARCHAR(1024) NOT NULL COMMENT 'Challenge token for HTTP-01 challenge/Challenge TXT record name for DNS-01 challenge',
    `AUTHORIZATION` VARCHAR(1024) NOT NULL COMMENT 'Challenge key authorization for HTTP-01 challenge/Digest for DNS-01 challenge',
    
    `STATUS` VARCHAR(128) NOT NULL DEFAULT 'PENDING' COMMENT 'Challenge status',
    `FAILED_REASON` TEXT DEFAULT NULL COMMENT 'Reason for challenge failure',
    `LAST_VALIDATED_AT` TIMESTAMP NULL DEFAULT NULL COMMENT 'Time when this challenge is validated',
    `RETRY_COUNT` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Number of times this challenge is retried',
    
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
	`UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    CONSTRAINT `FK1_SSL_CHLNG_REQ_ID` FOREIGN KEY (`REQUEST_ID`) REFERENCES `security_ssl_request` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;