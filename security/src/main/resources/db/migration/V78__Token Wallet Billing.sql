use `security`;

-- ============================================================================
-- V78: Replace subscription billing with a prepaid token-wallet model.
--
-- Tokens become the universal billing unit. The subscription stack
-- (Plan / PlanCycle / PlanLimit / ClientPlan + InvoiceItem) is dropped (we are
-- not in production with billing). Payment + PaymentGateway are kept; Invoice is
-- rebuilt lean for token purchases only. New tables add the wallet, ledger,
-- per-app billing config, action catalog/pricing.
-- ============================================================================

SELECT `ID`
  FROM `security`.`security_client`
 WHERE `CODE` = 'SYSTEM'
 LIMIT 1
  INTO @`v_client_system`;

-- ---------------------------------------------------------------------------
-- 1. Drop the subscription stack (FK-dependency order).
--    Payment & Invoice are recreated lean further below.
--    The obsolete Plan/Subscription RBAC rows are left as harmless dead data
--    (no code references them; cleaned up in a later pass if desired).
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS `security`.`security_payment`;
DROP TABLE IF EXISTS `security`.`security_invoice_item`;
DROP TABLE IF EXISTS `security`.`security_invoice`;
DROP TABLE IF EXISTS `security`.`security_client_plan`;
DROP TABLE IF EXISTS `security`.`security_plan_limit`;
DROP TABLE IF EXISTS `security`.`security_plan_cycle`;
DROP TABLE IF EXISTS `security`.`security_plan_app`;
DROP TABLE IF EXISTS `security`.`security_plan`;

-- ---------------------------------------------------------------------------
-- 2. Wallet (one per client). Single shared balance; per-app spend is tracked
--    on the ledger and capped via security_wallet_budget.
-- ---------------------------------------------------------------------------

CREATE TABLE `security`.`security_wallet` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client (tenant) that owns this wallet',
  `BALANCE` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Available token balance',
  `RESERVED_BALANCE` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Tokens reserved for in-flight actions',
  `CURRENCY` char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INR' COMMENT 'Settlement currency for top-up valuation',
  `STATUS` enum('ACTIVE','SUSPENDED','CLOSED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'Wallet status',
  `LOW_BALANCE_THRESHOLD` decimal(19,4) DEFAULT NULL COMMENT 'Balance below which low-balance alerts fire',
  `GRACE_FLOOR` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Most-negative balance allowed for ENGAGEMENT-class actions',
  `AUTO_RECHARGE_ENABLED` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Auto-recharge (deferred to post-v1)',
  `AUTO_RECHARGE_THRESHOLD` decimal(19,4) DEFAULT NULL COMMENT 'Balance that triggers auto-recharge',
  `AUTO_RECHARGE_AMOUNT` decimal(19,4) DEFAULT NULL COMMENT 'Tokens to buy on auto-recharge',
  `AUTO_RECHARGE_GATEWAY` enum('CASHFREE','RAZORPAY','STRIPE','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Gateway used for auto-recharge',
  `VERSION` bigint unsigned NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock counter',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_WALLET_CLIENT_ID` (`CLIENT_ID`),
  CONSTRAINT `FK1_WALLET_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 3. Per-app billing config (replaces Plan as the per-app config home).
-- ---------------------------------------------------------------------------

CREATE TABLE `security`.`security_app_billing_config` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `APP_ID` bigint unsigned NOT NULL COMMENT 'App this billing config applies to',
  `DEFAULT_PAYMENT_GATEWAY` enum('CASHFREE','RAZORPAY','STRIPE','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OTHER' COMMENT 'Default gateway for top-ups',
  `SEAT_BILLING_ENABLED` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Whether per-seat token burn applies',
  `SEAT_TOKENS_PER_MONTH` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Tokens charged per seat per month, dripped hourly',
  `MONTHLY_FREE_TOKENS` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Free tokens granted per month',
  `ENFORCED` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Turn-on flag: when 0 metering runs in shadow (no blocking)',
  `STATUS` enum('ACTIVE','INACTIVE','DELETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_APP_BILLING_CONFIG_APP_ID` (`APP_ID`),
  CONSTRAINT `FK1_APP_BILLING_CONFIG_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 4. Optional soft per-app budget caps (against the single shared balance).
-- ---------------------------------------------------------------------------

CREATE TABLE `security`.`security_wallet_budget` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `WALLET_ID` bigint unsigned NOT NULL COMMENT 'Wallet this cap belongs to',
  `APP_ID` bigint unsigned NOT NULL COMMENT 'App the cap applies to',
  `CAP_CREDITS` decimal(19,4) NOT NULL COMMENT 'Max tokens spendable on this app per period',
  `PERIOD` enum('DAY','MONTH') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MONTH' COMMENT 'Cap reset period',
  `CONSUMED_THIS_PERIOD` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Tokens consumed in the current period',
  `PERIOD_START` timestamp NULL DEFAULT NULL COMMENT 'Start of the current period',
  `STATUS` enum('ACTIVE','INACTIVE','DELETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_WALLET_BUDGET_WALLET_ID_APP_ID` (`WALLET_ID`,`APP_ID`),
  KEY `FK2_WALLET_BUDGET_APP_ID` (`APP_ID`),
  CONSTRAINT `FK1_WALLET_BUDGET_WALLET_ID` FOREIGN KEY (`WALLET_ID`) REFERENCES `security_wallet` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_WALLET_BUDGET_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 5. Append-only wallet ledger. Idempotency via (WALLET_ID, IDEMPOTENCY_KEY).
-- ---------------------------------------------------------------------------

CREATE TABLE `security`.`security_wallet_transaction` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `WALLET_ID` bigint unsigned NOT NULL COMMENT 'Wallet this entry belongs to',
  `TRANSACTION_TYPE` enum('DEBIT','CREDIT','RESERVE','RELEASE','GRANT','TOPUP','REFUND','ADJUSTMENT','SEAT_BURN') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Ledger entry type',
  `CREDITS` decimal(19,4) NOT NULL COMMENT 'Token magnitude; sign implied by TRANSACTION_TYPE',
  `BALANCE_AFTER` decimal(19,4) NOT NULL COMMENT 'Available balance snapshot after this entry',
  `RESERVED_AFTER` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Reserved balance snapshot after this entry',
  `ACTION_KEY` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Metered action key (soft ref to action catalog)',
  `APP_ID` bigint unsigned DEFAULT NULL COMMENT 'App the action was scoped to',
  `QUANTITY` decimal(19,4) DEFAULT NULL COMMENT 'Units consumed (tokens, segments, etc.)',
  `SHADOW` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Would-be debit recorded in shadow mode (balance unchanged)',
  `REFERENCE_TYPE` enum('INVOICE','PAYMENT','GRANT','ACTION','RESERVATION','ADJUSTMENT','REFUND','SEAT_BURN') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Type of the related reference',
  `REFERENCE_ID` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Related reference id',
  `IDEMPOTENCY_KEY` varchar(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Caller-supplied idempotency key',
  `DESCRIPTION` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Human-readable description',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_WALLET_TXN_IDEMPOTENCY` (`WALLET_ID`,`IDEMPOTENCY_KEY`),
  KEY `IDX1_WALLET_TXN_WALLET_CREATED` (`WALLET_ID`,`CREATED_AT`),
  KEY `IDX2_WALLET_TXN_WALLET_APP_CREATED` (`WALLET_ID`,`APP_ID`,`CREATED_AT`),
  KEY `FK2_WALLET_TXN_APP_ID` (`APP_ID`),
  CONSTRAINT `FK1_WALLET_TXN_WALLET_ID` FOREIGN KEY (`WALLET_ID`) REFERENCES `security_wallet` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_WALLET_TXN_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 6. Platform action catalog (master list of meterable actions).
-- ---------------------------------------------------------------------------

CREATE TABLE `security`.`security_action_catalog` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `ACTION_KEY` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Stable action key, e.g. core.function.execute',
  `NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Display name',
  `DESCRIPTION` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Description',
  `DEFAULT_ACTION_CLASS` enum('ENGAGEMENT','METERED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'METERED' COMMENT 'Default class; drives zero-balance behavior',
  `DEFAULT_UNIT_COST` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Fallback credit cost per unit when no per-app override',
  `UNIT_NAME` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Unit name, e.g. per_call, per_1k_tokens',
  `STATUS` enum('ACTIVE','INACTIVE','DELETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_ACTION_CATALOG_ACTION_KEY` (`ACTION_KEY`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 7. Per-app per-action credit cost override.
-- ---------------------------------------------------------------------------

CREATE TABLE `security`.`security_app_action_cost` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `APP_ID` bigint unsigned NOT NULL COMMENT 'App the cost applies to',
  `ACTION_KEY` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Action key (soft ref to action catalog)',
  `CREDIT_COST` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Credits per unit for this app',
  `ACTION_CLASS_OVERRIDE` enum('ENGAGEMENT','METERED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Overrides the catalog class for this app',
  `FREE_QUOTA` decimal(19,4) DEFAULT NULL COMMENT 'Free units per period before charging',
  `STATUS` enum('ACTIVE','INACTIVE','DELETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_APP_ACTION_COST_APP_ID_ACTION_KEY` (`APP_ID`,`ACTION_KEY`),
  CONSTRAINT `FK1_APP_ACTION_COST_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 8. Vendor cost -> tokens pricing (with markup). Versioned by EFFECTIVE_FROM.
-- ---------------------------------------------------------------------------

CREATE TABLE `security`.`security_credit_pricing` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Owning client (SYSTEM for platform default, partner for overrides)',
  `COST_BASIS_TYPE` enum('LLM_TOKENS','SMS_SEGMENT','EMAIL','STORAGE_GB','API_CALL','SEAT','CUSTOM') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'What the cost is based on',
  `VENDOR` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Vendor, e.g. anthropic, openai, twilio',
  `MODEL_OR_SKU` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Model or SKU, e.g. claude-opus-4-8',
  `UNIT` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Unit, e.g. per_1k_input_tokens, per_1k_output_tokens, per_segment',
  `VENDOR_UNIT_COST` decimal(19,6) NOT NULL DEFAULT 0 COMMENT 'Real vendor cost per unit in CURRENCY',
  `CURRENCY` char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INR' COMMENT 'Currency of VENDOR_UNIT_COST',
  `MARKUP_MULTIPLIER` decimal(10,4) NOT NULL DEFAULT 1.0000 COMMENT 'Margin multiplier, e.g. 1.5 = 50% markup',
  `CREDITS_PER_CURRENCY_UNIT` decimal(19,6) NOT NULL DEFAULT 1.000000 COMMENT 'Tokens per 1 unit of CURRENCY',
  `EFFECTIVE_FROM` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Price effective from',
  `EFFECTIVE_TO` timestamp NULL DEFAULT NULL COMMENT 'Price effective until (null = current)',
  `STATUS` enum('ACTIVE','INACTIVE','DELETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  KEY `FK1_CREDIT_PRICING_CLIENT_ID` (`CLIENT_ID`),
  KEY `IDX1_CREDIT_PRICING_LOOKUP` (`COST_BASIS_TYPE`,`VENDOR`,`MODEL_OR_SKU`,`UNIT`,`STATUS`),
  CONSTRAINT `FK1_CREDIT_PRICING_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 9. Lean invoice (token purchases only). No plan/cycle/proration/line-items.
-- ---------------------------------------------------------------------------

CREATE TABLE `security`.`security_invoice` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client ID',
  `WALLET_ID` bigint unsigned DEFAULT NULL COMMENT 'Wallet credited on payment',
  `INVOICE_NUMBER` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Invoice number',
  `INVOICE_TYPE` enum('TOPUP','AUTO_RECHARGE','CREDIT_REFUND') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TOPUP' COMMENT 'Invoice type',
  `INVOICE_DATE` timestamp NOT NULL COMMENT 'Invoice date',
  `INVOICE_DUE_DATE` timestamp NOT NULL COMMENT 'Invoice due date',
  `INVOICE_AMOUNT` decimal(19,4) NOT NULL COMMENT 'Invoice amount in CURRENCY',
  `CURRENCY` char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INR' COMMENT 'Currency',
  `CREDITS_PURCHASED` decimal(19,4) DEFAULT NULL COMMENT 'Tokens to grant on payment success',
  `TAX_AMOUNT` decimal(19,4) DEFAULT NULL COMMENT 'Total tax amount',
  `TAX_BREAKUP` json DEFAULT NULL COMMENT 'Tax line breakup',
  `INVOICE_STATUS` enum('PENDING','PAID','FAILED','CANCELLED','REFUNDED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'Invoice status',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_INVOICE_CLIENT_ID_INVOICE_NUMBER` (`CLIENT_ID`,`INVOICE_NUMBER`),
  KEY `FK1_INVOICE_CLIENT_ID` (`CLIENT_ID`),
  KEY `FK2_INVOICE_WALLET_ID` (`WALLET_ID`),
  CONSTRAINT `FK1_INVOICE_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_INVOICE_WALLET_ID` FOREIGN KEY (`WALLET_ID`) REFERENCES `security_wallet` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 10. Payment (recreated identical to the kept schema; FK -> lean invoice).
-- ---------------------------------------------------------------------------

CREATE TABLE `security`.`security_payment` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `INVOICE_ID` bigint unsigned NOT NULL COMMENT 'Invoice ID',
  `PAYMENT_DATE` timestamp NOT NULL COMMENT 'Payment date',
  `PAYMENT_AMOUNT` decimal(19,4) NOT NULL COMMENT 'Payment amount',
  `PAYMENT_STATUS` enum('PENDING','PAID','FAILED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'Payment status',
  `PAYMENT_METHOD` enum('CASHFREE','RAZORPAY','STRIPE','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OTHER' COMMENT 'Payment method',
  `PAYMENT_REFERENCE` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Payment reference or transaction id',
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

-- ---------------------------------------------------------------------------
-- 11. SOX log object names: add WALLET, APP_BILLING_CONFIG, ACTION_CATALOG.
-- ---------------------------------------------------------------------------

ALTER TABLE `security`.`security_sox_log`
    CHANGE COLUMN `OBJECT_NAME` `OBJECT_NAME` ENUM('USER', 'ROLE', 'PERMISSION', 'PACKAGE', 'CLIENT', 'CLIENT_TYPE', 'APP', 'PROFILE', 'INVOICE', 'WALLET', 'APP_BILLING_CONFIG', 'ACTION_CATALOG') CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_unicode_ci' NOT NULL COMMENT 'Operation on the object' ;

-- ---------------------------------------------------------------------------
-- 12. RBAC: Wallet, Billing Config and Action Catalog permissions and roles.
--     (Invoice & Payment roles from V61 are retained.)
-- ---------------------------------------------------------------------------

INSERT IGNORE INTO `security`.`security_permission` (`CLIENT_ID`, `NAME`, `DESCRIPTION`)
VALUES (@`v_client_system`, 'Wallet CREATE', 'Wallet create'),
       (@`v_client_system`, 'Wallet READ', 'Wallet read'),
       (@`v_client_system`, 'Wallet UPDATE', 'Wallet update'),
       (@`v_client_system`, 'Wallet DELETE', 'Wallet delete'),
       (@`v_client_system`, 'Wallet CHARGE', 'Wallet charge (internal metering)'),
       (@`v_client_system`, 'Billing Config CREATE', 'Billing config create'),
       (@`v_client_system`, 'Billing Config READ', 'Billing config read'),
       (@`v_client_system`, 'Billing Config UPDATE', 'Billing config update'),
       (@`v_client_system`, 'Billing Config DELETE', 'Billing config delete'),
       (@`v_client_system`, 'Action Catalog CREATE', 'Action catalog create'),
       (@`v_client_system`, 'Action Catalog READ', 'Action catalog read'),
       (@`v_client_system`, 'Action Catalog UPDATE', 'Action catalog update'),
       (@`v_client_system`, 'Action Catalog DELETE', 'Action catalog delete');

INSERT IGNORE INTO `security`.`security_v2_role` (`CLIENT_ID`, `NAME`, `SHORT_NAME`, `DESCRIPTION`)
VALUES (@`v_client_system`, 'Wallet CREATE', 'Create', 'Wallet create'),
       (@`v_client_system`, 'Wallet READ', 'Read', 'Wallet read'),
       (@`v_client_system`, 'Wallet UPDATE', 'Update', 'Wallet update'),
       (@`v_client_system`, 'Wallet DELETE', 'Delete', 'Wallet delete'),
       (@`v_client_system`, 'Wallet CHARGE', 'Charge', 'Wallet charge'),
       (@`v_client_system`, 'Wallet Manager', 'Manager', 'Wallet manager'),
       (@`v_client_system`, 'Billing Config CREATE', 'Create', 'Billing config create'),
       (@`v_client_system`, 'Billing Config READ', 'Read', 'Billing config read'),
       (@`v_client_system`, 'Billing Config UPDATE', 'Update', 'Billing config update'),
       (@`v_client_system`, 'Billing Config DELETE', 'Delete', 'Billing config delete'),
       (@`v_client_system`, 'Billing Config Manager', 'Manager', 'Billing config manager'),
       (@`v_client_system`, 'Action Catalog CREATE', 'Create', 'Action catalog create'),
       (@`v_client_system`, 'Action Catalog READ', 'Read', 'Action catalog read'),
       (@`v_client_system`, 'Action Catalog UPDATE', 'Update', 'Action catalog update'),
       (@`v_client_system`, 'Action Catalog DELETE', 'Delete', 'Action catalog delete'),
       (@`v_client_system`, 'Action Catalog Manager', 'Manager', 'Action catalog manager');

-- Resolve manager role ids
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Wallet Manager' LIMIT 1 INTO @`v_role_wallet_manager`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Billing Config Manager' LIMIT 1 INTO @`v_role_billing_manager`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Action Catalog Manager' LIMIT 1 INTO @`v_role_action_manager`;

-- Resolve CRUD/charge role ids
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Wallet CREATE' LIMIT 1 INTO @`v_role_wallet_create`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Wallet READ'   LIMIT 1 INTO @`v_role_wallet_read`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Wallet UPDATE' LIMIT 1 INTO @`v_role_wallet_update`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Wallet DELETE' LIMIT 1 INTO @`v_role_wallet_delete`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Wallet CHARGE' LIMIT 1 INTO @`v_role_wallet_charge`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Billing Config CREATE' LIMIT 1 INTO @`v_role_billing_create`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Billing Config READ'   LIMIT 1 INTO @`v_role_billing_read`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Billing Config UPDATE' LIMIT 1 INTO @`v_role_billing_update`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Billing Config DELETE' LIMIT 1 INTO @`v_role_billing_delete`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Action Catalog CREATE' LIMIT 1 INTO @`v_role_action_create`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Action Catalog READ'   LIMIT 1 INTO @`v_role_action_read`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Action Catalog UPDATE' LIMIT 1 INTO @`v_role_action_update`;
SELECT `ID` FROM `security`.`security_v2_role` WHERE `NAME` = 'Action Catalog DELETE' LIMIT 1 INTO @`v_role_action_delete`;

-- Resolve permission ids
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Wallet CREATE' LIMIT 1 INTO @`v_perm_wallet_create`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Wallet READ'   LIMIT 1 INTO @`v_perm_wallet_read`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Wallet UPDATE' LIMIT 1 INTO @`v_perm_wallet_update`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Wallet DELETE' LIMIT 1 INTO @`v_perm_wallet_delete`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Wallet CHARGE' LIMIT 1 INTO @`v_perm_wallet_charge`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Billing Config CREATE' LIMIT 1 INTO @`v_perm_billing_create`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Billing Config READ'   LIMIT 1 INTO @`v_perm_billing_read`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Billing Config UPDATE' LIMIT 1 INTO @`v_perm_billing_update`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Billing Config DELETE' LIMIT 1 INTO @`v_perm_billing_delete`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Action Catalog CREATE' LIMIT 1 INTO @`v_perm_action_create`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Action Catalog READ'   LIMIT 1 INTO @`v_perm_action_read`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Action Catalog UPDATE' LIMIT 1 INTO @`v_perm_action_update`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Action Catalog DELETE' LIMIT 1 INTO @`v_perm_action_delete`;

INSERT IGNORE INTO `security`.`security_v2_role_permission` (`ROLE_ID`, `PERMISSION_ID`)
VALUES (@`v_role_wallet_create`, @`v_perm_wallet_create`),
       (@`v_role_wallet_read`,   @`v_perm_wallet_read`),
       (@`v_role_wallet_update`, @`v_perm_wallet_update`),
       (@`v_role_wallet_delete`, @`v_perm_wallet_delete`),
       (@`v_role_wallet_charge`, @`v_perm_wallet_charge`),
       (@`v_role_billing_create`, @`v_perm_billing_create`),
       (@`v_role_billing_read`,   @`v_perm_billing_read`),
       (@`v_role_billing_update`, @`v_perm_billing_update`),
       (@`v_role_billing_delete`, @`v_perm_billing_delete`),
       (@`v_role_action_create`, @`v_perm_action_create`),
       (@`v_role_action_read`,   @`v_perm_action_read`),
       (@`v_role_action_update`, @`v_perm_action_update`),
       (@`v_role_action_delete`, @`v_perm_action_delete`);

INSERT IGNORE INTO `security`.`security_v2_role_role` (`ROLE_ID`, `SUB_ROLE_ID`)
VALUES (@`v_role_wallet_manager`, @`v_role_wallet_create`),
       (@`v_role_wallet_manager`, @`v_role_wallet_read`),
       (@`v_role_wallet_manager`, @`v_role_wallet_update`),
       (@`v_role_wallet_manager`, @`v_role_wallet_delete`),
       (@`v_role_wallet_manager`, @`v_role_wallet_charge`),
       (@`v_role_billing_manager`, @`v_role_billing_create`),
       (@`v_role_billing_manager`, @`v_role_billing_read`),
       (@`v_role_billing_manager`, @`v_role_billing_update`),
       (@`v_role_billing_manager`, @`v_role_billing_delete`),
       (@`v_role_action_manager`, @`v_role_action_create`),
       (@`v_role_action_manager`, @`v_role_action_read`),
       (@`v_role_action_manager`, @`v_role_action_update`),
       (@`v_role_action_manager`, @`v_role_action_delete`);
