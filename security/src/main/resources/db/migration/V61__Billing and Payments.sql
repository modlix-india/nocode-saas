use `security`;

SELECT `ID`
  FROM `security`.`security_client`
 WHERE `CODE` = 'SYSTEM'
 LIMIT 1
  INTO @`v_client_system`;

-- Plan based permissions and roles

INSERT IGNORE INTO `security`.`security_permission` (`CLIENT_ID`, `NAME`, `DESCRIPTION`)
VALUES (@`v_client_system`, 'Plan CREATE', 'Plan create'),
       (@`v_client_system`, 'Plan READ', 'Plan read'),
       (@`v_client_system`, 'Plan UPDATE', 'Plan update'),
       (@`v_client_system`, 'Plan DELETE', 'Plan delete');

INSERT IGNORE INTO `security`.`security_v2_role` (`CLIENT_ID`, `NAME`, `SHORT_NAME`, `DESCRIPTION`)
VALUES (@`v_client_system`, 'Plan CREATE', 'Create', 'Plan create'),
       (@`v_client_system`, 'Plan READ', 'Read', 'Plan read'),
       (@`v_client_system`, 'Plan UPDATE', 'Update', 'Plan update'),
       (@`v_client_system`, 'Plan DELETE', 'Delete', 'Plan delete'),
       (@`v_client_system`, 'Plan Manager', 'Manager', 'Plan manager');

SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Plan Manager' LIMIT 1 INTO @`v_v2_role_plan_manager`;

SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Plan CREATE' LIMIT 1 INTO @`v_v2_role_plan_create`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Plan READ' LIMIT 1 INTO @`v_v2_role_plan_read`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Plan UPDATE' LIMIT 1 INTO @`v_v2_role_plan_update`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Plan DELETE' LIMIT 1 INTO @`v_v2_role_plan_delete`;

SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Plan CREATE' LIMIT 1 INTO @`v_permission_plan_create`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Plan READ' LIMIT 1 INTO @`v_permission_plan_read`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Plan UPDATE' LIMIT 1 INTO @`v_permission_plan_update`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Plan DELETE' LIMIT 1 INTO @`v_permission_plan_delete`;

INSERT IGNORE INTO `security`.`security_v2_role_permission` (`ROLE_ID`, `PERMISSION_ID`)
VALUES (@`v_v2_role_plan_create`, @`v_permission_plan_create`),
       (@`v_v2_role_plan_read`, @`v_permission_plan_read`),
       (@`v_v2_role_plan_update`, @`v_permission_plan_update`),
       (@`v_v2_role_plan_delete`, @`v_permission_plan_delete`);

INSERT IGNORE INTO `security`.`security_v2_role_role` (`ROLE_ID`, `SUB_ROLE_ID`)
VALUES (@`v_v2_role_plan_manager`, @`v_v2_role_plan_create`),
       (@`v_v2_role_plan_manager`, @`v_v2_role_plan_read`),
       (@`v_v2_role_plan_manager`, @`v_v2_role_plan_update`),
       (@`v_v2_role_plan_manager`, @`v_v2_role_plan_delete`);

-- Invoice based permissions and roles

INSERT IGNORE INTO `security`.`security_permission` (`CLIENT_ID`, `NAME`, `DESCRIPTION`)
VALUES (@`v_client_system`, 'Invoice CREATE', 'Invoice create'),
       (@`v_client_system`, 'Invoice READ', 'Invoice read'),
       (@`v_client_system`, 'Invoice UPDATE', 'Invoice update'),
       (@`v_client_system`, 'Invoice DELETE', 'Invoice delete');

INSERT IGNORE INTO `security`.`security_v2_role` (`CLIENT_ID`, `NAME`, `SHORT_NAME`, `DESCRIPTION`)
VALUES (@`v_client_system`, 'Invoice CREATE', 'Create', 'Invoice create'),
       (@`v_client_system`, 'Invoice READ', 'Read', 'Invoice read'),
       (@`v_client_system`, 'Invoice UPDATE', 'Update', 'Invoice update'),
       (@`v_client_system`, 'Invoice DELETE', 'Delete', 'Invoice delete'),
       (@`v_client_system`, 'Invoice Manager', 'Manager', 'Invoice manager');

SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Invoice Manager' LIMIT 1 INTO @`v_v2_role_invoice_manager`;

SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Invoice CREATE' LIMIT 1 INTO @`v_v2_role_invoice_create`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Invoice READ' LIMIT 1 INTO @`v_v2_role_invoice_read`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Invoice UPDATE' LIMIT 1 INTO @`v_v2_role_invoice_update`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Invoice DELETE' LIMIT 1 INTO @`v_v2_role_invoice_delete`;

SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Invoice CREATE' LIMIT 1 INTO @`v_permission_invoice_create`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Invoice READ' LIMIT 1 INTO @`v_permission_invoice_read`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Invoice UPDATE' LIMIT 1 INTO @`v_permission_invoice_update`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Invoice DELETE' LIMIT 1 INTO @`v_permission_invoice_delete`;

INSERT IGNORE INTO `security`.`security_v2_role_permission` (`ROLE_ID`, `PERMISSION_ID`)
VALUES (@`v_v2_role_invoice_create`, @`v_permission_invoice_create`),
       (@`v_v2_role_invoice_read`, @`v_permission_invoice_read`),
       (@`v_v2_role_invoice_update`, @`v_permission_invoice_update`),
       (@`v_v2_role_invoice_delete`, @`v_permission_invoice_delete`);

INSERT IGNORE INTO `security`.`security_v2_role_role` (`ROLE_ID`, `SUB_ROLE_ID`)
VALUES (@`v_v2_role_invoice_manager`, @`v_v2_role_invoice_create`),
       (@`v_v2_role_invoice_manager`, @`v_v2_role_invoice_read`),
       (@`v_v2_role_invoice_manager`, @`v_v2_role_invoice_update`),
       (@`v_v2_role_invoice_manager`, @`v_v2_role_invoice_delete`);


-- Payment based permissions and roles

INSERT IGNORE INTO `security`.`security_permission` (`CLIENT_ID`, `NAME`, `DESCRIPTION`)
VALUES (@`v_client_system`, 'Payment CREATE', 'Payment create'),
       (@`v_client_system`, 'Payment READ', 'Payment read'),
       (@`v_client_system`, 'Payment UPDATE', 'Payment update'),
       (@`v_client_system`, 'Payment DELETE', 'Payment delete');

INSERT IGNORE INTO `security`.`security_v2_role` (`CLIENT_ID`, `NAME`, `SHORT_NAME`, `DESCRIPTION`)
VALUES (@`v_client_system`, 'Payment CREATE', 'Create', 'Payment create'),
       (@`v_client_system`, 'Payment READ', 'Read', 'Payment read'),
       (@`v_client_system`, 'Payment UPDATE', 'Update', 'Payment update'),
       (@`v_client_system`, 'Payment DELETE', 'Delete', 'Payment delete'),
       (@`v_client_system`, 'Payment Manager', 'Manager', 'Payment manager');

SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Payment Manager' LIMIT 1 INTO @`v_v2_role_payment_manager`;

SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Payment CREATE' LIMIT 1 INTO @`v_v2_role_payment_create`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Payment READ' LIMIT 1 INTO @`v_v2_role_payment_read`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Payment UPDATE' LIMIT 1 INTO @`v_v2_role_payment_update`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Payment DELETE' LIMIT 1 INTO @`v_v2_role_payment_delete`;

SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Payment CREATE' LIMIT 1 INTO @`v_permission_payment_create`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Payment READ' LIMIT 1 INTO @`v_permission_payment_read`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Payment UPDATE' LIMIT 1 INTO @`v_permission_payment_update`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Payment DELETE' LIMIT 1 INTO @`v_permission_payment_delete`;

INSERT IGNORE INTO `security`.`security_v2_role_permission` (`ROLE_ID`, `PERMISSION_ID`)
VALUES (@`v_v2_role_payment_create`, @`v_permission_payment_create`),
       (@`v_v2_role_payment_read`, @`v_permission_payment_read`),
       (@`v_v2_role_payment_update`, @`v_permission_payment_update`),
       (@`v_v2_role_payment_delete`, @`v_permission_payment_delete`);

INSERT IGNORE INTO `security`.`security_v2_role_role` (`ROLE_ID`, `SUB_ROLE_ID`)
VALUES (@`v_v2_role_payment_manager`, @`v_v2_role_payment_create`),
       (@`v_v2_role_payment_manager`, @`v_v2_role_payment_read`),
       (@`v_v2_role_payment_manager`, @`v_v2_role_payment_update`),
       (@`v_v2_role_payment_manager`, @`v_v2_role_payment_delete`);

-- Subscription based permissions and roles

INSERT IGNORE INTO `security`.`security_permission` (`CLIENT_ID`, `NAME`, `DESCRIPTION`)
VALUES (@`v_client_system`, 'Subscription CREATE', 'Subscription create'),
       (@`v_client_system`, 'Subscription READ', 'Subscription read'),
       (@`v_client_system`, 'Subscription UPDATE', 'Subscription update'),
       (@`v_client_system`, 'Subscription DELETE', 'Subscription delete');

INSERT IGNORE INTO `security`.`security_v2_role` (`CLIENT_ID`, `NAME`, `SHORT_NAME`, `DESCRIPTION`)
VALUES (@`v_client_system`, 'Subscription CREATE', 'Create', 'Subscription create'),
       (@`v_client_system`, 'Subscription READ', 'Read', 'Subscription read'),
       (@`v_client_system`, 'Subscription UPDATE', 'Update', 'Subscription update'),
       (@`v_client_system`, 'Subscription DELETE', 'Delete', 'Subscription delete'),
       (@`v_client_system`, 'Subscription Manager', 'Manager', 'Subscription manager'); 

SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Subscription Manager' LIMIT 1 INTO @`v_v2_role_subscription_manager`;    

SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Subscription CREATE' LIMIT 1 INTO @`v_v2_role_subscription_create`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Subscription READ' LIMIT 1 INTO @`v_v2_role_subscription_read`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Subscription UPDATE' LIMIT 1 INTO @`v_v2_role_subscription_update`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Subscription DELETE' LIMIT 1 INTO @`v_v2_role_subscription_delete`;

SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Subscription CREATE' LIMIT 1 INTO @`v_permission_subscription_create`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Subscription READ' LIMIT 1 INTO @`v_permission_subscription_read`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Subscription UPDATE' LIMIT 1 INTO @`v_permission_subscription_update`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Subscription DELETE' LIMIT 1 INTO @`v_permission_subscription_delete`;

INSERT IGNORE INTO `security`.`security_v2_role_permission` (`ROLE_ID`, `PERMISSION_ID`)
VALUES (@`v_v2_role_subscription_create`, @`v_permission_subscription_create`),
       (@`v_v2_role_subscription_read`, @`v_permission_subscription_read`),
       (@`v_v2_role_subscription_update`, @`v_permission_subscription_update`),
       (@`v_v2_role_subscription_delete`, @`v_permission_subscription_delete`);

INSERT IGNORE INTO `security`.`security_v2_role_role` (`ROLE_ID`, `SUB_ROLE_ID`)
VALUES (@`v_v2_role_subscription_manager`, @`v_v2_role_subscription_create`),
       (@`v_v2_role_subscription_manager`, @`v_v2_role_subscription_read`), 
       (@`v_v2_role_subscription_manager`, @`v_v2_role_subscription_update`),
       (@`v_v2_role_subscription_manager`, @`v_v2_role_subscription_delete`);

-- Plans Table

DROP TABLE IF EXISTS `security`.`security_payment_gateway`;
DROP TABLE IF EXISTS `security`.`security_payment`;
DROP TABLE IF EXISTS `security`.`security_invoice_item`;
DROP TABLE IF EXISTS `security`.`security_invoice`;
DROP TABLE IF EXISTS `security`.`security_client_plan`;
DROP TABLE IF EXISTS `security`.`security_plan_limit`;
DROP TABLE IF EXISTS `security`.`security_plan_cycle`;
DROP TABLE IF EXISTS `security`.`security_plan_app`;
DROP TABLE IF EXISTS `security`.`security_plan`;

CREATE TABLE `security`.`security_plan` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'URL Client ID for which this plan belongs to',
  `NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Name of the package',
  `DESCRIPTION` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Description of the package',
  `FEATURES` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Features of the plan',
  `STATUS` enum('ACTIVE','INACTIVE','DELETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status of the plan',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK2_PLAN_NAME_CLIENT_ID` (`NAME`,`CLIENT_ID`),
  KEY `FK1_PLAN_CLIENT_ID` (`CLIENT_ID`),
  CONSTRAINT `FK1_PLAN_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Apps part of a Plan Table

CREATE TABLE `security`.`security_plan_app` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `PLAN_ID` bigint unsigned NOT NULL COMMENT 'Plan ID',
  `APP_ID` bigint unsigned NOT NULL COMMENT 'App ID',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_PLAN_APP_PLAN_ID_APP_ID` (`PLAN_ID`,`APP_ID`),
  KEY `FK1_PLAN_APP_PLAN_ID` (`PLAN_ID`),
  KEY `FK2_PLAN_APP_APP_ID` (`APP_ID`),
  CONSTRAINT `FK1_PLAN_APP_PLAN_ID` FOREIGN KEY (`PLAN_ID`) REFERENCES `security_plan` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_PLAN_APP_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Plan Cycle Table

CREATE TABLE `security`.`security_plan_cycle` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Name of the cycle',
  `DESCRIPTION` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Description of the cycle',
  `PLAN_ID` bigint unsigned NOT NULL COMMENT 'Plan ID',
  `COST` decimal(10,2) NOT NULL COMMENT 'Cost of the plan',
  `CURRENCY` char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Currency of the plan',
  `TAX1` decimal(10,2) DEFAULT NULL COMMENT 'Tax1 of the plan',
  `TAX1_NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Tax1 name of the plan',
  `TAX2` decimal(10,2) DEFAULT NULL COMMENT 'Tax2 of the plan',
  `TAX2_NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Tax2 name of the plan',
  `TAX3` decimal(10,2) DEFAULT NULL COMMENT 'Tax3 of the plan',
  `TAX3_NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Tax3 name of the plan',
  `TAX4` decimal(10,2) DEFAULT NULL COMMENT 'Tax4 of the plan',
  `TAX4_NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Tax4 name of the plan',
  `TAX5` decimal(10,2) DEFAULT NULL COMMENT 'Tax5 of the plan',
  `TAX5_NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Tax5 name of the plan',
  `INTERVAL` int NOT NULL COMMENT 'Interval of the plan in days',
  `STATUS` enum('ACTIVE','INACTIVE','DELETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status of the cycle in a plan',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_PLAN_CYCLE_PLAN_ID_NAME` (`PLAN_ID`,`NAME`),
  KEY `FK1_PLAN_CYCLE_PLAN_ID` (`PLAN_ID`),
  CONSTRAINT `FK1_PLAN_CYCLE_PLAN_ID` FOREIGN KEY (`PLAN_ID`) REFERENCES `security_plan` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Limits Table

CREATE TABLE `security`.`security_plan_limit` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `PLAN_ID` bigint unsigned NOT NULL COMMENT 'Plan ID',
  `NAME` enum('USER', 'CLIENT', 'APP', 'FILE', 'CONNECTIONS', 'PAGES', 'STORAGE', 'CUSTOM') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Name of the limit',
  `CUSTOM_NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Custom name of the limit',
  `LIMIT` int NOT NULL COMMENT 'Limit of the plan',
  `STATUS` enum('ACTIVE','INACTIVE','DELETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status of the limit in a plan',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_PLAN_LIMIT_PLAN_ID_NAME` (`PLAN_ID`,`NAME`),
  KEY `FK1_PLAN_LIMIT_PLAN_ID` (`PLAN_ID`),
  CONSTRAINT `FK1_PLAN_LIMIT_PLAN_ID` FOREIGN KEY (`PLAN_ID`) REFERENCES `security_plan` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Client's plan Table

CREATE TABLE `security`.`security_client_plan` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client ID',
  `PLAN_ID` bigint unsigned NOT NULL COMMENT 'Plan ID',
  `CYCLE_ID` bigint unsigned NOT NULL COMMENT 'Cycle ID',
  `START_DATE` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Start date of the plan',
  `END_DATE` timestamp DEFAULT NULL COMMENT 'End date of the plan',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_CLIENT_PLAN_CLIENT_ID_PLAN_ID` (`CLIENT_ID`,`PLAN_ID`),
  KEY `FK1_CLIENT_PLAN_CLIENT_ID` (`CLIENT_ID`),
  KEY `FK2_CLIENT_PLAN_PLAN_ID` (`PLAN_ID`),
  CONSTRAINT `FK1_CLIENT_PLAN_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_CLIENT_PLAN_PLAN_ID` FOREIGN KEY (`PLAN_ID`) REFERENCES `security_plan` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Invoices Table

CREATE TABLE `security`.`security_invoice` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client ID',
  `PLAN_ID` bigint unsigned DEFAULT NULL COMMENT 'Plan ID',
  `CYCLE_ID` bigint unsigned DEFAULT NULL COMMENT 'Cycle ID',
  `INVOICE_NUMBER` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Invoice number',
  `INVOICE_DATE` timestamp NOT NULL COMMENT 'Invoice date',
  `INVOICE_DUE_DATE` timestamp NOT NULL COMMENT 'Invoice due date',
  `INVOICE_AMOUNT` decimal(10,2) NOT NULL COMMENT 'Invoice amount',
  `INVOICE_STATUS` enum('DRAFT', 'SENT', 'PENDING','PAID','FAILED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'Invoice status',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_INVOICE_CLIENT_ID_INVOICE_NUMBER` (`CLIENT_ID`,`INVOICE_NUMBER`),
  KEY `FK1_INVOICE_CLIENT_ID` (`CLIENT_ID`),
  KEY `FK2_INVOICE_PLAN_ID` (`PLAN_ID`),
  KEY `FK3_INVOICE_CYCLE_ID` (`CYCLE_ID`),
  CONSTRAINT `FK1_INVOICE_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_INVOICE_PLAN_ID` FOREIGN KEY (`PLAN_ID`) REFERENCES `security_plan` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK3_INVOICE_CYCLE_ID` FOREIGN KEY (`CYCLE_ID`) REFERENCES `security_plan_cycle` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Invoice Items Table

CREATE TABLE `security`.`security_invoice_item` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `INVOICE_ID` bigint unsigned NOT NULL COMMENT 'Invoice ID',
  `ITEM_NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Item name',
  `ITEM_DESCRIPTION` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Item description',
  `ITEM_AMOUNT` decimal(10,2) NOT NULL COMMENT 'Item amount',
  `ITEM_TAX1` decimal(10,2) DEFAULT NULL COMMENT 'Item tax1',
  `ITEM_TAX2` decimal(10,2) DEFAULT NULL COMMENT 'Item tax2',
  `ITEM_TAX3` decimal(10,2) DEFAULT NULL COMMENT 'Item tax3',
  `ITEM_TAX4` decimal(10,2) DEFAULT NULL COMMENT 'Item tax4',
  `ITEM_TAX5` decimal(10,2) DEFAULT NULL COMMENT 'Item tax5',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_INVOICE_ITEM_INVOICE_ID_ITEM_NAME` (`INVOICE_ID`,`ITEM_NAME`),
  KEY `FK1_INVOICE_ITEM_INVOICE_ID` (`INVOICE_ID`),
  CONSTRAINT `FK1_INVOICE_ITEM_INVOICE_ID` FOREIGN KEY (`INVOICE_ID`) REFERENCES `security_invoice` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Payments Table

CREATE TABLE `security`.`security_payment` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `INVOICE_ID` bigint unsigned NOT NULL COMMENT 'Invoice ID',
  `PAYMENT_DATE` timestamp NOT NULL COMMENT 'Payment date',
  `PAYMENT_AMOUNT` decimal(10,2) NOT NULL COMMENT 'Payment amount',
  `PAYMENT_STATUS` enum('PENDING','PAID','FAILED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'Payment status',
  `PAYMENT_METHOD` enum('CASHFREE', 'RAZORPAY', 'STRIPE', 'OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OTHER' COMMENT 'Payment method',
  `PAYMENT_REFERENCE` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Payment reference or trasaction id',
  `PAYMENT_RESPONSE` json DEFAULT NULL COMMENT 'Payment response or error message',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_PAYMENT_INVOICE_ID_PAYMENT_REFERENCE` (`INVOICE_ID`,`PAYMENT_REFERENCE`),
  KEY `FK1_PAYMENT_INVOICE_ID` (`INVOICE_ID`),
  CONSTRAINT `FK1_PAYMENT_INVOICE_ID` FOREIGN KEY (`INVOICE_ID`) REFERENCES `security_invoice` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Payment gateway details Table

CREATE TABLE `security`.`security_payment_gateway` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client ID for which this payment gateway belongs to',
  `PAYMENT_GATEWAY` enum('CASHFREE', 'RAZORPAY', 'STRIPE', 'OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OTHER' COMMENT 'Payment gateway',
  `PAYMENT_GATEWAY_DETAILS` json NOT NULL COMMENT 'Payment gateway details',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_PAYMENT_GATEWAY_CLIENT_ID_PAYMENT_GATEWAY` (`CLIENT_ID`,`PAYMENT_GATEWAY`),
  KEY `FK1_PAYMENT_GATEWAY_CLIENT_ID` (`CLIENT_ID`),
  CONSTRAINT `FK1_PAYMENT_GATEWAY_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;