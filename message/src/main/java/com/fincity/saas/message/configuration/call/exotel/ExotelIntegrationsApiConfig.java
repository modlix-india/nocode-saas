package com.fincity.saas.message.configuration.call.exotel;

/**
 * Exotel's Integrations Core API, a different service, host and credential pair from the telephony API in
 * {@link ExotelApiConfig}. Only this one can create the integration app, map agents to SIP identities, and mint
 * softphone tokens.
 *
 * <p>Shapes come from the vendor's client SDK, not a written contract; trust them only where a live call has
 * exercised them.
 */
public final class ExotelIntegrationsApiConfig {

    /** Mumbai. India-only today; a non-India tenant would need a different host. */
    public static final String DEFAULT_INTEGRATIONS_BASE = "https://integrationscore.mum1.exotel.com";

    /** Connection detail that overrides {@link #DEFAULT_INTEGRATIONS_BASE}. */
    public static final String INTEGRATIONS_BASE_URL = "integrationsBaseUrl";

    /** Connection detail naming the account's region, as Exotel spells it. */
    public static final String EXOTEL_DOMAIN = "exotelDomain";

    /** Connection details every Exotel calling connection must carry; both credential pairs are needed. */
    public static final String ACCOUNT_SID = "accountSid";

    public static final String API_KEY = "apiKey";

    public static final String API_TOKEN = "apiToken";

    public static final String CUSTOMER_ID = "customerId";

    public static final String CUSTOMER_SECRET = "customerSecret";

    /**
     * The app name at the provider, supplied on the connection so operators can find it in the Exotel dashboard.
     * Not used to find an existing app: the provider has returned apps under a name other than the one sent.
     */
    public static final String APP_NAME = "appName";

    /**
     * The status callback URL, taken from the connection exactly as written and never derived: it must be
     * reachable by the provider and carry whatever path the gateway needs to resolve the tenant.
     */
    public static final String CALLBACK_URL = "callbackUrl";

    /** Optional recovery details for adopting an app whose once-issued secret was lost. */
    public static final String APP_ID = "appId";

    public static final String APP_SECRET = "appSecret";

    /** Query parameters and payload keys in the vendor's spelling. */
    public static final String PARAM_ENTITY = "entity";

    public static final String ENTITY_CUSTOMER = "customer";

    public static final String PARAM_USER_ID = "user_id";

    /** The envelope key the paginated user listing nests its rows under. */
    public static final String FIELD_USERS = "Users";

    public static final String META_SIP_SECRET = "sipSecret";

    public static final String META_ROLE = "role";

    /** The app setting key under which the status callback URL is registered. */
    public static final String CALLBACK_SETTING_KEY = "callback";

    /** Turns provider-side recording on for the app. */
    public static final String RECORD_SETTING_KEY = "record";

    /** Where the provider reports an inbound call the agent hung up on. */
    public static final String INCOMING_HANGUP_SETTING_KEY = "incomingCallHangup";

    private ExotelIntegrationsApiConfig() {}

    public static String tokenUrl() {
        return "/v2/integrations/token";
    }

    public static String appUrl() {
        return "/v2/integrations/app";
    }

    public static String appSettingUrl() {
        return "/v2/integrations/app_setting";
    }

    public static String userMappingUrl() {
        return "/v2/integrations/usermapping";
    }

    /** Deleting agents: a DELETE with a bare array of app user ids, on {@code /users}, not {@code /usermapping}. */
    public static String usersUrl() {
        return "/v2/integrations/users";
    }

    /**
     * Places an outbound call for a browser-registered agent. Called by this service, not the browser, so the call
     * row is written with its ticket before Exotel dials.
     */
    public static String outboundCallUrl() {
        return "/v2/integrations/call/outbound_call";
    }
}
