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
import com.fincity.saas.message.util.PhoneUtil;
import com.fincity.saas.message.util.SetterUtil;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
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
 * <p>A separate bean from {@link ExotelCallService} rather than more methods on it. That service
 * speaks the telephony API with one credential pair; this one speaks a different API on a different
 * host with a different pair, and folding them together would put two unrelated contracts behind
 * one class.
 *
 * <p>Three auth styles reach the same host and none of them is the obvious one everywhere. See
 * {@link WebClientConfig#createExotelIntegrationsWebClient}.
 */
@Service
public class ExotelIntegrationsService {

    /**
     * Only ever reads JWT claims, so a plain mapper is enough and no bean is needed.
     */
    private static final ObjectMapper STATIC_MAPPER = new ObjectMapper();

    /**
     * What each provider call is named in the failure it raises.
     *
     * <p>Named once each because they are user-facing: they fill the first placeholder of
     * {@code exotel_request_failed}, so "Exotel app creation failed: …" is what an operator reads.
     * Spelled at the call site they drift, and the same failure gets two different names.
     */
    private static final String OPERATION_CUSTOMER_TOKEN = "customer token";

    private static final String OPERATION_APP_TOKEN = "app token";

    private static final String OPERATION_APP_LISTING = "app listing";

    private static final String OPERATION_APP_CREATION = "app creation";

    private static final String OPERATION_USER_MAPPING = "user mapping";

    private static final String OPERATION_AGENT_TOKEN = "agent session token";

    private static final String OPERATION_DIAL_TOKEN = "agent session token for dialling";

    private static final String OPERATION_OUTBOUND_CALL = "outbound call";

    /** The expiry claim on the provider's agent token. */
    private static final String CLAIM_EXPIRY = "exp";

    /** Stands in for the provider's own error text when it sent no body at all. */
    private static final String CAUSE_NO_RESPONSE = "no response";

    /** The dial parameter name, for the refusal an empty destination raises. */
    private static final String PARAM_TO_NUMBER = "toNumber";

    private static final String ENDPOINT_WEBRTC_SIP = "WEBRTC_SIP";
    private static final String ENDPOINT_PSTN_PHONE = "PSTN_PHONE";

    /**
     * {@code Data} on the token endpoint is a <b>bare JWT string</b>, not an object.
     *
     * <p>There is no {@code Token} field and no {@code ExpiresIn} field, whatever the vendor's
     * examples show. Binding an object here yields a null token on every call, silently.
     */
    private static final ParameterizedTypeReference<ExotelIntegrationsResponse<String>> TOKEN_TYPE =
            new ParameterizedTypeReference<>() {};
    /**
     * App responses are read as raw JSON on purpose.
     *
     * <p>{@code Data} is an array on {@code GET /app} but a single object on {@code POST /app}, and
     * binding one shape would fail on the other with a {@code MismatchedInputException}.
     * Normalising here costs a few lines and tolerates either, including whichever Exotel settles
     * on later.
     */
    /** The dial response, bound rather than picked apart: its fields carry the provider's names. */
    private static final ParameterizedTypeReference<ExotelIntegrationsResponse<ExotelOutboundCallResult>>
            OUTBOUND_CALL_TYPE = new ParameterizedTypeReference<>() {};

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
     * Everything a calling connection must carry before an app can be created.
     *
     * <p>Both credential pairs are required, not one: the telephony pair places click-to-call and
     * the integrations pair creates the app and mints softphone tokens, and a tenant using the
     * softphone keeps both flows.
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
     * The first required detail this connection does not carry.
     *
     * <p>Checked before anything reaches the provider, and all of them rather than one at a time:
     * app creation spends the tenant's credentials and creates a billable object, so a connection
     * that is half filled in should fail at the request rather than after an app exists with no way
     * to reach it. The callback URL is in this set for that reason — an app registered against a
     * URL nobody serves looks provisioned and reports nothing.
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

    /**
     * Unwraps the envelope, turning a provider-level failure into a real error.
     *
     * <p>Integrations Core reports failure inside a 200 body, so without this a refusal would read
     * as an empty success and surface much later as a null field.
     */
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
     * Authenticates the app itself.
     *
     * <p>Not interchangeable with the customer token: user mappings and app settings bind to the
     * right Exotel account only when the app token is used, and can otherwise land on a different
     * tenant altogether.
     */
    private Mono<String> appToken(Connection connection, String appId, String appSecret) {
        return this.requestToken(connection, ExotelTokenRequest.ofApp(appId, appSecret), OPERATION_APP_TOKEN);
    }

    // ---------------------------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------------------------

    /**
     * Registers this tenant's integration app, and initialises its settings record.
     *
     * <p>Idempotent, and self-contained: the admin supplies only what their Exotel dashboard shows
     * them — {@code accountSid}, {@code customerId}, {@code customerSecret}. The app and its secret
     * are created here.
     *
     * <p>{@code appSecret} is deliberately <b>not</b> required on the connection. Exotel mints it
     * when the app is created and returns it exactly once; demanding it upfront would be a
     * chicken-and-egg trap, since the client has no way to know it before the app exists. It is
     * captured from the creation response, stored, and read from our own row forever after.
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
     * Re-running against an app we already have: refresh the callback URL, keep the stored secret.
     *
     * <p>Worth doing on every run rather than only at creation, because the tenant's host can
     * change and a stale callback URL is invisible until a call goes unlogged.
     */
    private Mono<CallProviderApp> refreshAppSetting(
            MessageAccess access, Connection connection, CallProviderApp existing) {
        return this.appToken(connection, existing.getProviderAppId(), existing.getProviderAppSecret())
                .flatMap(token -> this.registerCallbackUrl(access, connection, token))
                .map(url -> url.isBlank() ? existing : existing.setCallbackUrl(url))
                .defaultIfEmpty(existing);
    }

    /**
     * Brings a brand-new app into existence, or refuses to.
     *
     * <p>Three rules, and each exists because breaking it costs a customer something irreversible.
     *
     * <p><b>Never create blindly.</b> The provider keeps every app ever made and happily accepts a
     * second one with the same name. If our row is the only guard and the row is lost — a restore,
     * a purge, a renamed client — every run adds another app to the customer's account. That is how
     * five orphans appeared on this account before any of this was written.
     *
     * <p><b>Persist the secret before doing anything else.</b> The provider returns it exactly
     * once, at creation. Every network call placed between creating the app and writing that secret
     * down is another chance to end up with an app nobody can ever authenticate as, and therefore
     * nobody can ever delete.
     *
     * <p><b>Refuse rather than guess.</b> If an app already exists under this name but we hold no
     * secret for it, there is no safe move: creating a duplicate is unbounded growth, and adopting
     * it is impossible without credentials. Say so, and name the two ways out.
     */
    private Mono<CallProviderApp> registerNewApp(
            MessageAccess access, Connection connection, String appName, String accountSid) {

        return FlatMapUtil.flatMapMono(
                        () -> this.customerToken(connection),
                        masterToken -> this.findAppAtProvider(connection, masterToken, accountSid)
                                .flatMap(existing -> this.adoptOrRefuse(connection, existing))
                                .switchIfEmpty(
                                        Mono.defer(() -> this.createApp(connection, masterToken, appName, accountSid))),
                        // Straight to the database, before the token call and the callback
                        // registration.
                        // Neither of those can lose the secret if it is already written down.
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

    /**
     * Writes the app down the moment we have its secret, before anything else can fail.
     */
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
            // Two initialize calls raced and the other won. Its row holds the same app, so
            // read it
            // back rather than failing: the loser's app is already recorded by the winner.
            return this.callProviderAppDAO
                    .findByClient(access.getAppCode(), access.getClientCode(), this.provider())
                    .switchIfEmpty(Mono.error(e));
        });
    }

    /**
     * The app already registered under this name on this account, if there is one.
     *
     * <p>Refuses on more than one match rather than picking. Duplicate names are possible at the
     * provider, and choosing silently would bind to whichever the listing returned first — whose
     * secret we would almost certainly not hold, failing later and a long way from the cause.
     */
    private Mono<ExotelAppData> findAppAtProvider(Connection connection, String masterToken, String accountSid) {

        return this.webClientConfig
                .createExotelIntegrationsWebClient(connection, masterToken, ReactiveAuthenticationScheme.NONE)
                .flatMap(client -> client.get()
                        .uri(uri -> uri.path(ExotelIntegrationsApiConfig.appUrl())
                                .queryParam(
                                        ExotelIntegrationsApiConfig.PARAM_ENTITY,
                                        ExotelIntegrationsApiConfig.ENTITY_CUSTOMER)
                                .build())
                        .retrieve()
                        .bodyToMono(APP_JSON_TYPE))
                .flatMap(response -> this.unwrap(response, OPERATION_APP_LISTING))
                .flatMapMany(data -> this.toList(data, ExotelAppData.class, OPERATION_APP_LISTING))
                // Matched on the account, never on the name. The name sent is not the
                // name that comes back — Exotel has returned an app under a name other
                // than the one it was given — so a name-equality filter never matches
                // the app it just created. That silently defeats the whole guard: the
                // next run finds nothing, creates a duplicate, and the account grows
                // another app whose secret nobody holds. The account is ours to know;
                // the name is the provider's to change, which is also why the name is
                // an operator's label on the connection rather than something derived.
                .filter(app -> accountSid.equalsIgnoreCase(app.getExotelAccountSid())
                        && !Boolean.FALSE.equals(app.getIsActive()))
                .collectList()
                .flatMap(matches -> {
                    if (matches.isEmpty()) return Mono.empty();
                    if (matches.size() == 1) return Mono.just(matches.getFirst());

                    // More than one app already lives on this account and our row
                    // is gone, so nothing here identifies which one was ours.
                    // The operator has to say, and they need the secret anyway.
                    String recoveryAppId = this.detail(connection, ExotelIntegrationsApiConfig.APP_ID);

                    if (recoveryAppId != null && !recoveryAppId.isBlank())
                        return Flux.fromIterable(matches)
                                .filter(app -> recoveryAppId.equalsIgnoreCase(app.getAppId()))
                                .next()
                                .switchIfEmpty(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.CONFLICT, msg),
                                        MessageResourceService.EXOTEL_RECOVERY_APP_NOT_FOUND,
                                        recoveryAppId,
                                        accountSid));

                    return this.msgService.throwMessage(
                            msg -> new GenericException(HttpStatus.CONFLICT, msg),
                            MessageResourceService.EXOTEL_APPS_UNCLAIMED,
                            accountSid,
                            matches.size());
                });
    }

    /**
     * Re-links an app that exists at the provider but not here, when that is possible at all.
     *
     * <p>Only reachable when our row is missing and the app is not: a database loss, or an app made
     * by hand. The provider never re-reveals a secret, so the operator has to supply the one they
     * were given. {@code appSecret} on the connection exists purely for this recovery — it is not
     * part of normal setup, is never written by this service, and should not be left there.
     */
    private Mono<ExotelAppData> adoptOrRefuse(Connection connection, ExotelAppData existing) {

        String recoverySecret = this.detail(connection, ExotelIntegrationsApiConfig.APP_SECRET);

        if (recoverySecret != null && !recoverySecret.isBlank())
            return Mono.just(existing.setAppSecret(recoverySecret));

        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.CONFLICT, msg),
                MessageResourceService.EXOTEL_APP_SECRET_UNKNOWN,
                existing.getAppName(),
                existing.getAppId());
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

    /** Accepts {@code Data} as either a single app or an array of them. */
    /**
     * Reads one agent's mapping back from the provider.
     *
     * <p>Filtered by {@code user_id}, which returns a single object where the unfiltered listing
     * returns a paginated {@code {Users:[…]}} envelope. Bound to raw JSON and normalised for the
     * same reason the app endpoints are: the same path answers in more than one shape, and binding
     * to one of them fails on the other.
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
                // 404 IS the miss. Exotel answers "user mapping not found" with a
                // 404 rather than an empty success, and Spring turns that into an
                // error — so without this, provisioning a brand-new agent fails on
                // the very read that exists to check whether they already have a
                // mapping, and no first agent can ever be created.
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
                // Anything else is not a miss, and must not be treated as one: we do
                // not know whether a mapping exists, and creating a duplicate on a
                // failed read is the exact outcome this method exists to prevent.
                .onErrorResume(e -> this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                        MessageResourceService.EXOTEL_MAPPING_READ_FAILED,
                        email));
    }

    /**
     * Reads a provider payload that may arrive as one object, an array, or a paginated envelope.
     *
     * <p>One helper for every listing, because the app listing and the user listing differed only
     * in the type they bound to. The envelope is unwrapped only when its key is present, so the same
     * method serves both shapes without the caller knowing which it got.
     *
     * <p>An unreadable payload raises rather than coming back empty. Empty is a real answer here —
     * it means "no such app" or "no such mapping" — and a caller that cannot tell the two apart goes
     * on to create a duplicate, which is the outcome the provider's own support asked us to stop
     * producing.
     */
    private <T> Flux<T> toList(JsonNode data, Class<T> type, String operation) {

        if (data == null || data.isNull()) return Flux.empty();

        JsonNode node = data.hasNonNull(ExotelIntegrationsApiConfig.FIELD_USERS)
                ? data.get(ExotelIntegrationsApiConfig.FIELD_USERS)
                : data;

        try {
            if (node.isArray())
                return Flux.fromIterable(this.objectMapper.convertValue(
                        node, this.objectMapper.getTypeFactory().constructCollectionType(List.class, type)));

            return Flux.just(this.objectMapper.convertValue(node, type));
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
     * Creates the app's settings record.
     *
     * <p>An initialisation rather than a configuration — the body carries only the app and the
     * account. It matters because the browser SDK reads {@code GET /app_setting} on startup, and
     * without this it gets a 404 and softphone initialisation fails before it reaches the
     * registrar.
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
     * Writes one app setting, and fails if it did not take.
     *
     * <p>Bound to the envelope rather than to {@code String}. Exotel reports failure inside a 200 —
     * {@code {"Status":"failure","Error":...}} — so reading the body as a string treats a refusal
     * as a success. {@link #unwrap} is the only thing that actually checks.
     *
     * <p>Errors propagate on purpose. The caller decides what a failure means, and it must not
     * conclude the settings registered: writing CALLBACK_URL for a callback Exotel never accepted
     * leaves a row asserting the integration is wired when it is not, which is harder to diagnose
     * than an initialize that says plainly it failed.
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
     * Where Exotel should post status for calls it placed outside a call flow.
     *
     * <p>Built with the client code, not without it. The tenant segment is load-bearing — the
     * gateway derives the tenant from the host and path, so a URL missing it resolves to the wrong
     * client or 404s, and outbound call logging then fails silently, which is the worst way for it
     * to fail.
     */
    /**
     * The status callback URL, exactly as the connection states it.
     *
     * <p>Registered verbatim: no path appended, no scheme repaired, no host derived. It was
     * previously resolved from the tenant's app URL, which is a different address with a different
     * lifecycle — that one answers a browser, this one has to be reachable by the provider and has
     * to carry whatever prefix the gateway needs to resolve the tenant from the path. Deriving it
     * produced a plausible URL that no callback ever arrived on.
     *
     * <p>Required by {@link #initializeApp}, so the value is present by the time this runs.
     */
    private String callbackUrl(Connection connection) {
        return this.detail(connection, ExotelIntegrationsApiConfig.CALLBACK_URL).trim();
    }

    /**
     * The account's region, as the connection states it.
     *
     * <p>No fallback. It is in {@code REQUIRED_DETAILS}, so a connection without it never reaches
     * here, and a default would be unreachable code claiming to handle a case that cannot arise.
     */
    private String exotelDomain(Connection connection) {
        return this.detail(connection, ExotelIntegrationsApiConfig.EXOTEL_DOMAIN);
    }

    /**
     * Deletes this tenant's integration app at the provider, and forgets it locally.
     *
     * <p>Only possible because the app secret is captured at creation: the delete has to be
     * authenticated <b>as the app itself</b>. A customer token is accepted and answers {@code
     * "Deleted Successfully"} while deleting nothing, so an app whose secret was never stored
     * cannot be removed by anyone — which is how orphans accumulate.
     *
     * <p>That same silent success is why this verifies afterwards rather than trusting the
     * response.
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

    /**
     * Reads the listing back, because the delete response cannot be believed on its own.
     */
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
     * Maps one agent onto a SIP device, and records where they can be reached.
     *
     * <p>Two endpoint rows come out of this: the SIP identity at priority 1 and the agent's mobile
     * at priority 2. Ringing is sequential, so that order is what Exotel actually dials.
     */
    public Mono<ProvisionedAgent> provisionAgent(
            MessageAccess access, Connection connection, ProvisionAgentRequest request) {

        if (request.getUserId() == null) return this.missingParam(BaseMessageRequest.Fields.userId);
        if (request.getVirtualNumber() == null || request.getVirtualNumber().isBlank())
            return this.missingParam(ProvisionAgentRequest.Fields.virtualNumber);
        // Refused, not defaulted. Sequential ringing means Exotel dials this after
        // maxRingingDuration, so a blank or wrong value does not degrade to "browser
        // only" — it rings whoever that number reaches, on every call the agent does
        // not answer in time. Every agent here is required to hold both a number and an
        // email, so a missing one is a bad request rather than a shape to support, and
        // saying so at provisioning time is the only point where someone can still fix it.
        if (request.getAgentNumber() == null || request.getAgentNumber().isBlank())
            return this.missingParam(ProvisionAgentRequest.Fields.agentNumber);

        // Checked as numbers, not merely as present. Both reach the provider, and a typo comes back
        // as a mapping that will not dial — one round trip and one confusing error later than it
        // needs to. PhoneUtil is the same parser the rest of this service compares numbers with, so
        // what it rejects here is exactly what it could not have matched afterwards.
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
                            // The override wins when given: the provider's identity for this
                            // agent cannot always follow ours. Only then is the email
                            // requirement irrelevant, since it exists to supply this value.
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
     * Confirms the target user belongs to a client this caller may administer.
     *
     * <p>Separate from the {@code ROLE_Owner} gate on the endpoint, and not covered by it: being an
     * owner says whose <em>request</em> this is, not whose <em>user</em>. Without this an owner in
     * one tenant could mint SIP credentials for a user in another, on their own Exotel account, and
     * then mint session tokens as that user.
     *
     * <p>Hierarchy rather than strict equality, so a parent administering a client beneath it still
     * works — which is the normal shape here.
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
     * Refuses to hand one provider identity to two different agents.
     *
     * <p>Nothing in the schema prevents it — the endpoint rows are keyed on our user id, so two
     * users mapped to the same provider identity are two perfectly valid rows. What they are not is
     * two agents: they share one SIP endpoint, so each rings for the other's calls, inbound routing
     * sends a deal's customer to whoever answers first, and the outbound log attributes every call
     * to whichever user the lookup happened to return.
     *
     * <p>Worth checking because it is the natural shortcut. A per-user licence tempts an operator
     * to point a second agent at an identity that already works, and the result looks provisioned
     * from every angle until two people are on one call.
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
     * Brings the provider's mapping in line with the request, creating it if there is none.
     *
     * <p>Three outcomes, and the middle one is the reason this is not a plain "create if absent":
     * no mapping means create one, a mapping that already matches is reused untouched, and a
     * mapping that differs is updated. Re-provisioning an agent is how an operator changes their
     * virtual or agent number, and this method returning the stale mapping unconditionally is what
     * made that silently do nothing — the request succeeded, the row said the old number, and the
     * agent kept dialling out on it.
     *
     * <p>Still never creates a second AppUser for an agent who has one. The update goes through the
     * same {@code POST /usermapping}, which the provider treats as an upsert keyed on {@code
     * app_user_id}, so an existing agent is modified rather than duplicated — and the re-read below
     * is what proves it, rather than the 200.
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

    /**
     * Sends a changed mapping and confirms it landed.
     *
     * <p>Note what is deliberately not recorded: what the numbers changed <em>from</em>. The row
     * afterwards holds the new values, and these services do not log, so the previous ones are
     * gone. If that history is ever wanted it has to become a row, not a log line.
     */
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
     * Writes the mapping at the provider and reads back what it now holds.
     *
     * <p>The read-back is the point. {@link #mapUser} returning a SIP identity says the request was
     * accepted, not that the mapping is in the state it was asked for, and every guard after this —
     * dial-readiness, and whether an update actually applied — needs the provider's own copy rather
     * than the echo of what we sent.
     *
     * <p>Fails rather than completing empty when that read finds nothing. Completing empty here
     * would fall through to the caller's {@code switchIfEmpty} and post the mapping a second time,
     * and a second post for an agent who already has a mapping is the duplicate AppUser that {@link
     * #resolveMapping} exists to prevent.
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
     * What the request would change about an agent who is already provisioned.
     *
     * <p>True when the request would change something, false when it asks for nothing new. The two numbers have to be read from two different places, which is the whole
     * awkwardness here: Exotel echoes {@code VirtualNumber} back on the mapping, but its mapping
     * response carries no agent number at all, so the only record of what that agent's mobile is
     * currently set to is our own {@code PSTN_PHONE} endpoint row.
     *
     * <p>A missing endpoint row reports no agent-number change on purpose. There is nothing to
     * compare against, and the write that follows creates the row either way.
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
     * What the request would change about the virtual number, or empty when it changes nothing.
     *
     * <p>Re-asserted rather than assumed correct when the provider reports no virtual number on the
     * read. There is nothing to compare against, and treating "cannot tell" as "already right" is
     * precisely what let a changed number never leave this service. The upsert writes the same
     * values when it was already right, which costs one call on an admin action.
     */
    private static boolean virtualNumberChanged(ExotelUserMappingData existing, ProvisionAgentRequest request) {

        String current = existing.getVirtualNumber();

        if (StringUtil.safeIsBlank(current)) return true;

        return !PhoneUtil.isSameNumber(current, request.getVirtualNumber());
    }

    /**
     * The same for the agent number, whose only record is our own endpoint row.
     *
     * <p>A blank current value reports no change on purpose: there is no row to compare against yet,
     * and the write that follows creates it either way.
     */
    private static boolean agentNumberChanged(String current, ProvisionAgentRequest request) {

        return !current.isBlank() && !PhoneUtil.isSameNumber(current, request.getAgentNumber());
    }

    /**
     * Confirms the provider actually applied the new virtual number.
     *
     * <p>A 200 from the mapping upsert is not evidence that the change landed, and this integration
     * has been caught by that distinction more than once. Only the virtual number can be checked:
     * it is the one of the two numbers Exotel echoes back, so a rejected or unassigned number shows
     * up here as the old value still standing.
     *
     * <p>The agent number is unverifiable at the provider by the same asymmetry — the mapping
     * response never carries it — so our endpoint row is the only assertion we can make about it.
     */
    private Mono<ExotelUserMappingData> requireChangeApplied(
            ExotelUserMappingData updated, ProvisionAgentRequest request, String email) {

        if (PhoneUtil.isSameNumber(updated.getVirtualNumber(), request.getVirtualNumber())) return Mono.just(updated);

        // Unverifiable is not the same as wrong. If the provider reports no virtual number
        // on the read path there is nothing to compare, and refusing here would fail a
        // provisioning that is very likely correct. The endpoint rows written next stand as the
        // record of what was asked for.
        if (StringUtil.safeIsBlank(updated.getVirtualNumber())) return Mono.just(updated);

        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                MessageResourceService.EXOTEL_VIRTUAL_NUMBER_NOT_APPLIED,
                email,
                updated.getVirtualNumber(),
                request.getVirtualNumber());
    }

    /**
     * Refuses a mapping the agent could not actually dial from.
     *
     * <p>Two conditions, and the provider's own diagnosis of error 10715 names both: an inactive
     * user has no SIP row to originate from, and a mapping without a {@code SipId} has no browser
     * identity to register. Either one produces a softphone that connects and then fails on every
     * call, which is the most expensive way for this to be wrong — the agent looks provisioned to
     * everyone including themselves.
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
                        // An array even for one agent: Exotel takes a list and answers with a
                        // list.
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
     * Writes the agent's endpoint rows.
     *
     * <p>Scoped to the <b>tenant's</b> client code, taken from {@link MessageAccess}, not to the
     * agent's own. These rows were previously written under the agent's client, which reads as more
     * precise and is in fact a bug: every operation that touches them afterwards — deactivation,
     * teardown, the admin listing — scopes by the caller's tenant, and an owner is explicitly
     * allowed to manage agents in client hierarchies below their own. Written under the agent's
     * client, those rows became invisible to the very operations meant to manage them: the SIP
     * mapping would be revoked at the provider while our rows stayed active, minting tokens for an
     * agent who could no longer dial.
     *
     * <p>Nothing read the agent's own client code — it was stored for audit and never consulted —
     * so this loses no information that anything depended on, and it matches how {@code
     * message_call_provider_apps} has always been keyed.
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

        // Upserts, because re-provisioning is the only way to change an agent's numbers: an
        // insert here hits UK2_PROVIDER_USER_ENDPOINTS_AGENT and turns that change into a
        // duplicate-key error on the second run.
        // Answered through the same folding the listing uses, so what a create returns and what a
        // subsequent read returns cannot describe the agent differently.
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
     * Whether this tenant's app exists, without asking the provider.
     *
     * <p>A read of our own row. It says the app was registered, not that the provider still holds
     * it — an app deleted in the provider's console leaves this answering yes. That is the same
     * trade {@code browserCallStatus} makes on its cheap path, and for the same reason: a settings
     * screen loads this on every visit.
     */
    public Mono<CallAppStatus> callAppStatus(MessageAccess access) {

        return this.callProviderAppDAO
                .findByClient(access.getAppCode(), access.getClientCode(), this.provider())
                .map(this::appStatusOf)
                .defaultIfEmpty(CallAppStatus.notInitialized(this.provider()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelIntegrationsService.callAppStatus"));
    }

    /**
     * The outward view of an app row.
     *
     * <p>One builder for both the setup call and the status read, so what {@code initialize} answers
     * and what a later status check answers cannot drift apart. It also keeps
     * {@code CallProviderApp} — which holds the app secret and the raw provider payload — from
     * reaching a response at all.
     */
    private CallAppStatus appStatusOf(CallProviderApp app) {
        return CallAppStatus.of(this.provider(), app.getProviderAppName(), app.getCallbackUrl());
    }

    /**
     * Every agent provisioned on a connection, one entry each.
     *
     * <p>Collapses the destination rows into the agent they belong to. The table holds one row per
     * destination because ringing is sequential and {@code PRIORITY} is the order the provider
     * dials; a listing that returns those rows shows the same person twice, once for their browser
     * and once for their phone, and invites an operator to deactivate "the other one".
     *
     * <p>Collected and grouped in memory rather than through {@code Flux.groupBy}. This is an admin
     * listing of one tenant's agents — tens of rows — and {@code groupBy} carries a real hazard at
     * that size for no benefit: its inner groups must be consumed promptly or the operator stalls on
     * its prefetch. A {@code LinkedHashMap} also keeps the DAO's ordering, so agents come back in
     * the order it sorted them rather than in whatever order groups happened to complete.
     */
    public Flux<ProvisionedAgent> getAgentEndpoints(MessageAccess access, Connection connection) {

        return this.providerUserEndpointDAO
                .findByConnection(access.getAppCode(), access.getClientCode(), connection.getName())
                .collectList()
                .flatMapIterable(ExotelIntegrationsService::consolidate)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelIntegrationsService.getAgentEndpoints"));
    }

    // Package-private so the collapsing is testable without a database: it is presentation logic
    // over rows whose shape a screen must not see, and the half-provisioned cases are the ones
    // worth pinning.
    static List<ProvisionedAgent> consolidate(List<ProviderUserEndpoint> endpoints) {

        Map<ULong, ProvisionedAgent> byUser = new LinkedHashMap<>();

        for (ProviderUserEndpoint endpoint : endpoints)
            merge(byUser.computeIfAbsent(endpoint.getUserId(), id -> new ProvisionedAgent()), endpoint);

        return List.copyOf(byUser.values());
    }

    /**
     * Folds one destination row into the agent it belongs to.
     *
     * <p>The endpoint type decides which field it lands in, so an agent with only a phone comes back
     * with a null {@code sipEndpoint} rather than being hidden — a half-provisioned agent is exactly
     * what a screen needs to show. A type this does not recognise is ignored rather than guessed at:
     * {@code ENDPOINT_TYPE} is a string precisely so a third kind needs no migration, and inventing
     * a home for it here would put it in the wrong one.
     */
    private static ProvisionedAgent merge(ProvisionedAgent agent, ProviderUserEndpoint endpoint) {

        agent.setUserId(endpoint.getUserId());

        SetterUtil.setIfPresent(endpoint.getProviderUserId(), agent::setProviderUserId);
        SetterUtil.setIfPresent(endpoint.getVirtualNumber(), agent::setVirtualNumber);

        if (ENDPOINT_WEBRTC_SIP.equals(endpoint.getEndpointType())) agent.setSipEndpoint(endpoint.getEndpointValue());
        else if (ENDPOINT_PSTN_PHONE.equals(endpoint.getEndpointType()))
            agent.setAgentNumber(endpoint.getEndpointValue());

        if (endpoint.isActive()) agent.setActive(true);

        if (endpoint.getUpdatedAt() != null
                && (agent.getUpdatedAt() == null || endpoint.getUpdatedAt().isAfter(agent.getUpdatedAt())))
            agent.setUpdatedAt(endpoint.getUpdatedAt());

        return agent;
    }

    /**
     * Retires an agent locally and at the provider.
     *
     * <p>Both halves matter and they do different jobs. Clearing our rows stops this service
     * minting new tokens; deleting the mapping is what makes Exotel refuse the agent's next
     * registration. Neither invalidates a token already issued, so a session open right now
     * survives until it reconnects.
     */
    public Mono<Integer> deactivateAgent(MessageAccess access, Connection connection, ULong userId) {

        return FlatMapUtil.flatMapMono(
                        () -> this.requireApp(access, connection),
                        // Same reasoning as provisioning: an owner must not be able to deprovision
                        // someone else's tenant's agent, which would take their phones down.
                        app -> this.requireManagedUser(access, userId),
                        (app, allowed) -> this.providerIdentity(access, connection, userId),
                        (app, allowed, providerUserId) ->
                                this.appToken(connection, app.getProviderAppId(), app.getProviderAppSecret()),
                        (app, allowed, providerUserId, token) ->
                                this.revokeAtProvider(connection, token, providerUserId),
                        // Keyed on the agent, not the caller's tenant. Rows record whoever
                        // provisioned last, so a child-client owner retiring an agent a parent
                        // owner had provisioned would match nothing here — having already revoked
                        // the mapping at the provider. That left the rows active, tokens still being
                        // minted, and a softphone registering for calls that all failed. The
                        // caller's right to touch this agent was settled by requireManagedUser.
                        (app, allowed, providerUserId, token, revoked) -> this.providerUserEndpointDAO.deactivate(
                                access.getAppCode(), userId, connection.getName()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelIntegrationsService.deactivateAgent"));
    }

    /**
     * The provider-side identity to revoke, or blank when this agent holds none.
     *
     * <p>Blank rather than empty, deliberately. An agent with no active endpoint still has local
     * rows worth clearing, and completing empty here abandons the whole chain — the endpoint
     * answered 200 with no body while the deactivation never ran.
     *
     * <p>Blank identities are filtered out before the map because {@code PROVIDER_USER_ID} is
     * nullable, and Reactor raises {@code NullPointerException} when a mapper returns null rather
     * than treating it as an empty signal.
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
     * Removes the agent's mapping at the provider, and fails the operation if it cannot.
     *
     * <p>That failure has to surface, which is the whole reason this exists. Clearing our own rows
     * only stops this service minting new tokens; deleting the mapping is what makes the provider
     * refuse the agent's next registration. The result used to be bound into the chain and never
     * read, so a refused revocation returned 200 — leaving a departed agent with a working
     * softphone and an administrator no reason to look.
     *
     * <p>Nothing to revoke counts as success: an agent with no provider identity has none to remove.
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
                // DELETE with a body, on /users rather than /usermapping. Mappings are created
                // on one
                // endpoint and removed on the other, and the body is a bare array of app user
                // ids.
                .flatMap(client -> client.method(HttpMethod.DELETE)
                        .uri(ExotelIntegrationsApiConfig.usersUrl())
                        .bodyValue(List.of(providerUserId))
                        .retrieve()
                        .bodyToMono(String.class))
                .thenReturn(Boolean.TRUE)
                // False, not an error, so the caller decides what a refusal means. It treats it as
                // a failure of the whole deactivation rather than clearing local rows regardless.
                .onErrorResume(e -> Mono.just(Boolean.FALSE));
    }

    // ---------------------------------------------------------------------------------------
    // Browser session
    // ---------------------------------------------------------------------------------------

    /**
     * A fresh token per call. Never cached: one shared between agents is a credential leak.
     */
    public Mono<BrowserCallToken> generateBrowserToken(MessageAccess access, Connection connection, ULong userId) {

        return FlatMapUtil.flatMapMono(
                        () -> this.requireApp(access, connection),
                        app -> this.requireEndpoint(access, connection, userId),
                        (app, endpoint) -> this.requestToken(
                                connection,
                                ExotelTokenRequest.ofAgent(
                                        app.getProviderAppId(),
                                        app.getProviderAppSecret(),
                                        endpoint.getProviderUserId()),
                                OPERATION_AGENT_TOKEN),
                        // Never log the token itself. It authenticates as that agent for its full
                        // life, around ninety days, and it grants their SIP credentials and the
                        // ability to place calls on the tenant's account.
                        (app, endpoint, token) -> Mono.just(BrowserCallToken.of(
                                token, endpoint.getProviderUserId(), expiresInSeconds(token), this.provider())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelIntegrationsService.generateBrowserToken"));
    }

    /**
     * A database read, no provider round trip, so it is cheap enough for every page load.
     */
    public Mono<BrowserCallStatus> browserCallStatus(MessageAccess access, Connection connection, ULong userId) {
        return this.browserCallStatus(access, connection, userId, false);
    }

    /**
     * Whether this agent can take calls in the browser.
     *
     * <p>Two depths, because the cheap answer is the one worth giving on every page load and the
     * expensive one is the only answer that is actually true. Unverified, this reads our own rows:
     * fast, no provider round trip, and blind to a user the provider has since deactivated or
     * stripped of their SIP device. Verified, it asks the provider.
     *
     * <p>The gap between the two is not hypothetical. An agent whose provider user is inactive will
     * still register a softphone successfully — registration and origination read different records
     * — and then fail every call with an opaque 500. Nothing on the cheap path can see that. Worth
     * verifying before enabling a dial button, or when diagnosing an agent who says calling is
     * broken; not worth it on every page load.
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
                            // No mapping at the provider means the endpoint row
                            // outlived it. Not provisioned, and say so rather than
                            // reporting our row's optimistic view.
                            .defaultIfEmpty(status.setProvisioned(false).checkedWithProvider())
                            .onErrorResume(e -> Mono.just(status));
                })
                .defaultIfEmpty(BrowserCallStatus.notProvisioned(this.provider()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelIntegrationsService.browserCallStatus"));
    }

    /**
     * Asks the provider to ring this agent's browser and then the customer.
     *
     * <p>Authenticated as the <b>agent</b>, not as the app. The vendor's SDK holds a single
     * agent-scoped token and uses it for everything including this call, so that is the credential
     * this endpoint is known to accept; an app token has never been shown to work here. Minting it
     * server-side is what makes a backend-placed dial possible at all, and it is the same token
     * {@link #generateBrowserToken} already issues.
     *
     * <p>Returns everything needed to record the call outright: the provider answers synchronously
     * with the call's {@code Sid}, the virtual number it presented and the SIP endpoint it
     * originated from. A response without a {@code Sid} is refused rather than recorded, since a
     * row no callback can be matched to is worse than a loud failure.
     *
     * @param toNumber the customer's number, already resolved from the deal by the service that
     * owns the deal. Never taken from a browser.
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
                        (app, endpoint) -> this.requestToken(
                                connection,
                                ExotelTokenRequest.ofAgent(
                                        app.getProviderAppId(),
                                        app.getProviderAppSecret(),
                                        endpoint.getProviderUserId()),
                                OPERATION_DIAL_TOKEN),
                        (app, endpoint, agentToken) -> this.webClientConfig
                                .createExotelIntegrationsWebClient(
                                        connection, agentToken, ReactiveAuthenticationScheme.NONE)
                                .flatMap(client -> client.post()
                                        .uri(ExotelIntegrationsApiConfig.outboundCallUrl())
                                        .bodyValue(ExotelOutboundCallRequest.of(
                                                this.customerIdFor(app, connection),
                                                app.getProviderAppId(),
                                                toNumber,
                                                endpoint.getProviderUserId()))
                                        .retrieve()
                                        .bodyToMono(OUTBOUND_CALL_TYPE))
                                .flatMap(response -> this.unwrap(response, OPERATION_OUTBOUND_CALL)
                                        .flatMap(result -> this.toDialResult(response.getRequestId(), result))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelIntegrationsService.placeOutboundCall"));
    }

    /**
     * Reads the dial response, and refuses one that cannot identify the call.
     *
     * <p>A missing {@code CallSid} is treated as a failure rather than recorded as a gap. The
     * provider returns one synchronously — verified against a live dial — so its absence means
     * something happened that this code does not understand, and the honest response is to say so
     * loudly. The alternative, writing a row that cannot be matched to any callback, produces a
     * call whose duration and recording arrive later and land nowhere.
     *
     * <p>The call may still have been placed. That is what the request id in the message is for: it
     * is the identifier the provider's own logs are searched by.
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
     * The provider's customer id, from our own row first.
     *
     * <p>Captured when the app was created and stored alongside it, so it stays correct even if the
     * connection document is later edited. Falls back to the connection for a row written before
     * that metadata was recorded.
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

    /**
     * The agent's SIP endpoint, or a 403.
     *
     * <p>Forbidden rather than not-found on purpose: the UI has to tell "you are not set up for
     * calling" apart from "calling is broken", and a 404 reads as the second.
     */
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
     * Seconds until this token expires, read from its own {@code exp} claim.
     *
     * <p>Derived rather than reported: the response carries no {@code ExpiresIn}, and the claim set
     * is only {@code Id} and {@code exp} — there is no {@code iat} to subtract. Observed lifetime
     * is about 90 days, not the 24 hours the vendor's examples imply, so anything scheduling a
     * refresh must read this rather than assume.
     *
     * <p>Returns null when the token cannot be parsed. A caller should treat that as "unknown", not
     * as "expired": the token may well be fine and only its metadata unreadable.
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
