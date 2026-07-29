package com.fincity.security.model.billing;

/**
 * The fixed taxonomy of metered action keys.
 */
public final class BillingActionKeys {

    public static final String APP_RENT = "security.app.rent";
    public static final String SITE_RENT = "security.site.rent";
    public static final String USER = "security.user";
    public static final String STORAGE_ROWS = "core.storage.rows";
    public static final String DEALS = "entityprocessor.deals";
    public static final String FILES_GB = "files.gb";
    public static final String AI_LLM_TOKENS = "ai.llm.tokens";

    private BillingActionKeys() {
    }
}
