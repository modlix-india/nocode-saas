use `security`;

-- =====================================================================
-- Token-based billing: clean rebuild.
-- Drops the old subscription/plan + multi-gateway billing stack and
-- builds a flat per-(client, app) token wallet model. Idempotent:
-- DROP IF EXISTS for the removed tables, CREATE TABLE IF NOT EXISTS for
-- the new ones. Kiran applies this manually on local.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Drop the old subscription + billing + payment tables (children first)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `security`.`security_payment_gateway`;
DROP TABLE IF EXISTS `security`.`security_payment`;
DROP TABLE IF EXISTS `security`.`security_invoice_item`;
DROP TABLE IF EXISTS `security`.`security_invoice`;
DROP TABLE IF EXISTS `security`.`security_client_plan`;
DROP TABLE IF EXISTS `security`.`security_plan_limit`;
DROP TABLE IF EXISTS `security`.`security_plan_cycle`;
DROP TABLE IF EXISTS `security`.`security_plan_app`;
DROP TABLE IF EXISTS `security`.`security_plan`;

-- ---------------------------------------------------------------------
-- 2. Per-(configurator client C, app) billing config
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `security`.`security_app_billing_config` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Configurator client C that owns these billing terms',
  `APP_ID` bigint unsigned NOT NULL COMMENT 'App these terms apply to',
  `APP_RENT_PER_MONTH` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Tokens/month per authored app (appbuilder only)',
  `SITE_RENT_PER_MONTH` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Tokens/month per authored site (appbuilder/sitezump)',
  `FILES_TOKENS_PER_MONTH` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Tokens/month per GB stored',
  `STORAGE_ROW_TOKENS_PER_MONTH` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Tokens/month per storage row',
  `DEAL_TOKENS_PER_MONTH` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Tokens/month per deal',
  `USER_TOKENS_PER_MONTH` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Tokens/month per user',
  `AI_TOKENS_PER_MILLION` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Billing tokens per 1,000,000 LLM tokens',
  `FREE_APPS` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Free apps allowance',
  `FREE_SITES` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Free sites allowance',
  `FREE_FILES_GB` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Free GB allowance',
  `FREE_STORAGE_ROWS` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Free storage rows allowance',
  `FREE_DEALS` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Free deals allowance',
  `FREE_USERS` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Free users allowance',
  `FREE_AI_TOKENS_PER_MONTH` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Free LLM tokens per month (cumulative grant)',
  `GST_PERCENTAGE` decimal(5,2) NOT NULL DEFAULT 0 COMMENT 'GST percentage applied to bundle purchases',
  `PAYMENT_GATEWAY` enum('RAZORPAY','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RAZORPAY' COMMENT 'Payment gateway for this config',
  `PAYMENT_GATEWAY_CONFIG` json DEFAULT NULL COMMENT 'Encrypted gateway credentials (key id/secret, webhook secret); masked on read',
  `SELLER_LEGAL_NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Seller-of-record legal name for invoices',
  `SELLER_GSTIN` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Seller-of-record GSTIN',
  `SELLER_ADDRESS` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Seller-of-record address',
  `LOW_BALANCE_THRESHOLD` decimal(19,4) DEFAULT NULL COMMENT 'Token level at which to warn the client',
  `SUSPEND_APP_CODE` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'App code to serve when a wallet under this config is suspended',
  `SUSPEND_CLIENT_CODE` char(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Client code to serve when suspended',
  `STATUS` enum('ACTIVE','INACTIVE','DELETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status of the config',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_ABC_CLIENT_ID_APP_ID` (`CLIENT_ID`,`APP_ID`),
  KEY `FK1_ABC_CLIENT_ID` (`CLIENT_ID`),
  KEY `FK2_ABC_APP_ID` (`APP_ID`),
  CONSTRAINT `FK1_ABC_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_ABC_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3. Token bundles (per config); FIXED tier or CUSTOM per-token price
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `security`.`security_app_billing_bundle` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `BILLING_CONFIG_ID` bigint unsigned NOT NULL COMMENT 'Owning billing config',
  `LABEL` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Display label',
  `BUNDLE_TYPE` enum('FIXED','CUSTOM') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FIXED' COMMENT 'FIXED tokens+price tier, or CUSTOM per-token price',
  `TOKENS` decimal(19,4) DEFAULT NULL COMMENT 'Tokens granted (FIXED)',
  `PRICE` decimal(19,4) DEFAULT NULL COMMENT 'Price (FIXED)',
  `PRICE_PER_TOKEN` decimal(19,6) DEFAULT NULL COMMENT 'Per-token price (CUSTOM)',
  `MIN_TOKENS` decimal(19,4) DEFAULT NULL COMMENT 'Minimum tokens for a CUSTOM purchase',
  `MAX_TOKENS` decimal(19,4) DEFAULT NULL COMMENT 'Maximum tokens for a CUSTOM purchase',
  `CURRENCY` char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INR' COMMENT 'Currency',
  `STATUS` enum('ACTIVE','INACTIVE','DELETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status',
  `DISPLAY_ORDER` int NOT NULL DEFAULT 0 COMMENT 'Display order',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  KEY `FK1_ABB_BILLING_CONFIG_ID` (`BILLING_CONFIG_ID`),
  CONSTRAINT `FK1_ABB_BILLING_CONFIG_ID` FOREIGN KEY (`BILLING_CONFIG_ID`) REFERENCES `security_app_billing_config` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 4. Wallet (one per billed client M + app)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `security`.`security_wallet` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Billed client M',
  `APP_ID` bigint unsigned NOT NULL COMMENT 'App this wallet funds',
  `BALANCE` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'Current token balance (may go negative once, then suspended)',
  `STATUS` enum('ACTIVE','SUSPENDED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'Wallet status',
  `ALERT_THRESHOLD` decimal(19,4) DEFAULT NULL COMMENT 'Overrides config low-balance threshold for this wallet',
  `LOW_BALANCE_NOTIFIED` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Latch: low-balance alert already raised until refilled',
  `VERSION` int NOT NULL DEFAULT 0 COMMENT 'Optimistic version',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_WALLET_CLIENT_ID_APP_ID` (`CLIENT_ID`,`APP_ID`),
  KEY `FK1_WALLET_CLIENT_ID` (`CLIENT_ID`),
  KEY `FK2_WALLET_APP_ID` (`APP_ID`),
  CONSTRAINT `FK1_WALLET_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_WALLET_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 5. Wallet transaction ledger (append-only)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `security`.`security_wallet_transaction` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `WALLET_ID` bigint unsigned NOT NULL COMMENT 'Wallet',
  `TYPE` enum('CREDIT','DEBIT','ADJUST','SUSPEND','REACTIVATE','REFUND') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Transaction type',
  `TOKENS` decimal(19,4) NOT NULL COMMENT 'Token amount (positive)',
  `BALANCE_AFTER` decimal(19,4) NOT NULL COMMENT 'Balance after this transaction',
  `ACTION_KEY` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Metered action key (debits)',
  `APP_ID` bigint unsigned DEFAULT NULL COMMENT 'App context',
  `QUANTITY` decimal(19,4) DEFAULT NULL COMMENT 'Raw usage units charged',
  `CHARGE_DATE` date DEFAULT NULL COMMENT 'Charge day (for windowed rent)',
  `WINDOW_INDEX` smallint DEFAULT NULL COMMENT '15-minute window of the day, 0..95',
  `IDEMPOTENCY_KEY` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Idempotency key (unique per wallet)',
  `REFERENCE_TYPE` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Reference type (e.g. INVOICE)',
  `REFERENCE_ID` bigint unsigned DEFAULT NULL COMMENT 'Reference id',
  `REASON` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Required reason for this mutation',
  `DESCRIPTION` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Optional description',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'Acting user; null for worker/machine charges',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_WTXN_WALLET_ID_IDEMPOTENCY_KEY` (`WALLET_ID`,`IDEMPOTENCY_KEY`),
  KEY `IDX1_WTXN_WALLET_ID_CREATED_AT` (`WALLET_ID`,`CREATED_AT`),
  KEY `IDX2_WTXN_WALLET_ID_CHARGE_DATE_WINDOW` (`WALLET_ID`,`CHARGE_DATE`,`WINDOW_INDEX`),
  KEY `FK2_WTXN_APP_ID` (`APP_ID`),
  CONSTRAINT `FK1_WTXN_WALLET_ID` FOREIGN KEY (`WALLET_ID`) REFERENCES `security_wallet` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_WTXN_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 6. Lean token-purchase invoice (immutable snapshot of seller + buyer)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `security`.`security_invoice` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `INVOICE_NUMBER` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Gapless sequential per seller + financial year',
  `INVOICE_DATE` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Invoice date',
  `STATUS` enum('PENDING','PAID','FAILED','UNDER_REVIEW') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'Invoice status',
  `SELLER_CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Seller of record (configurator C)',
  `SELLER_LEGAL_NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Seller legal name snapshot',
  `SELLER_GSTIN` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Seller GSTIN snapshot',
  `SELLER_ADDRESS` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Seller address snapshot',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Buyer client M',
  `BUYER_LEGAL_NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Buyer legal name snapshot',
  `BUYER_GSTIN` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Buyer GSTIN snapshot',
  `BUYER_ADDRESS` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Buyer address snapshot',
  `APP_ID` bigint unsigned NOT NULL COMMENT 'App whose wallet is funded',
  `BUNDLE_ID` bigint unsigned DEFAULT NULL COMMENT 'Bundle purchased (soft reference)',
  `BUNDLE_LABEL` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Bundle label snapshot',
  `TOKENS_PURCHASED` decimal(19,4) NOT NULL COMMENT 'Tokens credited on payment',
  `BASE_AMOUNT` decimal(19,4) NOT NULL COMMENT 'Taxable value',
  `GST_PERCENTAGE` decimal(5,2) NOT NULL DEFAULT 0 COMMENT 'GST percentage applied',
  `GST_AMOUNT` decimal(19,4) NOT NULL DEFAULT 0 COMMENT 'GST amount (base x pct)',
  `TOTAL_AMOUNT` decimal(19,4) NOT NULL COMMENT 'Base + GST',
  `CURRENCY` char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INR' COMMENT 'Currency',
  `PAYMENT_REFERENCE` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Gateway payment reference',
  `GATEWAY` enum('RAZORPAY','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RAZORPAY' COMMENT 'Payment gateway',
  `PAID_AT` timestamp NULL DEFAULT NULL COMMENT 'Time payment was confirmed',
  `PDF_FILE_KEY` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Stored PDF file key',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_INVOICE_SELLER_CLIENT_ID_INVOICE_NUMBER` (`SELLER_CLIENT_ID`,`INVOICE_NUMBER`),
  KEY `FK1_INVOICE_SELLER_CLIENT_ID` (`SELLER_CLIENT_ID`),
  KEY `FK2_INVOICE_CLIENT_ID` (`CLIENT_ID`),
  KEY `FK3_INVOICE_APP_ID` (`APP_ID`),
  CONSTRAINT `FK1_INVOICE_SELLER_CLIENT_ID` FOREIGN KEY (`SELLER_CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_INVOICE_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK3_INVOICE_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 7. Per-seller + financial-year invoice number counter (gapless)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `security`.`security_invoice_counter` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `SELLER_CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Seller client',
  `FIN_YEAR` char(9) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Financial year e.g. 2026-2027',
  `LAST_NUMBER` bigint unsigned NOT NULL DEFAULT 0 COMMENT 'Last allocated sequence number',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_INV_COUNTER_SELLER_FY` (`SELLER_CLIENT_ID`,`FIN_YEAR`),
  CONSTRAINT `FK1_INV_COUNTER_SELLER_CLIENT_ID` FOREIGN KEY (`SELLER_CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 8. Lean payment (Razorpay-only)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `security`.`security_payment` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `INVOICE_ID` bigint unsigned NOT NULL COMMENT 'Invoice paid',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Buyer client',
  `GATEWAY` enum('RAZORPAY','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RAZORPAY' COMMENT 'Payment gateway',
  `GATEWAY_ORDER_ID` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Gateway order id',
  `GATEWAY_PAYMENT_ID` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Gateway payment id',
  `AMOUNT` decimal(19,4) NOT NULL COMMENT 'Payment amount',
  `STATUS` enum('PENDING','PAID','FAILED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'Payment status',
  `RESPONSE` json DEFAULT NULL COMMENT 'Gateway response / error',
  `PAID_AT` timestamp NULL DEFAULT NULL COMMENT 'Time payment confirmed',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_PAYMENT_GATEWAY_ORDER_ID` (`GATEWAY_ORDER_ID`),
  KEY `FK1_PAYMENT_INVOICE_ID` (`INVOICE_ID`),
  KEY `FK2_PAYMENT_CLIENT_ID` (`CLIENT_ID`),
  CONSTRAINT `FK1_PAYMENT_INVOICE_ID` FOREIGN KEY (`INVOICE_ID`) REFERENCES `security_invoice` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_PAYMENT_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
