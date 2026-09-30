package com.fincity.saas.message.service.call.provider.telecmi;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.message.configuration.WebClientConfig;
import com.fincity.saas.message.configuration.call.telecmi.TelecmiApiConfig;
import com.fincity.saas.message.dao.call.CallProviderAppDAO;
import com.fincity.saas.message.dao.call.ProviderUserEndpointDAO;
import com.fincity.saas.message.dto.call.CallProviderApp;
import com.fincity.saas.message.dto.call.ProviderUserEndpoint;
import com.fincity.saas.message.dto.call.provider.telecmi.TelecmiCall;
import com.fincity.saas.message.model.base.BaseMessageRequest;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.fincity.saas.message.model.request.call.ProvisionAgentRequest;
import com.fincity.saas.message.model.request.call.provider.telecmi.TelecmiClickToCallRequest;
import com.fincity.saas.message.model.request.call.provider.telecmi.TelecmiUserRequest;
import com.fincity.saas.message.model.response.call.BrowserCallStatus;
import com.fincity.saas.message.model.response.call.BrowserCallToken;
import com.fincity.saas.message.model.response.call.CallAppStatus;
import com.fincity.saas.message.model.response.call.ProvisionedAgent;
import com.fincity.saas.message.model.response.call.provider.telecmi.TelecmiAgent;
import com.fincity.saas.message.model.response.call.provider.telecmi.TelecmiFlowReply;
import com.fincity.saas.message.model.response.call.provider.telecmi.TelecmiResponse;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.call.AgentEndpoints;
import com.fincity.saas.message.service.call.ICallRecordingService;
import com.fincity.saas.message.util.PhoneUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jooq.types.ULong;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * TeleCMI's side of browser calling. There is no app to create: setup checks the connection's app id and
 * secret and records them. Agents are TeleCMI users, and the softphone logs in with their id and password,
 * so the password is what the agent's endpoint stores and {@link #generateBrowserToken} hands out.
 */
@Service
public class TelecmiIntegrationsService {

    private static final String OPERATION_CREDENTIAL_CHECK = "credential check";
    private static final String OPERATION_LIST_USERS = "user listing";
    private static final String OPERATION_GET_USER = "user lookup";
    private static final String OPERATION_ADD_USER = "user creation";
    private static final String OPERATION_UPDATE_USER = "user update";
    private static final String OPERATION_REMOVE_USER = "user removal";
    private static final String OPERATION_CLICK_TO_CALL = "click-to-call";
    private static final String OPERATION_PLAY = "recording download";

    private static final String PARAM_USER_ID = "userId";
    private static final String PARAM_TO_NUMBER = "toNumber";
    private static final String PARAM_CALLER_ID = "callerId";

    /** Each attempt is one TeleCMI call. */
    private static final int MAX_EXTENSION_ATTEMPTS = 3;

    private static final int WEBHOOK_TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final List<String> REQUIRED_DETAILS = List.of(TelecmiApiConfig.APP_ID, TelecmiApiConfig.SECRET);

    private final WebClientConfig webClientConfig;
    private final CallProviderAppDAO callProviderAppDAO;
    private final ProviderUserEndpointDAO providerUserEndpointDAO;
    private final IFeignSecurityService securityService;
    private final MessageResourceService msgService;

    public TelecmiIntegrationsService(
            WebClientConfig webClientConfig,
            CallProviderAppDAO callProviderAppDAO,
            ProviderUserEndpointDAO providerUserEndpointDAO,
            IFeignSecurityService securityService,
            MessageResourceService msgService) {
        this.webClientConfig = webClientConfig;
        this.callProviderAppDAO = callProviderAppDAO;
        this.providerUserEndpointDAO = providerUserEndpointDAO;
        this.securityService = securityService;
        this.msgService = msgService;
    }

    private String provider() {
        return ConnectionSubType.TELECMI.getProvider();
    }

    private String detail(Connection connection, String key) {
        Object value = connection.getConnectionDetails() == null
                ? null
                : connection.getConnectionDetails().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Optional<String> firstMissingDetail(Connection connection) {
        return REQUIRED_DETAILS.stream()
                .filter(key -> StringUtil.safeIsBlank(this.detail(connection, key)))
                .findFirst();
    }

    private <T> Mono<T> missingDetail(String key) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                MessageResourceService.MISSING_CONNECTION_DETAILS,
                this.provider(),
                key);
    }

    private <T> Mono<T> invalidDetail(String key) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                MessageResourceService.INVALID_CONNECTION_DETAILS,
                this.provider(),
                key);
    }

    /** TeleCMI sends the app id as a number, and rejects it as a string. */
    private Long appId(Connection connection) {
        try {
            return Long.valueOf(this.detail(connection, TelecmiApiConfig.APP_ID).trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private TelecmiUserRequest appRequest(Connection connection) {
        return TelecmiUserRequest.ofApp(this.appId(connection), this.detail(connection, TelecmiApiConfig.SECRET));
    }

    private TelecmiUserRequest agentRequest(Connection connection, String agentId) {
        return TelecmiUserRequest.ofAgent(
                this.appId(connection), this.detail(connection, TelecmiApiConfig.SECRET), agentId);
    }

    /** Verbatim from the connection, never derived: a URL built from the app address answers a browser. */
    private String callbackUrl(Connection connection) {
        String url = this.detail(connection, TelecmiApiConfig.CALLBACK_URL);
        return StringUtil.safeIsBlank(url) ? null : url.trim();
    }

    private String sbcUri(Connection connection) {
        String sbc = this.detail(connection, TelecmiApiConfig.SBC_URI);
        return StringUtil.safeIsBlank(sbc) ? TelecmiApiConfig.DEFAULT_SBC_URI : sbc.trim();
    }

    /**
     * Returns TeleCMI's body whatever the HTTP status: refusals come both in 200 bodies and as HTTP errors, so the
     * caller decides from {@code code}. Only getting no answer at all is an error here.
     */
    private Mono<TelecmiResponse> post(Connection connection, String uri, Object body, String operation) {
        return this.webClientConfig
                .createTelecmiWebClient(connection)
                .flatMap(client ->
                        client.post().uri(uri).bodyValue(body).retrieve().bodyToMono(TelecmiResponse.class))
                .onErrorResume(WebClientResponseException.class, e -> Mono.just(readError(e)))
                .switchIfEmpty(this.requestFailed(operation, "no response"));
    }

    private static TelecmiResponse readError(WebClientResponseException e) {
        TelecmiResponse body;
        try {
            body = e.getResponseBodyAs(TelecmiResponse.class);
        } catch (RuntimeException unreadable) {
            body = null;
        }
        if (body == null) body = new TelecmiResponse().setMsg(e.getStatusText());
        if (body.getCode() == null) body.setCode(e.getStatusCode().value());
        return body;
    }

    private Mono<TelecmiResponse> require(TelecmiResponse response, String operation) {
        return response.isSuccess() ? Mono.just(response) : this.requestFailed(operation, response.errorDetail());
    }

    private <T> Mono<T> requestFailed(String operation, String cause) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                MessageResourceService.TELECMI_REQUEST_FAILED,
                operation,
                cause);
    }

    /**
     * Checks the credentials through {@code /v2/balance} and records them; idempotent, so rerunning picks up a
     * rotated secret. Balance is never judged: it has read 0 for a funded account. The webhook token is made once
     * with the row, only its hash is kept, and only that first response carries it.
     */
    public Mono<CallAppStatus> initializeApp(MessageAccess access, Connection connection) {

        Optional<String> missing = this.firstMissingDetail(connection);
        if (missing.isPresent()) return this.missingDetail(missing.get());
        if (this.appId(connection) == null) return this.invalidDetail(TelecmiApiConfig.APP_ID);

        return FlatMapUtil.flatMapMono(
                        () -> this.callProviderAppDAO
                                .findByClient(access.getAppCode(), access.getClientCode(), this.provider())
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        existing -> this.requireSameAccount(access, connection, existing),
                        (existing, sameAccount) -> this.post(
                                connection,
                                TelecmiApiConfig.balanceUrl(),
                                this.appRequest(connection),
                                OPERATION_CREDENTIAL_CHECK),
                        (existing, sameAccount, response) -> this.require(response, OPERATION_CREDENTIAL_CHECK),
                        (existing, sameAccount, response, verified) -> existing.isPresent()
                                ? this.refreshApp(connection, existing.get(), verified)
                                        .map(this::appStatusOf)
                                : this.registerApp(access, connection, verified))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiIntegrationsService.initializeApp"));
    }

    /**
     * Refuses to move to another TeleCMI account while agents are provisioned on the first: their users exist only
     * there, and deactivation against the new account would count them removed without freeing the paid seats.
     */
    private Mono<Boolean> requireSameAccount(
            MessageAccess access, Connection connection, Optional<CallProviderApp> existing) {

        String newAppId = this.detail(connection, TelecmiApiConfig.APP_ID).trim();

        if (existing.isEmpty()
                || StringUtil.safeIsBlank(existing.get().getProviderAppId())
                || newAppId.equals(existing.get().getProviderAppId().trim())) return Mono.just(Boolean.TRUE);

        CallProviderApp app = existing.get();
        String agentsOn =
                StringUtil.safeIsBlank(app.getConnectionName()) ? connection.getName() : app.getConnectionName();

        return this.providerUserEndpointDAO
                .findByConnection(access.getAppCode(), access.getClientCode(), agentsOn)
                .filter(ProviderUserEndpoint::isActive)
                .map(ProviderUserEndpoint::getUserId)
                .distinct()
                .count()
                .flatMap(active -> active > 0
                        ? this.msgService.<Boolean>throwMessage(
                                msg -> new GenericException(HttpStatus.CONFLICT, msg),
                                MessageResourceService.TELECMI_APP_CHANGED,
                                app.getProviderAppId(),
                                newAppId,
                                active)
                        : Mono.just(Boolean.TRUE));
    }

    private Mono<CallProviderApp> refreshApp(Connection connection, CallProviderApp existing, TelecmiResponse balance) {

        Map<String, Object> metadata = existing.getProviderMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(existing.getProviderMetadata());

        existing.setConnectionName(connection.getName())
                .setProviderAppId(
                        this.detail(connection, TelecmiApiConfig.APP_ID).trim())
                .setProviderAppSecret(this.detail(connection, TelecmiApiConfig.SECRET))
                .setAccountSid(this.detail(connection, TelecmiApiConfig.APP_ID).trim())
                .setCallbackUrl(this.callbackUrl(connection))
                .setProviderMetadata(withFlowUrl(withBalance(metadata, balance), connection));

        return this.callProviderAppDAO.update(existing).thenReturn(existing);
    }

    private Mono<CallAppStatus> registerApp(MessageAccess access, Connection connection, TelecmiResponse balance) {

        String webhookToken = newWebhookToken();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(TelecmiApiConfig.META_WEBHOOK_TOKEN_HASH, sha256Hex(webhookToken));

        CallProviderApp row = new CallProviderApp()
                .setConnectionName(connection.getName())
                .setProvider(this.provider())
                .setProviderAppId(
                        this.detail(connection, TelecmiApiConfig.APP_ID).trim())
                .setProviderAppSecret(this.detail(connection, TelecmiApiConfig.SECRET))
                .setAccountSid(this.detail(connection, TelecmiApiConfig.APP_ID).trim())
                .setCallbackUrl(this.callbackUrl(connection))
                .setProviderMetadata(withFlowUrl(withBalance(metadata, balance), connection));

        row.setAppCode(access.getAppCode()).setClientCode(access.getClientCode());

        return this.callProviderAppDAO
                .create(row)
                .map(created -> this.appStatusOf(created).setWebhookToken(webhookToken))
                // Two setups raced and the other won: answer with its row, without a token.
                .onErrorResume(e -> this.callProviderAppDAO
                        .findByClient(access.getAppCode(), access.getClientCode(), this.provider())
                        .map(this::appStatusOf)
                        .switchIfEmpty(Mono.error(e)));
    }

    private Map<String, Object> withFlowUrl(Map<String, Object> metadata, Connection connection) {
        String url = this.detail(connection, TelecmiApiConfig.HTTP_FLOW_URL);
        if (StringUtil.safeIsBlank(url)) metadata.remove(TelecmiApiConfig.META_HTTP_FLOW_URL);
        else metadata.put(TelecmiApiConfig.META_HTTP_FLOW_URL, url.trim());
        return metadata;
    }

    private static Map<String, Object> withBalance(Map<String, Object> metadata, TelecmiResponse balance) {
        if (balance.getBalance() != null) metadata.put(TelecmiApiConfig.META_BALANCE, balance.getBalance());
        if (balance.getExpire() != null) metadata.put(TelecmiApiConfig.META_EXPIRE, balance.getExpire());
        return metadata;
    }

    /** Never the row itself, which carries the secret. */
    CallAppStatus appStatusOf(CallProviderApp app) {

        CallAppStatus status = CallAppStatus.of(this.provider(), app.getProviderAppName(), app.getCallbackUrl());

        Map<String, Object> metadata = app.getProviderMetadata();
        if (metadata == null) return status;

        if (metadata.get(TelecmiApiConfig.META_BALANCE) instanceof Number balance)
            status.setBalance(balance.doubleValue());

        if (metadata.get(TelecmiApiConfig.META_HTTP_FLOW_URL) instanceof String flowUrl) status.setHttpFlowUrl(flowUrl);

        if (metadata.get(TelecmiApiConfig.META_EXPIRE) instanceof Number expire)
            status.setExpiresAt(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(expire.longValue()), ZoneId.systemDefault()));

        return status;
    }

    /** Balance and expiry are as of the last setup run. */
    public Mono<CallAppStatus> callAppStatus(MessageAccess access) {
        return this.callProviderAppDAO
                .findByClient(access.getAppCode(), access.getClientCode(), this.provider())
                .map(this::appStatusOf)
                .defaultIfEmpty(CallAppStatus.notInitialized(this.provider()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiIntegrationsService.callAppStatus"));
    }

    /**
     * Local only, and refused while any agent is provisioned: removing a TeleCMI user deletes it outright, and an
     * adopted one may be a person's own dashboard login.
     */
    public Mono<Boolean> teardownApp(MessageAccess access, Connection connection) {

        return this.providerUserEndpointDAO
                .findByConnection(access.getAppCode(), access.getClientCode(), connection.getName())
                .filter(ProviderUserEndpoint::isActive)
                .map(ProviderUserEndpoint::getUserId)
                .distinct()
                .count()
                .flatMap(active -> active > 0
                        ? this.msgService.<Boolean>throwMessage(
                                msg -> new GenericException(HttpStatus.CONFLICT, msg),
                                MessageResourceService.TELECMI_AGENTS_STILL_PROVISIONED,
                                active,
                                connection.getName())
                        : this.providerUserEndpointDAO
                                .purgeByConnection(access.getAppCode(), access.getClientCode(), connection.getName())
                                .then(this.callProviderAppDAO.purgeByClient(
                                        access.getAppCode(), access.getClientCode(), this.provider()))
                                .thenReturn(Boolean.TRUE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiIntegrationsService.teardownApp"));
    }

    /** {@code toString} hides the password: {@code FlatMapUtil}'s debug logging prints each step's value. */
    record ResolvedAgent(String agentId, String password) {

        @Override
        public String toString() {
            return "ResolvedAgent[agentId=" + this.agentId + ", password=***]";
        }
    }

    /**
     * Maps an agent onto a TeleCMI user: {@code appUserId} adopts one, a provisioned agent keeps theirs, anyone else
     * gets a new user. Never matched on number or email, since TeleCMI says an email is taken but not by whom.
     * Every path sends {@code followme: true} (TeleCMI's default is false) so a closed browser rings the mobile.
     */
    public Mono<ProvisionedAgent> provisionAgent(
            MessageAccess access, Connection connection, ProvisionAgentRequest request) {

        if (request.getUserId() == null) return this.missingParam(BaseMessageRequest.Fields.userId);
        if (StringUtil.safeIsBlank(request.getVirtualNumber()))
            return this.missingParam(ProvisionAgentRequest.Fields.virtualNumber);
        if (StringUtil.safeIsBlank(request.getAgentNumber()))
            return this.missingParam(ProvisionAgentRequest.Fields.agentNumber);

        if (PhoneUtil.parse(request.getVirtualNumber()) == null)
            return this.invalidParam(ProvisionAgentRequest.Fields.virtualNumber, request.getVirtualNumber());

        PhoneNumber agentNumber = PhoneUtil.parse(request.getAgentNumber());
        if (agentNumber == null)
            return this.invalidParam(ProvisionAgentRequest.Fields.agentNumber, request.getAgentNumber());

        if (request.getPassword() != null && request.getPassword().length() < TelecmiApiConfig.MIN_PASSWORD_LENGTH)
            return this.passwordTooShort();

        return FlatMapUtil.flatMapMono(
                        () -> this.requireApp(access, connection),
                        app -> this.requireManagedUser(access, request.getUserId()),
                        (app, allowed) -> this.securityService.getUserInternal(
                                request.getUserId().toBigInteger(), null),
                        (app, allowed, user) -> this.resolveAgent(access, connection, request, user, agentNumber),
                        (app, allowed, user, agent) -> this.writeEndpoints(access, connection, request, agent))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiIntegrationsService.provisionAgent"));
    }

    private Mono<ResolvedAgent> resolveAgent(
            MessageAccess access,
            Connection connection,
            ProvisionAgentRequest request,
            User user,
            PhoneNumber agentNumber) {

        String phone = telecmiPhone(agentNumber);

        if (!StringUtil.safeIsBlank(request.getAppUserId())) {
            String agentId = request.getAppUserId().trim();

            Mono<ResolvedAgent> refused = this.passwordRefusal(request);
            if (refused != null) return refused;

            return this.findAgent(connection, agentId)
                    .switchIfEmpty(this.msgService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            MessageResourceService.TELECMI_AGENT_NOT_FOUND,
                            agentId))
                    .flatMap(agent -> this.adopt(access, connection, request, agent, phone, request.getPassword()));
        }

        return this.providerUserEndpointDAO
                .findEndpoint(
                        access.getAppCode(),
                        request.getUserId(),
                        connection.getName(),
                        ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP)
                .filter(endpoint -> !StringUtil.safeIsBlank(endpoint.getProviderUserId()))
                .flatMap(endpoint -> this.findAgent(connection, endpoint.getProviderUserId())
                        .flatMap(agent -> this.refresh(connection, agent, phone, request.getPassword(), endpoint)))
                .switchIfEmpty(Mono.defer(() -> this.create(connection, request, user, phone)));
    }

    /**
     * Without a password in the request, TeleCMI's own is copied back from {@code /v3/user/get}: an agent can
     * change it in TeleCMI's portal, and the softphone must keep logging in.
     */
    private Mono<ResolvedAgent> refresh(
            Connection connection,
            TelecmiAgent agent,
            String phone,
            String requestedPassword,
            ProviderUserEndpoint endpoint) {

        String livePassword = StringUtil.safeIsBlank(agent.getPassword()) ? passwordOf(endpoint) : agent.getPassword();

        boolean passwordChanged = requestedPassword != null && !requestedPassword.equals(livePassword);
        boolean phoneChanged = !PhoneUtil.isSameNumber(agent.getPhone(), phone);
        boolean followmeOff = !Boolean.TRUE.equals(agent.getFollowme());

        String password = requestedPassword != null ? requestedPassword : livePassword;

        if (password == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.MISSING_CALL_PARAMETERS,
                    this.provider(),
                    ProvisionAgentRequest.Fields.password);

        ResolvedAgent resolved = new ResolvedAgent(agent.getAgentId(), password);

        if (!passwordChanged && !phoneChanged && !followmeOff) return Mono.just(resolved);

        TelecmiUserRequest update = this.agentRequest(connection, agent.getAgentId());
        if (passwordChanged) update.setPassword(requestedPassword);
        if (phoneChanged) update.setPhoneNumber(phone);
        if (followmeOff) update.setFollowme(Boolean.TRUE);

        return this.updateAgent(connection, update).thenReturn(resolved);
    }

    private Mono<ResolvedAgent> adopt(
            MessageAccess access,
            Connection connection,
            ProvisionAgentRequest request,
            TelecmiAgent agent,
            String phone,
            String password) {

        TelecmiUserRequest update = this.agentRequest(connection, agent.getAgentId())
                .setPassword(password)
                .setFollowme(Boolean.TRUE);

        if (!PhoneUtil.isSameNumber(agent.getPhone(), phone)) update.setPhoneNumber(phone);

        // Claimed-check first: resetting another agent's password would sign them out of their softphone.
        return this.requireIdentityUnclaimed(access, request, agent.getAgentId())
                .then(this.updateAgent(connection, update))
                .thenReturn(new ResolvedAgent(agent.getAgentId(), password));
    }

    private Mono<ResolvedAgent> create(Connection connection, ProvisionAgentRequest request, User user, String phone) {

        if (StringUtil.safeIsBlank(user.getEmailId()))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.AGENT_HAS_NO_EMAIL,
                    request.getUserId());

        Mono<ResolvedAgent> refused = this.passwordRefusal(request);
        if (refused != null) return refused;

        // Plain operators, not FlatMapUtil, so the password is never a logged step value.
        return this.post(connection, TelecmiApiConfig.userAllUrl(), this.appRequest(connection), OPERATION_LIST_USERS)
                .flatMap(response -> this.require(response, OPERATION_LIST_USERS))
                .flatMap(listing -> {
                    Set<Integer> taken = new HashSet<>();
                    if (listing.getAgents() != null)
                        for (TelecmiAgent agent : listing.getAgents())
                            if (agent.getExtension() != null) taken.add(agent.getExtension());

                    return this.createAgent(connection, user, phone, request.getPassword(), taken, 0);
                });
    }

    /**
     * Steps on when an extension is taken, since the listing can be stale. A duplicate email is a 409 at once:
     * not a race, and the owner must name that user in {@code appUserId} to adopt it.
     */
    private Mono<ResolvedAgent> createAgent(
            Connection connection, User user, String phone, String password, Set<Integer> taken, int attempt) {

        Integer extension = lowestFreeExtension(taken);

        if (extension == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.CONFLICT, msg),
                    MessageResourceService.TELECMI_NO_FREE_EXTENSION,
                    TelecmiApiConfig.MIN_EXTENSION,
                    TelecmiApiConfig.MAX_EXTENSION);

        String firstName = StringUtil.safeIsBlank(user.getFirstName()) ? user.getEmailId() : user.getFirstName();
        // TeleCMI requires a last name.
        String lastName = StringUtil.safeIsBlank(user.getLastName()) ? firstName : user.getLastName();

        TelecmiUserRequest add = this.appRequest(connection)
                .setExtension(extension)
                .setFirstName(firstName)
                .setLastName(lastName)
                .setEmailId(user.getEmailId())
                .setPhoneNumber(phone)
                .setPassword(password)
                .setFollowme(Boolean.TRUE);

        return this.post(connection, TelecmiApiConfig.userAddUrl(), add, OPERATION_ADD_USER)
                .flatMap(response -> {
                    if (response.isSuccess())
                        return Mono.just(new ResolvedAgent(
                                response.getAgent() != null
                                                && response.getAgent().getAgentId() != null
                                        ? response.getAgent().getAgentId()
                                        : extension + "_" + this.appId(connection),
                                password));

                    if (response.refused(TelecmiApiConfig.MSG_EXTENSION_EXISTS)
                            && attempt + 1 < MAX_EXTENSION_ATTEMPTS) {
                        taken.add(extension);
                        return this.createAgent(connection, user, phone, password, taken, attempt + 1);
                    }

                    if (response.refused(TelecmiApiConfig.MSG_EMAIL_EXISTS))
                        return this.msgService.<ResolvedAgent>throwMessage(
                                msg -> new GenericException(HttpStatus.CONFLICT, msg),
                                MessageResourceService.TELECMI_EMAIL_CLAIMED,
                                user.getEmailId());

                    return this.requestFailed(OPERATION_ADD_USER, response.errorDetail());
                });
    }

    /** Null when the password is usable; synchronous so the password is never a value in a logged chain. */
    private <T> Mono<T> passwordRefusal(ProvisionAgentRequest request) {
        if (StringUtil.safeIsBlank(request.getPassword()))
            return this.missingParam(ProvisionAgentRequest.Fields.password);
        if (request.getPassword().length() < TelecmiApiConfig.MIN_PASSWORD_LENGTH) return this.passwordTooShort();
        return null;
    }

    private <T> Mono<T> passwordTooShort() {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                MessageResourceService.TELECMI_PASSWORD_TOO_SHORT,
                TelecmiApiConfig.MIN_PASSWORD_LENGTH);
    }

    /** TeleCMI answers 404 for an unknown user and for a no-op update, so a 404 asks whether the user exists. */
    private Mono<Boolean> updateAgent(Connection connection, TelecmiUserRequest update) {

        return this.post(connection, TelecmiApiConfig.userUpdateUrl(), update, OPERATION_UPDATE_USER)
                .flatMap(response -> {
                    if (response.isSuccess()) return Mono.just(Boolean.TRUE);
                    if (!response.isNotFound())
                        return this.requestFailed(OPERATION_UPDATE_USER, response.errorDetail());

                    return this.findAgent(connection, update.getAgentId())
                            .map(exists -> Boolean.TRUE)
                            .switchIfEmpty(this.msgService.throwMessage(
                                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                    MessageResourceService.TELECMI_AGENT_NOT_FOUND,
                                    update.getAgentId()));
                });
    }

    private Mono<TelecmiAgent> findAgent(Connection connection, String agentId) {

        return this.post(
                        connection,
                        TelecmiApiConfig.userGetUrl(),
                        this.agentRequest(connection, agentId),
                        OPERATION_GET_USER)
                .flatMap(response -> {
                    if (response.isSuccess() && response.getAgent() != null) return Mono.just(response.getAgent());
                    if (response.isNotFound()) return Mono.empty();
                    return this.requestFailed(OPERATION_GET_USER, response.errorDetail());
                });
    }

    private Mono<ProvisionedAgent> writeEndpoints(
            MessageAccess access, Connection connection, ProvisionAgentRequest request, ResolvedAgent agent) {

        ProviderUserEndpoint sip = this.endpoint(
                access, connection, request, ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP, agent.agentId(), 1);

        sip.setProviderUserId(agent.agentId())
                .setProviderMetadata(Map.of(TelecmiApiConfig.META_PASSWORD, agent.password()));

        ProviderUserEndpoint pstn = this.endpoint(
                access, connection, request, ProviderUserEndpoint.ENDPOINT_PSTN_PHONE, request.getAgentNumber(), 2);

        pstn.setProviderUserId(agent.agentId());

        return this.providerUserEndpointDAO.upsert(sip).flatMap(writtenSip -> this.providerUserEndpointDAO
                .upsert(pstn)
                .map(writtenPstn ->
                        AgentEndpoints.fold(AgentEndpoints.fold(new ProvisionedAgent(), writtenSip), writtenPstn)));
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

    public Flux<ProvisionedAgent> getAgentEndpoints(MessageAccess access, Connection connection) {
        return this.providerUserEndpointDAO
                .findByConnection(access.getAppCode(), access.getClientCode(), connection.getName())
                .collectList()
                .flatMapIterable(AgentEndpoints::consolidate)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiIntegrationsService.getAgentEndpoints"));
    }

    /** A user TeleCMI no longer has counts as removed, so a retry after a half-finished deactivation completes. */
    public Mono<Integer> deactivateAgent(MessageAccess access, Connection connection, ULong userId) {

        return FlatMapUtil.flatMapMono(
                        () -> this.requireApp(access, connection),
                        app -> this.requireManagedUser(access, userId),
                        (app, allowed) -> this.providerIdentity(access, connection, userId),
                        (app, allowed, agentId) -> this.removeAtProvider(connection, agentId),
                        (app, allowed, agentId, removed) -> this.providerUserEndpointDAO.deactivate(
                                access.getAppCode(), userId, connection.getName()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiIntegrationsService.deactivateAgent"));
    }

    /** Blank when the agent holds no TeleCMI id, so their local rows still get cleared. */
    private Mono<String> providerIdentity(MessageAccess access, Connection connection, ULong userId) {
        return this.providerUserEndpointDAO
                .findActiveEndpoints(access.getAppCode(), userId, connection.getName(), this.provider())
                .filter(endpoint -> !StringUtil.safeIsBlank(endpoint.getProviderUserId()))
                .next()
                .map(ProviderUserEndpoint::getProviderUserId)
                .defaultIfEmpty("");
    }

    private Mono<Boolean> removeAtProvider(Connection connection, String agentId) {

        if (agentId.isBlank()) return Mono.just(Boolean.TRUE);

        return this.post(
                        connection,
                        TelecmiApiConfig.userRemoveUrl(),
                        this.agentRequest(connection, agentId),
                        OPERATION_REMOVE_USER)
                .flatMap(response -> response.isSuccess()
                        ? Mono.just(Boolean.TRUE)
                        : this.findAgent(connection, agentId)
                                .flatMap(stillThere ->
                                        this.<Boolean>requestFailed(OPERATION_REMOVE_USER, response.errorDetail()))
                                .defaultIfEmpty(Boolean.TRUE));
    }

    /**
     * The agent's id, password and SBC. The password, not a token: PIOPIY answers SIP digest and fetches its own
     * REST token with it. It does not expire, hence no {@code expiresIn}. Never log it.
     */
    public Mono<BrowserCallToken> generateBrowserToken(MessageAccess access, Connection connection, ULong userId) {

        return FlatMapUtil.flatMapMono(
                        () -> this.requireApp(access, connection),
                        app -> this.requireEndpoint(access, connection, userId),
                        (app, endpoint) -> {
                            String password = passwordOf(endpoint);

                            if (password == null)
                                return this.msgService.<BrowserCallToken>throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        MessageResourceService.TELECMI_AGENT_NO_PASSWORD,
                                        userId,
                                        connection.getName());

                            return Mono.just(
                                    BrowserCallToken.of(password, endpoint.getProviderUserId(), null, this.provider())
                                            .setRegion(this.sbcUri(connection)));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiIntegrationsService.generateBrowserToken"));
    }

    /** Verified, it asks TeleCMI whether the user still exists: one deleted in its dashboard looks provisioned. */
    public Mono<BrowserCallStatus> browserCallStatus(
            MessageAccess access, Connection connection, ULong userId, boolean verifyWithProvider) {

        return this.providerUserEndpointDAO
                .findActiveEndpoints(access.getAppCode(), userId, connection.getName(), this.provider())
                .filter(endpoint -> ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP.equals(endpoint.getEndpointType()))
                .next()
                .flatMap(endpoint -> {
                    BrowserCallStatus status = BrowserCallStatus.of(
                            this.provider(), endpoint.getProviderUserId(), endpoint.getVirtualNumber());

                    if (!verifyWithProvider) return Mono.just(status);

                    return this.findAgent(connection, endpoint.getProviderUserId())
                            .map(agent -> status.checkedWithProvider())
                            // Deferred: defaultIfEmpty's argument would mutate status before TeleCMI answered.
                            .switchIfEmpty(Mono.fromSupplier(
                                    () -> status.setProvisioned(false).checkedWithProvider()))
                            .onErrorResume(e -> Mono.just(status));
                })
                .defaultIfEmpty(BrowserCallStatus.notProvisioned(this.provider()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiIntegrationsService.browserCallStatus"));
    }

    /**
     * Proves a webhook is from this tenant's account: the {@code ?t=} token hashes to the stored one (constant
     * time) and the body's app id is the tenant's app. Every failure is the same 401.
     */
    public Mono<CallProviderApp> verifyWebhook(MessageAccess access, String token, String appId) {

        if (StringUtil.safeIsBlank(token)) return this.webhookUnauthorized();

        return this.callProviderAppDAO
                .findByClient(access.getAppCode(), access.getClientCode(), this.provider())
                .filter(app -> tokenMatches(app, token) && appId != null && appId.equals(app.getProviderAppId()))
                .switchIfEmpty(Mono.defer(this::webhookUnauthorized))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiIntegrationsService.verifyWebhook"));
    }

    static boolean tokenMatches(CallProviderApp app, String token) {
        Object stored = app.getProviderMetadata() == null
                ? null
                : app.getProviderMetadata().get(TelecmiApiConfig.META_WEBHOOK_TOKEN_HASH);
        if (!(stored instanceof String hash)) return false;
        return MessageDigest.isEqual(
                hash.getBytes(StandardCharsets.UTF_8), sha256Hex(token).getBytes(StandardCharsets.UTF_8));
    }

    private <T> Mono<T> webhookUnauthorized() {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.UNAUTHORIZED, msg),
                MessageResourceService.TELECMI_WEBHOOK_UNAUTHORIZED);
    }

    /**
     * The agent's provisioned endpoints, else the profile phone only when they have none; nothing to ring is
     * refused rather than sent as an empty target. A phone target without {@code agent_id} is undocumented
     * and untested.
     */
    public Mono<TelecmiFlowReply> flowReply(
            MessageAccess access, Connection connection, ULong userId, PhoneNumber profilePhone) {

        return this.providerUserEndpointDAO
                .findActiveEndpoints(access.getAppCode(), userId, connection.getName(), this.provider())
                .collectList()
                .flatMap(endpoints -> {
                    TelecmiFlowReply.Target target = flowTarget(endpoints, profilePhone);

                    if (target == null)
                        return this.msgService.<TelecmiFlowReply>throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                MessageResourceService.AGENT_UNREACHABLE,
                                userId,
                                connection.getName());

                    return Mono.just(new TelecmiFlowReply()
                            .setFollowme(target.getPhone() != null)
                            .setTimeout(this.flowTimeout(connection))
                            .setResult(List.of(target)));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiIntegrationsService.flowReply"));
    }

    static TelecmiFlowReply.Target flowTarget(List<ProviderUserEndpoint> endpoints, PhoneNumber profilePhone) {

        String agentId = endpoints.stream()
                .filter(endpoint -> ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP.equals(endpoint.getEndpointType()))
                .map(ProviderUserEndpoint::getProviderUserId)
                .filter(id -> !StringUtil.safeIsBlank(id))
                .findFirst()
                .orElse(null);

        PhoneNumber mobile = endpoints.stream()
                .filter(endpoint -> ProviderUserEndpoint.ENDPOINT_PSTN_PHONE.equals(endpoint.getEndpointType()))
                .map(endpoint -> PhoneUtil.parse(endpoint.getEndpointValue()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(endpoints.isEmpty() ? profilePhone : null);

        if (agentId == null && mobile == null) return null;

        return new TelecmiFlowReply.Target().setAgentId(agentId).setPhone(mobile == null ? null : telecmiPhone(mobile));
    }

    private int flowTimeout(Connection connection) {
        try {
            String value = this.detail(connection, TelecmiApiConfig.FLOW_TIMEOUT);
            int seconds = value == null ? TelecmiFlowReply.DEFAULT_TIMEOUT_SECONDS : Integer.parseInt(value.trim());
            return seconds > 0 ? seconds : TelecmiFlowReply.DEFAULT_TIMEOUT_SECONDS;
        } catch (NumberFormatException e) {
            return TelecmiFlowReply.DEFAULT_TIMEOUT_SECONDS;
        }
    }

    boolean flowAppMatches(Connection connection, Map<String, String> flowRequest) {
        String appId = flowRequest.get(TelecmiApiConfig.FLOW_APP_ID);
        return appId != null
                && appId.trim()
                        .equals(this.detail(connection, TelecmiApiConfig.APP_ID).trim());
    }

    public <T> Mono<T> unauthorized() {
        return this.webhookUnauthorized();
    }

    /** The row's code is already in {@code extra_params}, so the row can be written before TeleCMI rings. */
    public record TelecmiDial(TelecmiClickToCallRequest request, TelecmiCall call) {}

    /**
     * Checks everything before anything is written or rung. The caller id asked for must be the agent's or the
     * connection's number: it comes from the page, and TeleCMI is not known to check it belongs to the account.
     */
    public Mono<TelecmiDial> prepareDial(
            MessageAccess access,
            Connection connection,
            ULong userId,
            String toNumber,
            String callerId,
            boolean browser) {

        if (userId == null) return this.missingParam(PARAM_USER_ID);
        if (StringUtil.safeIsBlank(toNumber)) return this.missingParam(PARAM_TO_NUMBER);

        PhoneNumber customer = PhoneUtil.parse(toNumber);
        if (customer == null) return this.invalidParam(PARAM_TO_NUMBER, toNumber);

        Optional<String> missing = this.firstMissingDetail(connection);
        if (missing.isPresent()) return this.missingDetail(missing.get());

        return FlatMapUtil.flatMapMono(
                        () -> this.requireApp(access, connection),
                        app -> this.requireEndpoint(access, connection, userId),
                        (app, sip) -> browser
                                ? Mono.just(Optional.<ProviderUserEndpoint>empty())
                                : this.providerUserEndpointDAO
                                        .findEndpoint(
                                                access.getAppCode(),
                                                userId,
                                                connection.getName(),
                                                ProviderUserEndpoint.ENDPOINT_PSTN_PHONE)
                                        .filter(ProviderUserEndpoint::isActive)
                                        .map(Optional::of)
                                        .defaultIfEmpty(Optional.empty()),
                        (app, sip, mobile) -> this.toDial(connection, sip, mobile, customer, callerId, browser))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiIntegrationsService.prepareDial"));
    }

    private Mono<TelecmiDial> toDial(
            Connection connection,
            ProviderUserEndpoint sip,
            Optional<ProviderUserEndpoint> mobile,
            PhoneNumber customer,
            String callerId,
            boolean browser) {

        String agentNumber = sip.getVirtualNumber();
        String connectionNumber = this.detail(connection, TelecmiApiConfig.CALLER_ID);

        if (!StringUtil.safeIsBlank(callerId) && !isOneOf(callerId, agentNumber, connectionNumber))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.TELECMI_CALLER_ID_NOT_ALLOWED,
                    callerId,
                    connection.getName());

        String presented = firstNonBlank(callerId, agentNumber, connectionNumber);
        if (presented == null) return this.missingDetail(TelecmiApiConfig.CALLER_ID);

        PhoneNumber shown = PhoneUtil.parse(presented);
        if (shown == null) return this.invalidParam(PARAM_CALLER_ID, presented);

        String agentId = sip.getProviderUserId();
        String secret = this.detail(connection, TelecmiApiConfig.SECRET);
        Long to = Long.valueOf(telecmiPhone(customer));
        Long shownNumber = Long.valueOf(telecmiPhone(shown));

        TelecmiCall call = new TelecmiCall()
                .setConnectionName(connection.getName())
                .setIsOutbound(Boolean.TRUE)
                .setFrom(
                        browser
                                ? agentId
                                : mobile.map(ProviderUserEndpoint::getEndpointValue)
                                        .orElse(null))
                .setToDialCode(customer.getCountryCode())
                .setTo(customer.getNumber())
                .setCustomerDialCode(customer.getCountryCode())
                .setCustomerPhoneNumber(customer.getNumber())
                .setCallerId(shown.getNumber())
                .setStartTime(LocalDateTime.now());

        TelecmiClickToCallRequest request = (browser
                        ? TelecmiClickToCallRequest.ofBrowser(agentId, secret, to, shownNumber)
                        : TelecmiClickToCallRequest.ofMobile(agentId, secret, to, shownNumber))
                .setExtraParams(Map.of(TelecmiApiConfig.EXTRA_CALL_CODE, call.getCode()));

        call.setTelecmiCallRequest(request.toRecorded());

        return Mono.just(new TelecmiDial(request, call));
    }

    /** Not judged here, so the caller can record a refusal on the row before reporting it. */
    public Mono<TelecmiResponse> clickToCall(Connection connection, TelecmiClickToCallRequest request) {
        return this.post(connection, TelecmiApiConfig.clickToCallUrl(), request, OPERATION_CLICK_TO_CALL)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiIntegrationsService.clickToCall"));
    }

    /** A success without a {@code request_id} counts as refused: webhooks are keyed on it. */
    public <T> Mono<T> clickToCallRefused(TelecmiResponse response) {
        return this.requestFailed(
                OPERATION_CLICK_TO_CALL,
                response.isSuccess() ? "no request_id in the response" : response.errorDetail());
    }

    /** Parsed numbers only, not {@code PhoneUtil.isSameNumber}, whose last-ten-digits fallback is too lenient. */
    static boolean isOneOf(String number, String... allowed) {
        PhoneNumber asked = PhoneUtil.parse(number);
        if (asked == null) return false;
        for (String candidate : allowed) {
            PhoneNumber parsed = StringUtil.safeIsBlank(candidate) ? null : PhoneUtil.parse(candidate.trim());
            if (parsed != null && asked.getNumber().equals(parsed.getNumber())) return true;
        }
        return false;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (!StringUtil.safeIsBlank(value)) return value.trim();
        return null;
    }

    /**
     * {@code /v2/play} carries the secret in its query string, so errors are drained and replaced, and a failure to
     * reach TeleCMI becomes a 502 naming no URL ({@code WebClientResponseException}'s message includes it).
     * TeleCMI also refuses in JSON bodies, so anything not audio is "not available". {@code Range} support is untested.
     */
    public Mono<ResponseEntity<Flux<DataBuffer>>> fetchRecording(
            Connection connection, String callCode, String file, String range) {

        return this.webClientConfig
                .createTelecmiWebClient(connection)
                .flatMap(client -> client.get()
                        .uri(builder -> builder.path(TelecmiApiConfig.playUrl())
                                .queryParam("appid", this.appId(connection))
                                .queryParam("secret", this.detail(connection, TelecmiApiConfig.SECRET))
                                .queryParam("file", file)
                                .build())
                        .headers(headers -> {
                            if (!StringUtil.safeIsBlank(range)) headers.set(HttpHeaders.RANGE, range);
                        })
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, response -> response.releaseBody()
                                .then(this.recordingUnavailable(callCode)))
                        .toEntityFlux(DataBuffer.class))
                .flatMap(entity ->
                        ICallRecordingService.isAudio(entity.getHeaders().getContentType())
                                ? Mono.just(ICallRecordingService.asPlayable(entity))
                                : entity.getBody()
                                        .doOnNext(DataBufferUtils::release)
                                        .then(this.<ResponseEntity<Flux<DataBuffer>>>recordingUnavailable(callCode)))
                .onErrorResume(
                        e -> !(e instanceof GenericException), e -> this.requestFailed(OPERATION_PLAY, "no answer"))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiIntegrationsService.fetchRecording"));
    }

    public <T> Mono<T> recordingUnavailable(String callCode) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                MessageResourceService.CALL_RECORDING_NOT_AVAILABLE,
                callCode);
    }

    private Mono<CallProviderApp> requireApp(MessageAccess access, Connection connection) {
        return this.callProviderAppDAO
                .findByClient(access.getAppCode(), access.getClientCode(), this.provider())
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        MessageResourceService.CALL_APP_NOT_INITIALIZED,
                        connection.getName()));
    }

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

    /** Refuses to hand one TeleCMI user to two agents: they would ring for each other's calls. */
    private Mono<Boolean> requireIdentityUnclaimed(
            MessageAccess access, ProvisionAgentRequest request, String agentId) {
        return this.providerUserEndpointDAO
                .findByProviderUserId(access.getAppCode(), agentId, this.provider())
                .filter(existing -> !request.getUserId().equals(existing.getUserId()))
                .next()
                .flatMap(clash -> this.msgService.<Boolean>throwMessage(
                        msg -> new GenericException(HttpStatus.CONFLICT, msg),
                        MessageResourceService.EXOTEL_IDENTITY_CLAIMED,
                        agentId,
                        clash.getUserId()))
                .defaultIfEmpty(Boolean.TRUE);
    }

    /** A 403, which the UI reads as "not set up" rather than broken. */
    private Mono<ProviderUserEndpoint> requireEndpoint(MessageAccess access, Connection connection, ULong userId) {
        return this.providerUserEndpointDAO
                .findActiveEndpoints(access.getAppCode(), userId, connection.getName(), this.provider())
                .filter(endpoint -> ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP.equals(endpoint.getEndpointType()))
                .next()
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        MessageResourceService.AGENT_NOT_PROVISIONED,
                        userId,
                        connection.getName()));
    }

    private <T> Mono<T> missingParam(String param) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                MessageResourceService.MISSING_CALL_PARAMETERS,
                this.provider(),
                param);
    }

    private <T> Mono<T> invalidParam(String param, String value) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                MessageResourceService.EXOTEL_INVALID_PHONE_NUMBER,
                param,
                value);
    }

    /** E.164 digits without the {@code +}. */
    static String telecmiPhone(PhoneNumber number) {
        String e164 = number.getNumber();
        return e164.startsWith("+") ? e164.substring(1) : e164;
    }

    static Integer lowestFreeExtension(Set<Integer> taken) {
        for (int extension = TelecmiApiConfig.MIN_EXTENSION; extension <= TelecmiApiConfig.MAX_EXTENSION; extension++)
            if (!taken.contains(extension)) return extension;
        return null;
    }

    static String passwordOf(ProviderUserEndpoint endpoint) {
        Map<String, Object> metadata = endpoint.getProviderMetadata();
        Object password = metadata == null ? null : metadata.get(TelecmiApiConfig.META_PASSWORD);
        return password instanceof String value && !value.isBlank() ? value : null;
    }

    static String newWebhookToken() {
        byte[] bytes = new byte[WEBHOOK_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
