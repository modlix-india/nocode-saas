package com.fincity.saas.message.configuration.call.telecmi;

/**
 * TeleCMI's CHub REST API: v3 exists only for users, so click-to-call, recordings and balance stay on v2.
 * Every call authenticates with the app id and secret in the body. User errors arrive as
 * {@code {"code": 400, "status": "error"}} inside an HTTP 200, while an update that changes nothing is a real 404.
 */
public final class TelecmiApiConfig {

    public static final String DEFAULT_REST_BASE = "https://rest.telecmi.com";

    /** The India SBC, which every agent registered against in testing. */
    public static final String DEFAULT_SBC_URI = "sbcind.telecmi.com";

    /** Connection details keys; a key spelled two ways silently reads as unconfigured. */
    public static final String APP_ID = "appId";

    public static final String SECRET = "secret";

    /** The tenant's virtual number, shown to customers as the caller id. */
    public static final String CALLER_ID = "callerId";

    /** Optional. The SBC the softphone registers with; {@link #DEFAULT_SBC_URI} when absent. */
    public static final String SBC_URI = "sbcUri";

    /** Optional. Overrides {@link #DEFAULT_REST_BASE}. */
    public static final String REST_BASE_URL = "restBaseUrl";

    /**
     * Optional. The webhook base the owner registers, without suffix or token ({@code <callbackUrl>/events?t=...}).
     * Taken verbatim: it must carry the gateway prefix that resolves the tenant, which cannot be derived here.
     */
    public static final String CALLBACK_URL = "callbackUrl";

    /** Optional. The inbound HTTP flow's URL (entity-processor's {@code /open/call/telecmi}); never derived. */
    public static final String HTTP_FLOW_URL = "httpFlowUrl";

    /** Optional. Inbound ring time in seconds; 30 when absent. */
    public static final String FLOW_TIMEOUT = "flowTimeout";

    public static final String META_BALANCE = "balance";

    public static final String META_EXPIRE = "expire";

    public static final String META_WEBHOOK_TOKEN_HASH = "webhookTokenHash";

    public static final String META_HTTP_FLOW_URL = "httpFlowUrl";

    /** The softphone's login password, on the agent's endpoint metadata. */
    public static final String META_PASSWORD = "password";

    /** TeleCMI's extension range for users made through v3. */
    public static final int MIN_EXTENSION = 1000;

    public static final int MAX_EXTENSION = 9999;

    /** TeleCMI's minimum user password length. */
    public static final int MIN_PASSWORD_LENGTH = 8;

    public static final int CODE_SUCCESS = 200;

    public static final int CODE_NOT_FOUND = 404;

    /** TeleCMI's refusal messages, exactly as sent. */
    public static final String MSG_EXTENSION_EXISTS = "Extension Already Exists";

    public static final String MSG_EMAIL_EXISTS = "Email Already Exists";

    /**
     * Our call code's key in {@code extra_params}, which TeleCMI echoes on every webhook, so the row is found
     * even when the response carrying {@code request_id} never reached us.
     */
    public static final String EXTRA_CALL_CODE = "callCode";

    /** The inbound HTTP flow's form fields, as TeleCMI posted them live. */
    public static final String FLOW_FROM = "from";

    public static final String FLOW_TO = "to";

    public static final String FLOW_CMIUUID = "cmiuuid";

    public static final String FLOW_APP_ID = "appid";

    private TelecmiApiConfig() {}

    /** Credential check; 200 proves the app id and secret. */
    public static String balanceUrl() {
        return "/v2/balance";
    }

    public static String userAddUrl() {
        return "/v3/user/add";
    }

    public static String userGetUrl() {
        return "/v3/user/get";
    }

    public static String userAllUrl() {
        return "/v3/user/all";
    }

    public static String userUpdateUrl() {
        return "/v3/user/update";
    }

    /** Deletes the user; there is no deactivate. */
    public static String userRemoveUrl() {
        return "/v3/user/remove";
    }

    /** Rings the agent's softphone or, through Follow Me, their mobile, then the customer. Takes no app id. */
    public static String clickToCallUrl() {
        return "/v2/webrtc/click2call";
    }

    /** A recording by CDR file name. The secret is in the query string: this URL must never reach a browser or log. */
    public static String playUrl() {
        return "/v2/play";
    }
}
