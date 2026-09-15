package com.fincity.saas.message.configuration.call.exotel;

/**
 * Exotel's Integrations Core API, which is a different service from the telephony API in {@link
 * ExotelApiConfig}.
 *
 * <p>Two APIs, two credential pairs, two hosts. {@code apiKey}/{@code apiToken} authenticate the
 * telephony API that places click-to-call; {@code customerId}/{@code customerSecret} authenticate
 * this one, which is the only thing that can create the integration app, map agents to SIP
 * identities, and mint the tokens a browser softphone registers with.
 *
 * <p>Read from the vendor's published client SDK rather than from a written contract, so treat the
 * request and response shapes as confirmed only where a live call has exercised them.
 */
public final class ExotelIntegrationsApiConfig {

    /** Mumbai. India-only today; a non-India tenant would need a different host. */
    public static final String DEFAULT_INTEGRATIONS_BASE = "https://integrationscore.mum1.exotel.com";

    /** Connection detail that overrides {@link #DEFAULT_INTEGRATIONS_BASE}. */
    public static final String INTEGRATIONS_BASE_URL = "integrationsBaseUrl";

    /** Connection detail naming the account's region, as Exotel spells it. */
    public static final String EXOTEL_DOMAIN = "exotelDomain";

    /**
     * Connection details every Exotel calling connection must carry.
     *
     * <p>Named here rather than written at each read site, following {@link #INTEGRATIONS_BASE_URL}
     * and {@link #EXOTEL_DOMAIN} above: a key spelled two ways is a connection that silently reads
     * as unconfigured, and the compiler cannot catch a string.
     *
     * <p>{@code ACCOUNT_SID}, {@code API_KEY} and {@code API_TOKEN} authenticate the telephony API;
     * {@code CUSTOMER_ID} and {@code CUSTOMER_SECRET} authenticate this one. Both pairs are needed
     * because a tenant using the softphone also keeps click-to-call.
     */
    public static final String ACCOUNT_SID = "accountSid";

    public static final String API_KEY = "apiKey";

    public static final String API_TOKEN = "apiToken";

    public static final String CUSTOMER_ID = "customerId";

    public static final String CUSTOMER_SECRET = "customerSecret";

    /**
     * The name the app is registered under at the provider, taken from the connection.
     *
     * <p>Supplied rather than composed. It was briefly built from the app code, client code and
     * deployment environment, which reads as tidy and is the wrong owner: the name is what an
     * operator finds the app by in the Exotel dashboard, so it belongs wherever they can set it and
     * see it. A composed name also changes when the thing composing it changes, and the connection
     * is the record of what was actually sent.
     *
     * <p>Not used to find an existing app. The provider has returned apps under a name other than
     * the one it was sent, so the lookup matches on {@code accountSid} instead — which is why
     * changing this cannot orphan an app that already exists.
     */
    public static final String APP_NAME = "appName";

    /**
     * The status callback URL, taken from the connection exactly as written.
     *
     * <p>Required, and never derived. It was previously resolved from the tenant's app URL, which is
     * a different thing with a different lifecycle: that address answers a browser, this one has to
     * be reachable by the provider and has to carry whatever path the gateway needs to resolve the
     * tenant. Deriving it meant a correct-looking URL that no callback ever arrived on.
     */
    public static final String CALLBACK_URL = "callbackUrl";

    /**
     * Recovery details for an app this service did not create.
     *
     * <p>Optional, and the only way to adopt an app whose secret was issued once and lost. Absent
     * them, an existing app on the account is refused rather than guessed at.
     */
    public static final String APP_ID = "appId";

    public static final String APP_SECRET = "appSecret";

    /**
     * Query parameters and payload keys the provider's own API defines.
     *
     * <p>Named for the same reason the connection keys above are: these are the vendor's spellings,
     * not ours, and a typo in one produces a request the provider answers oddly rather than refuses.
     */
    public static final String PARAM_ENTITY = "entity";

    public static final String ENTITY_CUSTOMER = "customer";

    public static final String PARAM_USER_ID = "user_id";

    /** The envelope key the paginated user listing nests its rows under. */
    public static final String FIELD_USERS = "Users";

    /** Provider metadata keys, stored on the rows this service writes. */
    public static final String META_SIP_SECRET = "sipSecret";

    public static final String META_ROLE = "role";

    /** Matches the mum1 / in1 hosts this integration is scoped to. */
    public static final String DEFAULT_EXOTEL_DOMAIN = "Mumbai";

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

    /**
     * Deleting agents. Note it is <b>not</b> {@code /usermapping}.
     *
     * <p>Mappings are created on {@code /usermapping} but removed here, by posting a DELETE whose
     * body is a plain array of app user ids. An easy thing to get wrong by symmetry.
     */
    public static String usersUrl() {
        return "/v2/integrations/users";
    }

    /**
     * Places an outbound call on behalf of a browser-registered agent.
     *
     * <p>Called by this service, not by the browser. The SDK would call it directly with the agent's
     * own token, but doing so leaves the backend blind: no row exists when the call starts, so the
     * deal cannot be attached and the deal check is lost. Placing it here means the call row is
     * written with its ticket before Exotel is even asked to dial.
     */
    public static String outboundCallUrl() {
        return "/v2/integrations/call/outbound_call";
    }
}
