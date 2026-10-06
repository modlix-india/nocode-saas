package com.fincity.saas.message.service.call.provider.exotel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.message.configuration.WebClientConfig;
import com.fincity.saas.message.configuration.call.exotel.ExotelIntegrationsApiConfig;
import com.fincity.saas.message.configuration.interceptor.ReactiveAuthenticationScheme;
import com.fincity.saas.message.dao.call.CallProviderAppDAO;
import com.fincity.saas.message.dao.call.ProviderUserEndpointDAO;
import com.fincity.saas.message.dto.call.CallProviderApp;
import com.fincity.saas.message.dto.call.ProviderUserEndpoint;
import com.fincity.saas.message.model.base.BaseMessageRequest;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.call.ProvisionAgentRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.integrations.ExotelAppRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.integrations.ExotelAppSettingRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.integrations.ExotelOutboundCallRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.integrations.ExotelTokenRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.integrations.ExotelUserMappingRequest;
import com.fincity.saas.message.model.response.call.BrowserCallStatus;
import com.fincity.saas.message.model.response.call.BrowserCallToken;
import com.fincity.saas.message.model.response.call.CallAppStatus;
import com.fincity.saas.message.model.response.call.ProvisionedAgent;
import com.fincity.saas.message.model.response.call.provider.exotel.integrations.ExotelAppData;
import com.fincity.saas.message.model.response.call.provider.exotel.integrations.ExotelIntegrationsResponse;
import com.fincity.saas.message.model.response.call.provider.exotel.integrations.ExotelOutboundCallResult;
import com.fincity.saas.message.model.response.call.provider.exotel.integrations.ExotelUserMappingData;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.call.AgentEndpoints;
import com.fincity.saas.message.util.PhoneUtil;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.types.ULong;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Exotel's Integrations Core API: the app, its agents, and the tokens a browser registers with.
 *
 * <p>Separate from {@link ExotelCallService}: a different API on a different host with a different credential
 * pair. Auth style varies by endpoint; see {@link WebClientConfig#createExotelIntegrationsWebClient}.
 */
@Service
public class ExotelIntegrationsService {

    private static final ObjectMapper STATIC_MAPPER = new ObjectMapper();

    /** Operation names are user-facing: they fill the first placeholder of {@code exotel_request_failed}. */
    private static final String OPERATION_CUSTOMER_TOKEN = "customer token";

    private static final String OPERATION_APP_TOKEN = "app token";

    private static final String OPERATION_APP_LISTING = "app listing";

    private static final String OPERATION_APP_CREATION = "app creation";

    private static final String OPERATION_USER_MAPPING = "user mapping";

    private static final String OPERATION_OUTBOUND_CALL = "outbound call";

    private static final String CLAIM_EXPIRY = "exp";

    /** Stands in for the provider's own error text when it sent no body at all. */
    private static final String CAUSE_NO_RESPONSE = "no response";

    private static final int MAX_CAUSE_LENGTH = 200;

    private static final String PARAM_TO_NUMBER = "toNumber";

    private static final String ENDPOINT_WEBRTC_SIP = ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP;
    private static final String ENDPOINT_PSTN_PHONE = ProviderUserEndpoint.ENDPOINT_PSTN_PHONE;

    /**
     * {@code Data} on the token endpoint is a bare JWT string, not the object with {@code Token} and
     * {@code ExpiresIn} the vendor's examples show. Binding an object yields a null token, silently.
     */
    private static final ParameterizedTypeReference<ExotelIntegrationsResponse<String>> TOKEN_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ExotelIntegrationsResponse<ExotelOutboundCallResult>>
            OUTBOUND_CALL_TYPE = new ParameterizedTypeReference<>() {};

    /** Read as raw JSON: {@code Data} is an array on {@code GET /app} but a single object on {@code POST /app}. */
    private static final ParameterizedTypeReference<ExotelIntegrationsResponse<JsonNode>> APP_JSON_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<ExotelIntegrationsResponse<List<ExotelUserMappingData>>>
            USER_MAPPING_TYPE = new ParameterizedTypeReference<>() {};

    private final WebClientConfig webClientConfig;
    private final CallProviderAppDAO callProviderAppDAO;
    private final ProviderUserEndpointDAO providerUserEndpointDAO;
    private final IFeignSecurityService securityService;
    private final MessageResourceService msgService;
    private final ObjectMapper objectMapper;

    /**
     * Required before an app can be created. Both credential pairs: the telephony pair places click-to-call and
     * the integrations pair creates the app and mints softphone tokens.
     */
    private static final List<String> REQUIRED_DETAILS = List.of(
            ExotelIntegrationsApiConfig.ACCOUNT_SID,
            ExotelIntegrationsApiConfig.API_KEY,
            ExotelIntegrationsApiConfig.API_TOKEN,
            ExotelIntegrationsApiConfig.CUSTOMER_ID,
            ExotelIntegrationsApiConfig.CUSTOMER_SECRET,
            ExotelIntegrationsApiConfig.EXOTEL_DOMAIN,
            ExotelIntegrationsApiConfig.APP_NAME,
            ExotelIntegrationsApiConfig.CALLBACK_URL);

    public ExotelIntegrationsService(
            WebClientConfig webClientConfig,
            CallProviderAppDAO callProviderAppDAO,
            ProviderUserEndpointDAO providerUserEndpointDAO,
            IFeignSecurityService securityService,
            MessageResourceService msgService,
            ObjectMapper objectMapper) {
        this.webClientConfig = webClientConfig;
        this.callProviderAppDAO = callProviderAppDAO;
        this.providerUserEndpointDAO = providerUserEndpointDAO;
        this.securityService = securityService;
        this.msgService = msgService;
        this.objectMapper = objectMapper;
    }

    private String provider() {
        return ConnectionSubType.EXOTEL.getProvider();
    }

    /**
     * The first required detail this connection lacks, checked before app creation spends credentials on a
     * billable object. The callback URL is included because an app registered against an unserved URL reports
     * nothing.
     */
    private Optional<String> firstMissingDetail(Connection connection) {

        return REQUIRED_DETAILS.stream()
                .filter(key -> StringUtil.safeIsBlank(this.detail(connection, key)))
                .findFirst();
    }

    private String detail(Connection connection, String key) {
        Object value = connection.getConnectionDetails().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private <T> Mono<T> missingDetail(String key) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                MessageResourceService.MISSING_CONNECTION_DETAILS,
                this.provider(),
                key);
    }

    /** Unwraps the envelope. Integrations Core reports failure inside a 200 body, so this is the real check. */
    private <T> Mono<T> unwrap(ExotelIntegrationsResponse<T> response, String operation) {
        if (response == null || !response.isSuccess()) {
            String cause = response == null ? CAUSE_NO_RESPONSE : response.errorDetail();
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                    MessageResourceService.EXOTEL_REQUEST_FAILED,
                    operation,
                    cause);
        }
        return Mono.just(response.getData());
    }

    /**
     * An HTTP error from Exotel, as the same 502 {@link #unwrap} gives, with Exotel's own reason. Unmapped it
     * surfaces as a bare 500 and the reason is lost.
     */
    private <T> Mono<T> exotelFailure(WebClientResponseException e, String operation) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                MessageResourceService.EXOTEL_REQUEST_FAILED,
                operation,
                failureCause(e));
    }

    /** Exotel's {@code Error} from the body when it sent one, else the HTTP status; capped for the message. */
    static String failureCause(WebClientResponseException e) {
        String cause = null;
        try {
            JsonNode error = STATIC_MAPPER.readTree(e.getResponseBodyAsString()).path("Error");
            if (error.isTextual() && !error.asText().isBlank()) cause = error.asText();
        } catch (Exception ignored) {
            // Not JSON: an HTML error page from a proxy, say.
        }
        if (cause == null) cause = "HTTP " + e.getStatusCode().value();
        return cause.length() > MAX_CAUSE_LENGTH ? cause.substring(0, MAX_CAUSE_LENGTH) : cause;
    }

    // ---------------------------------------------------------------------------------------
    // Tokens
    // ---------------------------------------------------------------------------------------

    private Mono<String> requestToken(Connection connection, ExotelTokenRequest request, String operation) {
        return this.webClientConfig
                .createExotelIntegrationsWebClient(connection)
                .flatMap(client -> client.post()
                        .uri(ExotelIntegrationsApiConfig.tokenUrl())
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(TOKEN_TYPE))
                .onErrorResume(WebClientResponseException.class, e -> this.exotelFailure(e, operation))
                .flatMap(response -> this.unwrap(response, operation));
    }

    /** Authenticates the organisation, so it can manage its apps. */
    private Mono<String> customerToken(Connection connection) {
        String customerId = this.detail(connection, ExotelIntegrationsApiConfig.CUSTOMER_ID);
        String customerSecret = this.detail(connection, ExotelIntegrationsApiConfig.CUSTOMER_SECRET);

        if (customerId == null || customerId.isBlank())
            return this.missingDetail(ExotelIntegrationsApiConfig.CUSTOMER_ID);
        if (customerSecret == null || customerSecret.isBlank())
            return this.missingDetail(ExotelIntegrationsApiConfig.CUSTOMER_SECRET);

        return this.requestToken(
                connection, ExotelTokenRequest.ofCustomer(customerId, customerSecret), OPERATION_CUSTOMER_TOKEN);
    }

    /**
     * Authenticates the app itself. Not interchangeable with the customer token: user mappings and app settings
     * bind to the right Exotel account only with the app token.
     */
    private Mono<String> appToken(Connection connection, String appId, String appSecret) {
        return this.requestToken(connection, ExotelTokenRequest.ofApp(appId, appSecret), OPERATION_APP_TOKEN);
    }

    // ---------------------------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------------------------

    /**
     * Registers this tenant's integration app and initialises its settings. Idempotent.
     *
     * <p>{@code appSecret} is not required on the connection: Exotel mints it at creation and returns it exactly
     * once, so it is captured from that response and read from our own row afterwards.
     */
    public Mono<CallAppStatus> initializeApp(MessageAccess access, Connection connection) {

        Optional<String> missing = this.firstMissingDetail(connection);

        if (missing.isPresent()) return this.missingDetail(missing.get());

        String accountSid = this.detail(connection, ExotelIntegrationsApiConfig.ACCOUNT_SID);
        String appName = this.detail(connection, ExotelIntegrationsApiConfig.APP_NAME);

        return this.callProviderAppDAO
                .findByClient(access.getAppCode(), access.getClientCode(), this.provider())
                .flatMap(existing -> this.refreshAppSetting(access, connection, existing))
                .switchIfEmpty(Mono.defer(() -> this.registerNewApp(access, connection, appName, accountSid)))
                .map(this::appStatusOf)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelIntegrationsService.initializeApp"));
    }

    /**
     * Re-running against an existing app: refresh the callback URL, since the tenant's host can change, and keep
     * the stored secret.
     */
    private Mono<CallProviderApp> refreshAppSetting(
            MessageAccess access, Connection connection, CallProviderApp existing) {
        return this.appToken(connection, existing.getProviderAppId(), existing.getProviderAppSecret())
                .flatMap(token -> this.registerCallbackUrl(access, connection, token))
                .map(url -> url.isBlank() ? existing : existing.setCallbackUrl(url))
                .defaultIfEmpty(existing);
    }

    /**
     * Creates an app for a tenant that has none.
     *
     * <p>Our own row is the only authority on whether this tenant has an app: the provider's listing carries
     * nothing that identifies a tenant, and the returned {@code AppName} is not always the name sent. One Exotel
     * account holds many apps, one per tenant and per environment.
     *
     * <p>The secret is persisted before any other call because the provider returns it only once; without it the
     * app can never be authenticated as, or deleted. Tear down before wiping a database.
     *
     * <p>A lost row cannot be told apart from a tenant that never had one, so nothing is adopted by guessing:
     * {@code appId} and {@code appSecret} on the connection name an existing app to re-link, for recovery only.
     */
    private Mono<CallProviderApp> registerNewApp(
            MessageAccess access, Connection connection, String appName, String accountSid) {

        return FlatMapUtil.flatMapMono(
                        () -> this.customerToken(connection),
                        masterToken -> this.adoptFromConnection(connection)
                                .switchIfEmpty(
                                        Mono.defer(() -> this.createApp(connection, masterToken, appName, accountSid))),
                        // Persisted before the token call and callback registration, so neither can lose the secret.
                        (masterToken, app) -> this.persistApp(access, connection, app, appName, accountSid),
                        (masterToken, app, row) ->
                                this.appToken(connection, row.getProviderAppId(), row.getProviderAppSecret()),
                        (masterToken, app, row, token) -> this.registerCallbackUrl(access, connection, token),
                        (masterToken, app, row, token, callbackUrl) -> callbackUrl.isBlank()
                                ? Mono.just(row)
                                : this.callProviderAppDAO
                                        .updateCallbackUrl(row.getId(), callbackUrl)
                                        .thenReturn(row.setCallbackUrl(callbackUrl)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelIntegrationsService.registerNewApp"));
    }

    private Mono<CallProviderApp> persistApp(
            MessageAccess access, Connection connection, ExotelAppData app, String appName, String accountSid) {

        CallProviderApp row = new CallProviderApp()
                .setConnectionName(connection.getName())
                .setProvider(this.provider())
                .setProviderAppId(app.getAppId())
                .setProviderAppSecret(app.getAppSecret())
                .setProviderAppName(app.getAppName() == null ? appName : app.getAppName())
                .setAccountSid(accountSid)
                .setProviderMetadata(Map.of(
                        ExotelIntegrationsApiConfig.EXOTEL_DOMAIN,
                        app.getExotelDomain() == null ? "" : app.getExotelDomain(),
                        ExotelIntegrationsApiConfig.CUSTOMER_ID,
                        app.getCustomerId() == null ? "" : app.getCustomerId()));

        row.setAppCode(access.getAppCode()).setClientCode(access.getClientCode());

        return this.callProviderAppDAO.create(row).onErrorResume(e -> {
            // Two initialize calls raced and the other won; its row holds the same app, so read it back.
            return this.callProviderAppDAO
                    .findByClient(access.getAppCode(), access.getClientCode(), this.provider())
                    .switchIfEmpty(Mono.error(e));
        });
    }

    /**
     * Re-links an app the operator named on the connection; empty unless both {@code appId} and {@code appSecret}
     * are given.
     *
     * <p>Verified before it is written, unlike a created app: an unverified row would block its own retry on
     * {@code UK2_CALL_PROVIDER_APPS_TENANT} and could not be torn down. A rejected pair fails the initialize
     * rather than falling through to creation, so a typo cannot quietly create a new app.
     */
    private Mono<ExotelAppData> adoptFromConnection(Connection connection) {

        String appId = this.detail(connection, ExotelIntegrationsApiConfig.APP_ID);
        String appSecret = this.detail(connection, ExotelIntegrationsApiConfig.APP_SECRET);

        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) return Mono.empty();

        return this.appToken(connection, appId, appSecret)
                .thenReturn(new ExotelAppData().setAppId(appId).setAppSecret(appSecret));
    }

    private Mono<ExotelAppData> createApp(
            Connection connection, String masterToken, String appName, String accountSid) {

        return this.webClientConfig
                .createExotelIntegrationsWebClient(connection, masterToken, ReactiveAuthenticationScheme.NONE)
                .flatMap(client -> client.post()
                        .uri(ExotelIntegrationsApiConfig.appUrl())
                        .bodyValue(ExotelAppRequest.of(
                                appName,
                                accountSid,
                                this.detail(connection, ExotelIntegrationsApiConfig.API_KEY),
                                this.detail(connection, ExotelIntegrationsApiConfig.API_TOKEN),
                                this.exotelDomain(connection)))
                        .retrieve()
                        .bodyToMono(APP_JSON_TYPE))
                .flatMap(response -> this.unwrap(response, OPERATION_APP_CREATION))
                .flatMapMany(data -> this.toList(data, ExotelAppData.class, OPERATION_APP_LISTING))
                .next()
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                        MessageResourceService.EXOTEL_APP_NOT_RETURNED));
    }

    /**
     * Reads one agent's mapping back, filtered by {@code user_id}. Read as raw JSON because the path answers
     * either as a single object or as a paginated {@code {Users:[…]}} envelope.
     */
    private Mono<ExotelUserMappingData> findMapping(Connection connection, String appToken, String email) {

        return this.webClientConfig
                .createExotelIntegrationsWebClient(connection, appToken, ReactiveAuthenticationScheme.NONE)
                .flatMap(client -> client.get()
                        .uri(uri -> uri.path(ExotelIntegrationsApiConfig.userMappingUrl())
                                .queryParam(ExotelIntegrationsApiConfig.PARAM_USER_ID, email)
                                .build())
                        .retrieve()
                        .bodyToMono(APP_JSON_TYPE))
                .flatMap(response -> response.isSuccess() ? Mono.justOrEmpty(response.getData()) : Mono.empty())
                .flatMapMany(data -> this.toList(data, ExotelUserMappingData.class, OPERATION_USER_MAPPING))
                .filter(mapping -> email.equalsIgnoreCase(mapping.getAppUserId()))
                .next()
                // Exotel answers "user mapping not found" with a 404; that is the miss, not an error.
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
                // Any other failure is unknown, not a miss: treating it as one would create a duplicate mapping.
                .onErrorResume(e -> this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                        MessageResourceService.EXOTEL_MAPPING_READ_FAILED,
                        email));
    }

    /**
     * Reads a payload that may be one object, an array, or a {@code Users} envelope.
     *
     * <p>An unreadable payload raises rather than returning empty, since empty means "none exists" and the
     * caller would go on to create a duplicate.
     */
    private <T> Flux<T> toList(JsonNode data, Class<T> type, String operation) {

        if (data == null || data.isNull()) return Flux.empty();

        JsonNode node = data.hasNonNull(ExotelIntegrationsApiConfig.FIELD_USERS)
                ? data.get(ExotelIntegrationsApiConfig.FIELD_USERS)
                : data;

        try {
            if (node.isArray())
                return Flux.fromIterable(STATIC_MAPPER.convertValue(
                        node, STATIC_MAPPER.getTypeFactory().constructCollectionType(List.class, type)));

            return Flux.just(STATIC_MAPPER.convertValue(node, type));
        } catch (IllegalArgumentException e) {
            return this.msgService
                    .<T>throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                            MessageResourceService.EXOTEL_UNREADABLE_RESPONSE,
                            operation)
                    .flux();
        }
    }

    /**
     * Writes the app's settings. The browser SDK reads {@code GET /app_setting} on startup, and without them it
     * gets a 404 and softphone initialisation fails.
     */
    private Mono<String> registerCallbackUrl(MessageAccess access, Connection connection, String appToken) {

        String callbackUrl = this.callbackUrl(connection);

        return this.webClientConfig
                .createExotelIntegrationsWebClient(connection, appToken, ReactiveAuthenticationScheme.NONE)
                .flatMap(client -> this.putAppSetting(
                                client, ExotelIntegrationsApiConfig.CALLBACK_SETTING_KEY, callbackUrl)
                        .then(this.putAppSetting(
                                client, ExotelIntegrationsApiConfig.RECORD_SETTING_KEY, Boolean.TRUE.toString()))
                        .then(this.putAppSetting(
                                client, ExotelIntegrationsApiConfig.INCOMING_HANGUP_SETTING_KEY, callbackUrl))
                        .thenReturn(callbackUrl));
    }

    /**
     * Writes one app setting, and fails if it did not take. Exotel reports failure inside a 200, so the body goes
     * through {@link #unwrap} rather than being read as a string.
     */
    private Mono<Void> putAppSetting(WebClient client, String key, String value) {

        return client.post()
                .uri(ExotelIntegrationsApiConfig.appSettingUrl())
                .bodyValue(ExotelAppSettingRequest.of(key, value))
                .retrieve()
                .bodyToMono(APP_JSON_TYPE)
                .flatMap(response -> this.unwrap(response, key))
                .then();
    }

    /**
     * The status callback URL exactly as the connection states it, never derived: it must be reachable by the
     * provider and carry whatever path prefix the gateway needs to resolve the tenant.
     */
    private String callbackUrl(Connection connection) {
        return this.detail(connection, ExotelIntegrationsApiConfig.CALLBACK_URL).trim();
    }

    private String exotelDomain(Connection connection) {
        return this.detail(connection, ExotelIntegrationsApiConfig.EXOTEL_DOMAIN);
    }

    /**
     * Deletes this tenant's app at the provider, and forgets it locally.
     *
     * <p>The delete must be authenticated as the app itself: a customer token answers {@code "Deleted
     * Successfully"} while deleting nothing. That silent success is why this verifies afterwards.
     */
    public Mono<Boolean> teardownApp(MessageAccess access, Connection connection) {

        return FlatMapUtil.flatMapMono(
                        () -> this.requireApp(access, connection),
                        app -> this.appToken(connection, app.getProviderAppId(), app.getProviderAppSecret()),
                        (app, token) -> this.deleteApp(connection, token, app.getProviderAppId()),
                        (app, token, deleted) -> this.confirmAppGone(connection, app.getProviderAppId()),
                        (app, token, deleted, gone) -> {
                            if (Boolean.FALSE.equals(gone)) {
                                // Do not purge the local rows: they hold the only copy of the secret,
                                // and without it the app can never be deleted by anyone again.
                                return this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                                        MessageResourceService.EXOTEL_APP_NOT_DELETED,
                                        app.getProviderAppId());
                            }

                            return this.providerUserEndpointDAO
                                    .purgeByConnection(
                                            access.getAppCode(), access.getClientCode(), connection.getName())
                                    .then(this.callProviderAppDAO.purgeByClient(
                                            access.getAppCode(), access.getClientCode(), this.provider()))
                                    .thenReturn(Boolean.TRUE);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelIntegrationsService.teardownApp"));
    }

    private Mono<Boolean> deleteApp(Connection connection, String appToken, String appId) {
        return this.webClientConfig
                .createExotelIntegrationsWebClient(connection, appToken, ReactiveAuthenticationScheme.NONE)
                .flatMap(client -> client.method(HttpMethod.DELETE)
                        .uri(ExotelIntegrationsApiConfig.appUrl())
                        .bodyValue(List.of(appId))
                        .retrieve()
                        .bodyToMono(String.class))
                .thenReturn(Boolean.TRUE)
                .onErrorResume(e -> Mono.just(Boolean.FALSE));
    }

    /** Reads the listing back, because the delete response cannot be believed on its own. */
    private Mono<Boolean> confirmAppGone(Connection connection, String appId) {
        return this.customerToken(connection)
                .flatMap(masterToken -> this.webClientConfig
                        .createExotelIntegrationsWebClient(connection, masterToken, ReactiveAuthenticationScheme.NONE)
                        .flatMap(client -> client.get()
                                .uri(uri -> uri.path(ExotelIntegrationsApiConfig.appUrl())
                                        .queryParam(
                                                ExotelIntegrationsApiConfig.PARAM_ENTITY,
                                                ExotelIntegrationsApiConfig.ENTITY_CUSTOMER)
                                        .build())
                                .retrieve()
                                .bodyToMono(APP_JSON_TYPE)))
                .flatMap(response -> this.unwrap(response, OPERATION_APP_LISTING))
                .flatMapMany(data -> this.toList(data, ExotelAppData.class, OPERATION_APP_LISTING))
                .filter(app -> appId.equalsIgnoreCase(app.getAppId()))
                .hasElements()
                .map(stillListed -> !stillListed);
    }

    // ---------------------------------------------------------------------------------------
    // Agents
    // ---------------------------------------------------------------------------------------

    /**
     * Maps one agent onto a SIP device and records their endpoints: SIP at priority 1, mobile at priority 2.
     * Ringing is sequential, so that order is what Exotel dials.
     */
    public Mono<ProvisionedAgent> provisionAgent(
            MessageAccess access, Connection connection, ProvisionAgentRequest request) {

        if (request.getUserId() == null) return this.missingParam(BaseMessageRequest.Fields.userId);
        if (request.getVirtualNumber() == null || request.getVirtualNumber().isBlank())
            return this.missingParam(ProvisionAgentRequest.Fields.virtualNumber);
        // Refused, not defaulted: Exotel dials this after maxRingingDuration, so a wrong value rings whoever it
        // reaches on every call the agent does not answer in time.
        if (request.getAgentNumber() == null || request.getAgentNumber().isBlank())
            return this.missingParam(ProvisionAgentRequest.Fields.agentNumber);

        // Parsed with the same PhoneUtil that compares numbers later, so a typo fails here rather than at dial time.
        if (PhoneUtil.parse(request.getVirtualNumber()) == null)
            return this.invalidParam(ProvisionAgentRequest.Fields.virtualNumber, request.getVirtualNumber());

        if (PhoneUtil.parse(request.getAgentNumber()) == null)
            return this.invalidParam(ProvisionAgentRequest.Fields.agentNumber, request.getAgentNumber());

        return FlatMapUtil.flatMapMono(
                        () -> this.requireApp(access, connection),
                        app -> this.requireManagedUser(access, request.getUserId()),
                        (app, allowed) -> this.securityService.getUserInternal(
                                request.getUserId().toBigInteger(), null),
                        (app, allowed, user) -> {
                            // The override wins when given; the email requirement exists only to supply this.
                            if (request.getAppUserId() != null
                                    && !request.getAppUserId().isBlank())
                                return Mono.just(request.getAppUserId().trim());

                            if (user.getEmailId() == null || user.getEmailId().isBlank())
                                return this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        MessageResourceService.AGENT_HAS_NO_EMAIL,
                                        request.getUserId());
                            return Mono.just(user.getEmailId());
                        },
                        (app, allowed, user, email) -> this.requireIdentityUnclaimed(access, request, email),
                        (app, allowed, user, email, unclaimed) ->
                                this.appToken(connection, app.getProviderAppId(), app.getProviderAppSecret()),
                        (app, allowed, user, email, unclaimed, token) -> this.resolveMapping(
                                access, connection, token, app, request, user.getFirstName(), email),
                        (app, allowed, user, email, unclaimed, token, mapping) ->
                                this.writeEndpoints(access, connection, request, mapping))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelIntegrationsService.provisionAgent"));
    }

    /**
     * Confirms the target user belongs to a client this caller may administer (hierarchy, not equality). The
     * {@code ROLE_Owner} gate does not cover this: without it an owner could mint SIP credentials and session
     * tokens for another tenant's user.
     */
    private Mono<Boolean> requireManagedUser(MessageAccess access, ULong userId) {

        return this.securityService
                .isUserPartOfHierarchy(userId.toBigInteger(), access.getClientCode())
                .filter(Boolean.TRUE::equals)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        MessageResourceService.INVALID_USER_FOR_CLIENT,
                        userId,
                        access.getClientCode()));
    }

    /**
     * Refuses to hand one provider identity to two agents. The schema allows it, but they would share one SIP
     * endpoint: each rings for the other's calls, and the call log attributes calls to whichever the lookup returns.
     */
    private Mono<Boolean> requireIdentityUnclaimed(
            MessageAccess access, ProvisionAgentRequest request, String providerUserId) {

        return this.providerUserEndpointDAO
                .findByProviderUserId(access.getAppCode(), providerUserId, this.provider())
                .filter(existing -> !request.getUserId().equals(existing.getUserId()))
                .next()
                .flatMap(clash -> this.msgService.<Boolean>throwMessage(
                        msg -> new GenericException(HttpStatus.CONFLICT, msg),
                        MessageResourceService.EXOTEL_IDENTITY_CLAIMED,
                        providerUserId,
                        clash.getUserId()))
                .defaultIfEmpty(Boolean.TRUE);
    }

    /**
     * Brings the provider's mapping in line with the request: create if none, reuse if it matches, update if it
     * differs. Re-provisioning is how an operator changes an agent's numbers.
     *
     * <p>Never creates a second AppUser: {@code POST /usermapping} is an upsert keyed on {@code app_user_id}, and
     * the re-read proves it rather than the 200.
     */
    private Mono<ExotelUserMappingData> resolveMapping(
            MessageAccess access,
            Connection connection,
            String appToken,
            CallProviderApp app,
            ProvisionAgentRequest request,
            String displayName,
            String email) {

        return this.findMapping(connection, appToken, email)
                .flatMap(existing -> this.hasPendingChange(access, connection, request, existing)
                        .flatMap(changed -> Boolean.TRUE.equals(changed)
                                ? this.applyMappingChange(connection, appToken, app, request, displayName, email)
                                : Mono.just(existing)))
                .switchIfEmpty(
                        Mono.defer(() -> this.upsertMapping(connection, appToken, app, request, displayName, email)))
                .flatMap(mapping -> this.requireDialReady(mapping, email));
    }

    /** Sends a changed mapping and confirms it landed. The previous numbers are not recorded anywhere. */
    private Mono<ExotelUserMappingData> applyMappingChange(
            Connection connection,
            String appToken,
            CallProviderApp app,
            ProvisionAgentRequest request,
            String displayName,
            String email) {

        return this.upsertMapping(connection, appToken, app, request, displayName, email)
                .flatMap(updated -> this.requireChangeApplied(updated, request, email));
    }

    /**
     * Writes the mapping and reads back what the provider now holds; later guards need its copy, not our echo.
     * Fails rather than completing empty, which would make the caller's {@code switchIfEmpty} post a duplicate.
     */
    private Mono<ExotelUserMappingData> upsertMapping(
            Connection connection,
            String appToken,
            CallProviderApp app,
            ProvisionAgentRequest request,
            String displayName,
            String email) {

        return this.mapUser(connection, appToken, app, request, displayName, email)
                .then(this.findMapping(connection, appToken, email))
                .switchIfEmpty(Mono.defer(() -> this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                        MessageResourceService.EXOTEL_MAPPING_NOT_READABLE,
                        email)));
    }

    /**
     * Whether the request would change an already-provisioned agent. Exotel echoes {@code VirtualNumber} on the
     * mapping but never the agent number, so that one is compared against our own {@code PSTN_PHONE} row.
     */
    private Mono<Boolean> hasPendingChange(
            MessageAccess access,
            Connection connection,
            ProvisionAgentRequest request,
            ExotelUserMappingData existing) {

        return this.providerUserEndpointDAO
                .findEndpoint(access.getAppCode(), request.getUserId(), connection.getName(), ENDPOINT_PSTN_PHONE)
                .map(ProviderUserEndpoint::getEndpointValue)
                .defaultIfEmpty("")
                .map(currentAgentNumber ->
                        virtualNumberChanged(existing, request) || agentNumberChanged(currentAgentNumber, request));
    }

    /**
     * A virtual number the provider did not report counts as changed, so the upsert re-asserts it rather than
     * assuming it is right.
     */
    private static boolean virtualNumberChanged(ExotelUserMappingData existing, ProvisionAgentRequest request) {

        String current = existing.getVirtualNumber();

        if (StringUtil.safeIsBlank(current)) return true;

        return !PhoneUtil.isSameNumber(current, request.getVirtualNumber());
    }

    /** A blank current value (no endpoint row yet) reports no change; the write that follows creates the row. */
    private static boolean agentNumberChanged(String current, ProvisionAgentRequest request) {

        return !current.isBlank() && !PhoneUtil.isSameNumber(current, request.getAgentNumber());
    }

    /**
     * Confirms the provider applied the new virtual number; a 200 from the upsert is not evidence. The agent
     * number cannot be checked because the mapping response never carries it.
     */
    private Mono<ExotelUserMappingData> requireChangeApplied(
            ExotelUserMappingData updated, ProvisionAgentRequest request, String email) {

        if (PhoneUtil.isSameNumber(updated.getVirtualNumber(), request.getVirtualNumber())) return Mono.just(updated);

        // Unverifiable is not wrong: with no virtual number on the read, refusing would fail a likely-correct
        // provisioning.
        if (StringUtil.safeIsBlank(updated.getVirtualNumber())) return Mono.just(updated);

        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                MessageResourceService.EXOTEL_VIRTUAL_NUMBER_NOT_APPLIED,
                email,
                updated.getVirtualNumber(),
                request.getVirtualNumber());
    }

    /**
     * Refuses a mapping the agent could not dial from, per the provider's diagnosis of error 10715: an inactive
     * user has no SIP row to originate from, and no {@code SipId} means no browser identity to register.
     */
    private Mono<ExotelUserMappingData> requireDialReady(ExotelUserMappingData mapping, String email) {

        boolean active = !Boolean.FALSE.equals(mapping.getIsActive());
        boolean hasSip = mapping.getSipId() != null && !mapping.getSipId().isBlank();

        if (active && hasSip) return Mono.just(mapping);

        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                MessageResourceService.EXOTEL_AGENT_NOT_DIAL_READY,
                email,
                (active ? "having no SIP device" : "inactive"));
    }

    private Mono<ExotelUserMappingData> mapUser(
            Connection connection,
            String appToken,
            CallProviderApp app,
            ProvisionAgentRequest request,
            String displayName,
            String email) {

        ExotelUserMappingRequest mapping = new ExotelUserMappingRequest()
                .setAppUserId(email)
                .setAppUsername(displayName == null ? email : displayName)
                .setEmail(email)
                .setExotelAccountSid(app.getAccountSid())
                .setExotelUserName(displayName == null ? email : displayName)
                .setAgentNumber(request.getAgentNumber())
                .setVirtualNumber(request.getVirtualNumber());

        return this.webClientConfig
                .createExotelIntegrationsWebClient(connection, appToken, ReactiveAuthenticationScheme.NONE)
                .flatMap(client -> client.post()
                        .uri(ExotelIntegrationsApiConfig.userMappingUrl())
                        // An array even for one agent: Exotel takes a list and answers with a list.
                        .bodyValue(List.of(mapping))
                        .retrieve()
                        .bodyToMono(USER_MAPPING_TYPE))
                .flatMap(response -> this.unwrap(response, OPERATION_USER_MAPPING))
                .flatMap(list -> list.isEmpty()
                        ? this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                                MessageResourceService.EXOTEL_NO_SIP_IDENTITY)
                        : Mono.just(list.getFirst()));
    }

    /**
     * Writes the agent's endpoint rows under the tenant's client code from {@link MessageAccess}, not the agent's
     * own: deactivation, teardown and the admin listing all scope by the caller's tenant, and an owner may manage
     * agents in client hierarchies below their own.
     */
    private Mono<ProvisionedAgent> writeEndpoints(
            MessageAccess access, Connection connection, ProvisionAgentRequest request, ExotelUserMappingData mapping) {

        // SipId arrives already prefixed with "sip:", so it goes in as-is.
        ProviderUserEndpoint sip =
                this.endpoint(access, connection, request, ENDPOINT_WEBRTC_SIP, mapping.getSipId(), 1);

        sip.setProviderUserId(mapping.getAppUserId())
                .setProviderMetadata(Map.of(
                        ExotelIntegrationsApiConfig.META_SIP_SECRET,
                        mapping.getSipSecret() == null ? "" : mapping.getSipSecret(),
                        ExotelIntegrationsApiConfig.META_ROLE,
                        mapping.getRole() == null ? "" : mapping.getRole()));

        ProviderUserEndpoint pstn =
                this.endpoint(access, connection, request, ENDPOINT_PSTN_PHONE, request.getAgentNumber(), 2);

        pstn.setProviderUserId(mapping.getAppUserId());

        // Upserts: re-provisioning changes an agent's numbers, and an insert would hit
        // UK2_PROVIDER_USER_ENDPOINTS_AGENT on the second run.
        return this.providerUserEndpointDAO.upsert(sip).flatMap(writtenSip -> this.providerUserEndpointDAO
                .upsert(pstn)
                .map(writtenPstn -> merge(merge(new ProvisionedAgent(), writtenSip), writtenPstn)));
    }

    private ProviderUserEndpoint endpoint(
            MessageAccess access,
            Connection connection,
            ProvisionAgentRequest request,
            String type,
            String value,
            int priority) {

        ProviderUserEndpoint endpoint = new ProviderUserEndpoint()
                .setConnectionName(connection.getName())
                .setProvider(this.provider())
                .setEndpointType(type)
                .setEndpointValue(value)
                .setPriority(priority)
                .setVirtualNumber(request.getVirtualNumber());

        endpoint.setAppCode(access.getAppCode())
                .setClientCode(access.getClientCode())
                .setUserId(request.getUserId());

        return endpoint;
    }

    /**
     * Whether this tenant's app exists, from our own row only: an app deleted in the provider's console still
     * reads as present. Cheap because a settings screen loads it on every visit.
     */
    public Mono<CallAppStatus> callAppStatus(MessageAccess access) {

        return this.callProviderAppDAO
                .findByClient(access.getAppCode(), access.getClientCode(), this.provider())
                .map(this::appStatusOf)
                .defaultIfEmpty(CallAppStatus.notInitialized(this.provider()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelIntegrationsService.callAppStatus"));
    }

    /** One builder for setup and status; it also keeps the app secret and raw provider payload out of responses. */
    private CallAppStatus appStatusOf(CallProviderApp app) {
        return CallAppStatus.of(this.provider(), app.getProviderAppName(), app.getCallbackUrl());
    }

    /**
     * Every agent provisioned on a connection, one entry each, collapsed from the per-destination rows.
     *
     * <p>Grouped in memory rather than with {@code Flux.groupBy}: tens of rows, no prefetch-stall hazard, and a
     * {@code LinkedHashMap} keeps the DAO's ordering.
     */
    public Flux<ProvisionedAgent> getAgentEndpoints(MessageAccess access, Connection connection) {

        return this.providerUserEndpointDAO
                .findByConnection(access.getAppCode(), access.getClientCode(), connection.getName())
                .collectList()
                .flatMapIterable(ExotelIntegrationsService::consolidate)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelIntegrationsService.getAgentEndpoints"));
    }

    // Package-private so the collapsing is testable without a database.
    static List<ProvisionedAgent> consolidate(List<ProviderUserEndpoint> endpoints) {
        return AgentEndpoints.consolidate(endpoints);
    }

    /** Folds one destination row into its agent. Shared with every provider: see {@link AgentEndpoints}. */
    private static ProvisionedAgent merge(ProvisionedAgent agent, ProviderUserEndpoint endpoint) {
        return AgentEndpoints.fold(agent, endpoint);
    }

    /**
     * Retires an agent locally and at the provider. Clearing our rows stops new tokens; deleting the mapping makes
     * Exotel refuse the next registration. A token already issued survives until the session reconnects.
     */
    public Mono<Integer> deactivateAgent(MessageAccess access, Connection connection, ULong userId) {

        return FlatMapUtil.flatMapMono(
                        () -> this.requireApp(access, connection),
                        // An owner must not be able to deprovision another tenant's agent.
                        app -> this.requireManagedUser(access, userId),
                        (app, allowed) -> this.providerIdentity(access, connection, userId),
                        (app, allowed, providerUserId) ->
                                this.appToken(connection, app.getProviderAppId(), app.getProviderAppSecret()),
                        (app, allowed, providerUserId, token) ->
                                this.revokeAtProvider(connection, token, providerUserId),
                        // Keyed on the agent, not the caller's tenant: rows record whoever provisioned last,
                        // so a child-client owner would otherwise match nothing after revoking at the provider.
                        // Access was settled by requireManagedUser.
                        (app, allowed, providerUserId, token, revoked) -> this.providerUserEndpointDAO.deactivate(
                                access.getAppCode(), userId, connection.getName()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelIntegrationsService.deactivateAgent"));
    }

    /**
     * The provider identity to revoke, or blank when there is none. Blank rather than empty so the deactivation
     * still runs; blank ids are filtered first because {@code PROVIDER_USER_ID} is nullable and a null map throws.
     */
    private Mono<String> providerIdentity(MessageAccess access, Connection connection, ULong userId) {

        return this.providerUserEndpointDAO
                .findActiveEndpoints(access.getAppCode(), userId, connection.getName(), this.provider())
                .filter(endpoint -> !StringUtil.safeIsBlank(endpoint.getProviderUserId()))
                .next()
                .map(ProviderUserEndpoint::getProviderUserId)
                .defaultIfEmpty("");
    }

    /**
     * Removes the agent's mapping at the provider, and fails the operation if it cannot, so a refused revocation
     * does not leave a departed agent with a working softphone. Nothing to revoke counts as success.
     */
    private Mono<Boolean> revokeAtProvider(Connection connection, String appToken, String providerUserId) {

        if (providerUserId.isBlank()) return Mono.just(Boolean.TRUE);

        return this.deleteUserMapping(connection, appToken, providerUserId)
                .flatMap(deleted -> Boolean.TRUE.equals(deleted)
                        ? Mono.just(Boolean.TRUE)
                        : this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                                MessageResourceService.EXOTEL_REVOCATION_FAILED,
                                providerUserId));
    }

    private Mono<Boolean> deleteUserMapping(Connection connection, String appToken, String providerUserId) {
        return this.webClientConfig
                .createExotelIntegrationsWebClient(connection, appToken, ReactiveAuthenticationScheme.NONE)
                // DELETE with a body, on /users rather than /usermapping; the body is a bare array of app user ids.
                .flatMap(client -> client.method(HttpMethod.DELETE)
                        .uri(ExotelIntegrationsApiConfig.usersUrl())
                        .bodyValue(List.of(providerUserId))
                        .retrieve()
                        .bodyToMono(String.class))
                .thenReturn(Boolean.TRUE)
                // False, not an error: the caller fails the whole deactivation on it.
                .onErrorResume(e -> Mono.just(Boolean.FALSE));
    }

    // ---------------------------------------------------------------------------------------
    // Browser session
    // ---------------------------------------------------------------------------------------

    /**
     * The app token, with the agent's Exotel user id: what Exotel's browser SDK starts with. Exotel has no token
     * narrower than the app, so it goes only to a provisioned agent's own request, and is never cached or logged.
     */
    public Mono<BrowserCallToken> generateBrowserToken(MessageAccess access, Connection connection, ULong userId) {

        return FlatMapUtil.flatMapMono(
                        () -> this.requireApp(access, connection),
                        app -> this.requireEndpoint(access, connection, userId),
                        (app, endpoint) ->
                                this.appToken(connection, app.getProviderAppId(), app.getProviderAppSecret()),
                        // Never log the token: it authenticates as the whole app for about ninety days.
                        (app, endpoint, token) -> Mono.just(BrowserCallToken.of(
                                token, endpoint.getProviderUserId(), expiresInSeconds(token), this.provider())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelIntegrationsService.generateBrowserToken"));
    }

    /** Reads our own rows only, with no provider round trip, so it is cheap enough for every page load. */
    public Mono<BrowserCallStatus> browserCallStatus(MessageAccess access, Connection connection, ULong userId) {
        return this.browserCallStatus(access, connection, userId, false);
    }

    /**
     * Whether this agent can take calls in the browser. Unverified, it reads our own rows; verified, it asks the
     * provider, the only way to see a user it has deactivated or stripped of a SIP device. Such an agent still
     * registers a softphone and then fails every call with an opaque 500.
     */
    public Mono<BrowserCallStatus> browserCallStatus(
            MessageAccess access, Connection connection, ULong userId, boolean verifyWithProvider) {

        return this.providerUserEndpointDAO
                .findActiveEndpoints(access.getAppCode(), userId, connection.getName(), this.provider())
                .filter(endpoint -> ENDPOINT_WEBRTC_SIP.equals(endpoint.getEndpointType()))
                .next()
                .flatMap(endpoint -> {
                    BrowserCallStatus status = BrowserCallStatus.of(
                            this.provider(), endpoint.getProviderUserId(), endpoint.getVirtualNumber());

                    if (!verifyWithProvider) return Mono.just(status);

                    return this.requireApp(access, connection)
                            .flatMap(app ->
                                    this.appToken(connection, app.getProviderAppId(), app.getProviderAppSecret()))
                            .flatMap(token -> this.findMapping(connection, token, endpoint.getProviderUserId()))
                            .map(mapping -> status.setProvisioned(!Boolean.FALSE.equals(mapping.getIsActive())
                                            && mapping.getSipId() != null
                                            && !mapping.getSipId().isBlank())
                                    .checkedWithProvider())
                            // No mapping at the provider: our endpoint row outlived it.
                            // Deferred: an eager argument would mark the status before the check runs, and a
                            // provider error would then report the agent as checked and not provisioned.
                            .switchIfEmpty(Mono.fromSupplier(() -> status.setProvisioned(false)
                                    .checkedWithProvider()))
                            .onErrorResume(e -> Mono.just(status));
                })
                .defaultIfEmpty(BrowserCallStatus.notProvisioned(this.provider()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelIntegrationsService.browserCallStatus"));
    }

    /**
     * Asks the provider to ring this agent's browser and then the customer.
     *
     * <p>Authenticated with the app token, since Exotel has no agent-scoped one; the agent is named in the body.
     *
     * @param toNumber the customer's number, resolved from the deal by the service that owns it. Never taken from
     * a browser.
     */
    public Mono<ExotelOutboundCallResult> placeOutboundCall(
            MessageAccess access, Connection connection, ULong userId, String toNumber) {

        if (toNumber == null || toNumber.isBlank())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.MISSING_CALL_PARAMETERS,
                    this.provider(),
                    PARAM_TO_NUMBER);

        return FlatMapUtil.flatMapMono(
                        () -> this.requireApp(access, connection),
                        app -> this.requireEndpoint(access, connection, userId),
                        (app, endpoint) ->
                                this.appToken(connection, app.getProviderAppId(), app.getProviderAppSecret()),
                        (app, endpoint, appToken) -> this.webClientConfig
                                .createExotelIntegrationsWebClient(
                                        connection, appToken, ReactiveAuthenticationScheme.NONE)
                                .flatMap(client -> client.post()
                                        .uri(ExotelIntegrationsApiConfig.outboundCallUrl())
                                        .bodyValue(ExotelOutboundCallRequest.of(
                                                this.customerIdFor(app, connection),
                                                app.getProviderAppId(),
                                                toNumber,
                                                endpoint.getProviderUserId()))
                                        .retrieve()
                                        .bodyToMono(OUTBOUND_CALL_TYPE))
                                .onErrorResume(
                                        WebClientResponseException.class,
                                        e -> this.exotelFailure(e, OPERATION_OUTBOUND_CALL))
                                .flatMap(response -> this.unwrap(response, OPERATION_OUTBOUND_CALL)
                                        .flatMap(result -> this.toDialResult(response.getRequestId(), result))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelIntegrationsService.placeOutboundCall"));
    }

    /**
     * Refuses a dial response without a {@code CallSid}: the provider returns one synchronously, and a row no
     * callback can match is worse than a loud failure. The call may still have been placed, so the request id in
     * the message is what the provider's logs are searched by.
     */
    private Mono<ExotelOutboundCallResult> toDialResult(String requestId, ExotelOutboundCallResult result) {

        if (result == null || StringUtil.safeIsBlank(result.getCallSid()))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                    MessageResourceService.EXOTEL_NO_CALL_SID,
                    requestId);

        return Mono.just(result.setRequestId(requestId));
    }

    /**
     * The provider's customer id, from our row first so later edits to the connection do not change it; falls
     * back to the connection for older rows.
     */
    private String customerIdFor(CallProviderApp app, Connection connection) {

        Map<String, Object> metadata = app.getProviderMetadata();

        if (metadata != null) {
            Object stored = metadata.get(ExotelIntegrationsApiConfig.CUSTOMER_ID);
            if (stored != null && !stored.toString().isBlank()) return stored.toString();
        }

        return this.detail(connection, ExotelIntegrationsApiConfig.CUSTOMER_ID);
    }

    // ---------------------------------------------------------------------------------------

    private Mono<CallProviderApp> requireApp(MessageAccess access, Connection connection) {
        return this.callProviderAppDAO
                .findByClient(access.getAppCode(), access.getClientCode(), this.provider())
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        MessageResourceService.CALL_APP_NOT_INITIALIZED,
                        connection.getName()));
    }

    /** The agent's SIP endpoint, or a 403 so the UI can tell "not set up for calling" from "calling is broken". */
    private Mono<ProviderUserEndpoint> requireEndpoint(MessageAccess access, Connection connection, ULong userId) {
        return this.providerUserEndpointDAO
                .findActiveEndpoints(access.getAppCode(), userId, connection.getName(), this.provider())
                .filter(endpoint -> ENDPOINT_WEBRTC_SIP.equals(endpoint.getEndpointType()))
                .next()
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        MessageResourceService.AGENT_NOT_PROVISIONED,
                        userId,
                        connection.getName()));
    }

    /**
     * Seconds until this token expires, from its {@code exp} claim: the response has no {@code ExpiresIn} and the
     * claims no {@code iat}. Observed lifetime is about 90 days, not the 24 hours the vendor's examples imply.
     * Null means unknown, not expired.
     */
    static Long expiresInSeconds(String jwt) {
        if (jwt == null) return null;

        String[] parts = jwt.split("\\.");
        if (parts.length < 2) return null;

        try {
            byte[] payload = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            JsonNode claims = STATIC_MAPPER.readTree(payload);
            if (!claims.hasNonNull(CLAIM_EXPIRY)) return null;

            long remaining = claims.get(CLAIM_EXPIRY).asLong() - Instant.now().getEpochSecond();
            return remaining > 0 ? remaining : 0L;
        } catch (Exception e) {
            return null;
        }
    }

    private static String padBase64(String value) {
        int remainder = value.length() % 4;
        return remainder == 0 ? value : value + "====".substring(remainder);
    }

    private <T> Mono<T> invalidParam(String param, String value) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                MessageResourceService.EXOTEL_INVALID_PHONE_NUMBER,
                param,
                value);
    }

    private <T> Mono<T> missingParam(String param) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                MessageResourceService.MISSING_CALL_PARAMETERS,
                this.provider(),
                param);
    }
}
