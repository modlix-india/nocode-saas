package com.fincity.saas.message.service.call.provider.telecmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.message.configuration.WebClientConfig;
import com.fincity.saas.message.configuration.call.telecmi.TelecmiApiConfig;
import com.fincity.saas.message.dao.call.CallProviderAppDAO;
import com.fincity.saas.message.dao.call.ProviderUserEndpointDAO;
import com.fincity.saas.message.dto.call.CallProviderApp;
import com.fincity.saas.message.dto.call.ProviderUserEndpoint;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.call.ProvisionAgentRequest;
import com.fincity.saas.message.model.response.call.BrowserCallStatus;
import com.fincity.saas.message.model.response.call.CallAppStatus;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.service.MessageResourceService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Setup, agent provisioning, teardown and browser status against TeleCMI, stubbed at the HTTP layer.
 *
 * <p>The responses are the bodies TeleCMI returned on the test account, identifiers replaced. Each
 * test says what TeleCMI answers and checks both what we sent and what we stored — the two things
 * a real provisioning run could get wrong without failing: the wrong password reaching TeleCMI, and
 * the wrong one reaching the row the softphone logs in from.
 */
class TelecmiProvisioningTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String AGENT = "1001_1111112";
    private static final String AGENT_NUMBER = "+919000000001";
    private static final ULong USER = ULong.valueOf(7);

    private static final String LISTING =
            "{\"code\":200,\"status\":\"success\",\"count\":1,\"agents\":[{\"agent_id\":\"1000_1111112\",\"extension\":1000}]}";
    private static final String ADDED =
            "{\"code\":200,\"status\":\"success\",\"msg\":\"Saved successfully\",\"agent\":{\"agent_id\":\"1001_1111112\",\"extension\":1001}}";
    private static final String EXTENSION_TAKEN =
            "{\"code\":400,\"status\":\"error\",\"msg\":\"Extension Already Exists !\"}";
    private static final String EMAIL_TAKEN = "{\"code\":400,\"status\":\"error\",\"msg\":\"Email Already Exists !\"}";
    private static final String SAVED = "{\"code\":200,\"status\":\"success\",\"msg\":\"Saved successfully\"}";
    private static final String NO_CHANGES =
            "{\"code\":404,\"status\":\"error\",\"msg\":\"Agent not found or no changes made\"}";
    private static final String REMOVED = "{\"code\":200,\"status\":\"success\"}";

    private static String agentBody(String password, String phone, boolean followme) {
        return "{\"code\":200,\"status\":\"success\",\"agent\":{\"agent_id\":\"" + AGENT + "\",\"extension\":1001,"
                + "\"password\":\"" + password + "\",\"phone\":\"" + phone + "\",\"followme\":" + followme + "}}";
    }

    /** TeleCMI, one queue of answers per path, recording every request it receives. */
    private final Map<String, Deque<ClientResponse>> answers = new HashMap<>();

    private final List<String> paths = new ArrayList<>();
    private final List<JsonNode> bodies = new ArrayList<>();

    private CallProviderAppDAO apps;
    private ProviderUserEndpointDAO endpoints;
    private IFeignSecurityService security;
    private TelecmiIntegrationsService service;

    private Connection connection;
    private final MessageAccess access = MessageAccess.of("app", "CLIENT", Boolean.TRUE);

    private void answer(String path, HttpStatus status, String json) {
        this.answers
                .computeIfAbsent(path, p -> new ArrayDeque<>())
                .add(ClientResponse.create(status)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(json)
                        .build());
    }

    private void answer(String path, String json) {
        this.answer(path, HttpStatus.OK, json);
    }

    private JsonNode sent(String path) {
        int index = this.paths.lastIndexOf(path);
        return index < 0 ? null : this.bodies.get(index);
    }

    @BeforeEach
    void setUp() {

        WebClient telecmi = WebClient.builder()
                .baseUrl(TelecmiApiConfig.DEFAULT_REST_BASE)
                .exchangeFunction(request -> {
                    MockClientHttpRequest captured = new MockClientHttpRequest(request.method(), request.url());
                    return request.writeTo(captured, ExchangeStrategies.withDefaults())
                            .then(Mono.defer(captured::getBodyAsString))
                            .map(body -> {
                                String path = request.url().getPath();
                                this.paths.add(path);
                                try {
                                    this.bodies.add(MAPPER.readTree(body));
                                } catch (Exception e) {
                                    throw new IllegalStateException(e);
                                }
                                Deque<ClientResponse> queue = this.answers.get(path);
                                if (queue == null || queue.isEmpty())
                                    throw new AssertionError("Unexpected call to TeleCMI " + path);
                                return queue.poll();
                            });
                })
                .build();

        WebClientConfig clients = mock(WebClientConfig.class);
        when(clients.createTelecmiWebClient(any())).thenReturn(Mono.just(telecmi));

        this.apps = mock(CallProviderAppDAO.class);
        when(this.apps.findByClient(any(), any(), any())).thenReturn(Mono.just(new CallProviderApp()));

        this.endpoints = mock(ProviderUserEndpointDAO.class);
        when(this.endpoints.findEndpoint(any(), any(), any(), any())).thenReturn(Mono.empty());
        when(this.endpoints.findByProviderUserId(any(), any(), any())).thenReturn(Flux.empty());
        when(this.endpoints.findByConnection(any(), any(), any())).thenReturn(Flux.empty());
        when(this.endpoints.upsert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        this.security = mock(IFeignSecurityService.class);
        when(this.security.isUserPartOfHierarchy(any(), any())).thenReturn(Mono.just(Boolean.TRUE));
        User user = new User();
        user.setEmailId("agent@example.com");
        user.setFirstName("Test");
        user.setLastName("Agent");
        when(this.security.getUserInternal(any(), any())).thenReturn(Mono.just(user));

        // Every refusal surfaces as the GenericException its guard builds, message = the key.
        MessageResourceService messages = mock(MessageResourceService.class, invocation -> {
            if (!"throwMessage".equals(invocation.getMethod().getName())) return null;
            @SuppressWarnings("unchecked")
            Function<String, GenericException> error = invocation.getArgument(0);
            return Mono.error(error.apply(invocation.getArgument(1)));
        });

        this.service = new TelecmiIntegrationsService(clients, this.apps, this.endpoints, this.security, messages);

        this.connection = new Connection()
                .setConnectionDetails(Map.of(TelecmiApiConfig.APP_ID, "1111112", TelecmiApiConfig.SECRET, "s"));
        this.connection.setName("calls");
    }

    private ProvisionAgentRequest request(String password) {
        ProvisionAgentRequest request = new ProvisionAgentRequest()
                .setAgentNumber(AGENT_NUMBER)
                .setVirtualNumber("+919000000002")
                .setPassword(password);
        request.setUserId(USER);
        return request;
    }

    private GenericException failure(Mono<?> call) {
        return assertThrows(GenericException.class, call::block);
    }

    private List<ProviderUserEndpoint> stored() {
        ArgumentCaptor<ProviderUserEndpoint> rows = ArgumentCaptor.forClass(ProviderUserEndpoint.class);
        verify(this.endpoints, org.mockito.Mockito.atLeastOnce()).upsert(rows.capture());
        return rows.getAllValues();
    }

    private static String passwordOn(ProviderUserEndpoint sip) {
        return (String) sip.getProviderMetadata().get(TelecmiApiConfig.META_PASSWORD);
    }

    // ---------------------------------------------------------------------------------------
    // New agents
    // ---------------------------------------------------------------------------------------

    @Test
    void aNewAgentGetsTheLowestFreeExtensionWithTheRequestedPassword() {

        this.answer(TelecmiApiConfig.userAllUrl(), LISTING);
        this.answer(TelecmiApiConfig.userAddUrl(), ADDED);

        var agent = this.service
                .provisionAgent(this.access, this.connection, this.request("chosen-pass"))
                .block();

        JsonNode add = this.sent(TelecmiApiConfig.userAddUrl());
        assertEquals(1001, add.get("extension").asInt());
        assertEquals("agent@example.com", add.get("email_id").asText());
        assertEquals("919000000001", add.get("phone_number").asText());
        assertEquals("chosen-pass", add.get("password").asText());
        assertTrue(add.get("followme").asBoolean());
        assertEquals(1111112L, add.get("appid").asLong());

        assertEquals(AGENT, agent.getProviderUserId());
        ProviderUserEndpoint sip = this.stored().get(0);
        assertEquals(ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP, sip.getEndpointType());
        assertEquals("chosen-pass", passwordOn(sip));
    }

    @Test
    void aNewAgentWithoutAPasswordIsRefusedBeforeTelecmiIsAsked() {

        GenericException refused =
                this.failure(this.service.provisionAgent(this.access, this.connection, this.request(null)));

        assertEquals(MessageResourceService.MISSING_CALL_PARAMETERS, refused.getMessage());
        assertTrue(this.paths.isEmpty());
    }

    @Test
    void aShortPasswordIsRefusedBeforeTelecmiIsAsked() {

        GenericException refused =
                this.failure(this.service.provisionAgent(this.access, this.connection, this.request("short")));

        assertEquals(MessageResourceService.TELECMI_PASSWORD_TOO_SHORT, refused.getMessage());
        assertTrue(this.paths.isEmpty());
    }

    @Test
    void aTakenExtensionStepsToTheNextOne() {

        this.answer(TelecmiApiConfig.userAllUrl(), LISTING);
        this.answer(TelecmiApiConfig.userAddUrl(), EXTENSION_TAKEN);
        this.answer(TelecmiApiConfig.userAddUrl(), ADDED);

        this.service
                .provisionAgent(this.access, this.connection, this.request("chosen-pass"))
                .block();

        assertEquals(
                1002, this.sent(TelecmiApiConfig.userAddUrl()).get("extension").asInt());
    }

    @Test
    void repeatedlyTakenExtensionsReportTelecmisOwnRefusal() {

        this.answer(TelecmiApiConfig.userAllUrl(), LISTING);
        this.answer(TelecmiApiConfig.userAddUrl(), EXTENSION_TAKEN);
        this.answer(TelecmiApiConfig.userAddUrl(), EXTENSION_TAKEN);
        this.answer(TelecmiApiConfig.userAddUrl(), EXTENSION_TAKEN);

        GenericException refused =
                this.failure(this.service.provisionAgent(this.access, this.connection, this.request("chosen-pass")));

        assertEquals(MessageResourceService.TELECMI_REQUEST_FAILED, refused.getMessage());
        assertEquals(HttpStatus.BAD_GATEWAY.value(), refused.getStatusCode().value());
    }

    @Test
    void aTakenEmailIsAConflictThatNamesAppUserIdAndAdoptsNothing() {

        this.answer(TelecmiApiConfig.userAllUrl(), LISTING);
        this.answer(TelecmiApiConfig.userAddUrl(), EMAIL_TAKEN);

        GenericException refused =
                this.failure(this.service.provisionAgent(this.access, this.connection, this.request("chosen-pass")));

        assertEquals(MessageResourceService.TELECMI_EMAIL_CLAIMED, refused.getMessage());
        assertEquals(HttpStatus.CONFLICT.value(), refused.getStatusCode().value());
        assertFalse(this.paths.contains(TelecmiApiConfig.userUpdateUrl()));
        verify(this.endpoints, never()).upsert(any());
    }

    // ---------------------------------------------------------------------------------------
    // Adoption, only through appUserId
    // ---------------------------------------------------------------------------------------

    @Test
    void appUserIdAdoptsThatUserWithTheRequestedPassword() {

        this.answer(TelecmiApiConfig.userGetUrl(), agentBody("their-old-pass", "919000000009", false));
        this.answer(TelecmiApiConfig.userUpdateUrl(), SAVED);

        ProvisionAgentRequest request = this.request("chosen-pass").setAppUserId(AGENT);
        this.service.provisionAgent(this.access, this.connection, request).block();

        JsonNode update = this.sent(TelecmiApiConfig.userUpdateUrl());
        assertEquals(AGENT, update.get("agent_id").asText());
        assertEquals("chosen-pass", update.get("password").asText());
        assertEquals("919000000001", update.get("phone_number").asText());
        assertTrue(update.get("followme").asBoolean());
        assertFalse(this.paths.contains(TelecmiApiConfig.userAddUrl()));
        assertEquals("chosen-pass", passwordOn(this.stored().get(0)));
    }

    @Test
    void adoptionNeedsAPassword() {

        GenericException refused = this.failure(this.service.provisionAgent(
                this.access, this.connection, this.request(null).setAppUserId(AGENT)));

        assertEquals(MessageResourceService.MISSING_CALL_PARAMETERS, refused.getMessage());
        assertTrue(this.paths.isEmpty());
    }

    @Test
    void aUserHeldByAnotherAgentIsNotTouched() {

        ProviderUserEndpoint someoneElse = new ProviderUserEndpoint().setProviderUserId(AGENT);
        someoneElse.setUserId(ULong.valueOf(99));
        when(this.endpoints.findByProviderUserId(any(), any(), any())).thenReturn(Flux.just(someoneElse));

        this.answer(TelecmiApiConfig.userGetUrl(), agentBody("their-pass", "919000000001", true));

        GenericException refused = this.failure(this.service.provisionAgent(
                this.access, this.connection, this.request("chosen-pass").setAppUserId(AGENT)));

        assertEquals(MessageResourceService.EXOTEL_IDENTITY_CLAIMED, refused.getMessage());
        assertFalse(this.paths.contains(TelecmiApiConfig.userUpdateUrl()));
    }

    @Test
    void noMatchOnNumberAloneEverAdopts() {

        // A TeleCMI user with the agent's own number exists; without appUserId it is left alone and
        // a new user is made.
        this.answer(
                TelecmiApiConfig.userAllUrl(),
                "{\"code\":200,\"status\":\"success\",\"agents\":[{\"agent_id\":\"5001_1111112\",\"extension\":5001,\"phone\":\"919000000001\"}]}");
        this.answer(TelecmiApiConfig.userAddUrl(), ADDED);

        this.service
                .provisionAgent(this.access, this.connection, this.request("chosen-pass"))
                .block();

        assertFalse(this.paths.contains(TelecmiApiConfig.userUpdateUrl()));
        assertEquals(
                1000, this.sent(TelecmiApiConfig.userAddUrl()).get("extension").asInt());
    }

    // ---------------------------------------------------------------------------------------
    // Re-provisioning an agent who already has a user
    // ---------------------------------------------------------------------------------------

    private void alreadyProvisioned(String storedPassword) {
        ProviderUserEndpoint sip = new ProviderUserEndpoint()
                .setEndpointType(ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP)
                .setProviderUserId(AGENT)
                .setProviderMetadata(Map.of(TelecmiApiConfig.META_PASSWORD, storedPassword));
        when(this.endpoints.findEndpoint(any(), any(), any(), any())).thenReturn(Mono.just(sip));
    }

    @Test
    void aPasswordChangedAtTelecmiIsCopiedBackWithoutAReset() {

        this.alreadyProvisioned("stale-pass");
        this.answer(TelecmiApiConfig.userGetUrl(), agentBody("live-pass", "919000000001", true));

        this.service
                .provisionAgent(this.access, this.connection, this.request(null))
                .block();

        assertFalse(this.paths.contains(TelecmiApiConfig.userUpdateUrl()));
        assertEquals("live-pass", passwordOn(this.stored().get(0)));
    }

    @Test
    void aPasswordInTheRequestReplacesTheOneAtTelecmi() {

        this.alreadyProvisioned("live-pass");
        this.answer(TelecmiApiConfig.userGetUrl(), agentBody("live-pass", "919000000001", true));
        this.answer(TelecmiApiConfig.userUpdateUrl(), SAVED);

        this.service
                .provisionAgent(this.access, this.connection, this.request("rotated-pass"))
                .block();

        JsonNode update = this.sent(TelecmiApiConfig.userUpdateUrl());
        assertEquals("rotated-pass", update.get("password").asText());
        assertNull(update.get("phone_number"));
        assertNull(update.get("followme"));
        assertEquals("rotated-pass", passwordOn(this.stored().get(0)));
    }

    @Test
    void anUpdateTelecmiCallsUnchangedIsDone() {

        this.alreadyProvisioned("live-pass");
        this.answer(TelecmiApiConfig.userGetUrl(), agentBody("live-pass", "919000000001", false));
        this.answer(TelecmiApiConfig.userUpdateUrl(), HttpStatus.NOT_FOUND, NO_CHANGES);
        this.answer(TelecmiApiConfig.userGetUrl(), agentBody("live-pass", "919000000001", true));

        var agent = this.service
                .provisionAgent(this.access, this.connection, this.request(null))
                .block();

        assertEquals(AGENT, agent.getProviderUserId());
    }

    @Test
    void anAgentWhoseUserWasDeletedAtTelecmiGetsANewOne() {

        this.alreadyProvisioned("live-pass");
        this.answer(TelecmiApiConfig.userGetUrl(), HttpStatus.NOT_FOUND, "{\"code\":404,\"status\":\"error\"}");
        this.answer(TelecmiApiConfig.userAllUrl(), LISTING);
        this.answer(TelecmiApiConfig.userAddUrl(), ADDED);

        this.service
                .provisionAgent(this.access, this.connection, this.request("chosen-pass"))
                .block();

        assertEquals(
                "chosen-pass",
                this.sent(TelecmiApiConfig.userAddUrl()).get("password").asText());
    }

    // ---------------------------------------------------------------------------------------
    // Deactivation and setup
    // ---------------------------------------------------------------------------------------

    private void activeEndpoint() {
        ProviderUserEndpoint sip = new ProviderUserEndpoint()
                .setEndpointType(ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP)
                .setProviderUserId(AGENT);
        when(this.endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.just(sip));
        when(this.endpoints.deactivate(any(), any(), any())).thenReturn(Mono.just(2));
    }

    @Test
    void deactivationDeletesTheUserAtTelecmiAndLocally() {

        this.activeEndpoint();
        this.answer(TelecmiApiConfig.userRemoveUrl(), REMOVED);

        assertEquals(
                2,
                this.service.deactivateAgent(this.access, this.connection, USER).block());
        assertEquals(
                AGENT,
                this.sent(TelecmiApiConfig.userRemoveUrl()).get("agent_id").asText());
        verify(this.endpoints).deactivate("app", USER, "calls");
    }

    @Test
    void aUserAlreadyGoneAtTelecmiStillDeactivatesLocally() {

        this.activeEndpoint();
        this.answer(TelecmiApiConfig.userRemoveUrl(), HttpStatus.NOT_FOUND, "{\"code\":404,\"status\":\"error\"}");
        this.answer(TelecmiApiConfig.userGetUrl(), HttpStatus.NOT_FOUND, "{\"code\":404,\"status\":\"error\"}");

        assertEquals(
                2,
                this.service.deactivateAgent(this.access, this.connection, USER).block());
    }

    @Test
    void aRemovalTelecmiRefusesKeepsTheRowsActive() {

        this.activeEndpoint();
        this.answer(
                TelecmiApiConfig.userRemoveUrl(),
                "{\"code\":500,\"status\":\"error\",\"msg\":\"Sorry, something goes wrong !\"}");
        this.answer(TelecmiApiConfig.userGetUrl(), agentBody("live-pass", "919000000001", true));

        GenericException refused = this.failure(this.service.deactivateAgent(this.access, this.connection, USER));

        assertEquals(MessageResourceService.TELECMI_REQUEST_FAILED, refused.getMessage());
        verify(this.endpoints, never()).deactivate(any(), any(), any());
    }

    @Test
    void aNonNumericAppIdIsInvalidAndNeverSent() {

        this.connection.setConnectionDetails(Map.of(TelecmiApiConfig.APP_ID, "abc", TelecmiApiConfig.SECRET, "s"));

        GenericException refused = this.failure(this.service.initializeApp(this.access, this.connection));

        assertEquals(MessageResourceService.INVALID_CONNECTION_DETAILS, refused.getMessage());
        assertTrue(this.paths.isEmpty());
    }

    // ---------------------------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------------------------

    private static final String BALANCE = "{\"code\":200,\"expire\":1790620199999,\"sms\":0,\"balance\":0}";

    @Test
    void theFirstSetupStoresTheCredentialsAndOnlyTheTokensHash() {

        when(this.apps.findByClient(any(), any(), any())).thenReturn(Mono.empty());
        when(this.apps.create(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        this.answer(TelecmiApiConfig.balanceUrl(), BALANCE);

        CallAppStatus status =
                this.service.initializeApp(this.access, this.connection).block();

        ArgumentCaptor<CallProviderApp> created = ArgumentCaptor.forClass(CallProviderApp.class);
        verify(this.apps).create(created.capture());
        CallProviderApp row = created.getValue();

        assertEquals("TELECMI", row.getProvider());
        assertEquals("1111112", row.getProviderAppId());
        assertEquals("s", row.getProviderAppSecret());
        assertEquals("1111112", row.getAccountSid());
        assertEquals("CLIENT", row.getClientCode());

        assertTrue(status.isInitialized());
        assertEquals(0.0, status.getBalance());
        assertEquals(
                TelecmiIntegrationsService.sha256Hex(status.getWebhookToken()),
                row.getProviderMetadata().get(TelecmiApiConfig.META_WEBHOOK_TOKEN_HASH));
        assertFalse(row.getProviderMetadata().containsValue(status.getWebhookToken()));

        JsonNode check = this.sent(TelecmiApiConfig.balanceUrl());
        assertEquals(1111112L, check.get("appid").asLong());
        assertEquals("s", check.get("secret").asText());
    }

    @Test
    void setupReportsTheWebhookUrlExactlyAsTheConnectionStatesIt() {

        this.connection.setConnectionDetails(Map.of(
                TelecmiApiConfig.APP_ID, "1111112",
                TelecmiApiConfig.SECRET, "s",
                TelecmiApiConfig.CALLBACK_URL,
                        " https://tenant.example/app/CLIENT/page/api/message/call/callback/telecmi "));
        when(this.apps.findByClient(any(), any(), any())).thenReturn(Mono.empty());
        when(this.apps.create(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        this.answer(TelecmiApiConfig.balanceUrl(), BALANCE);

        CallAppStatus status =
                this.service.initializeApp(this.access, this.connection).block();

        assertEquals(
                "https://tenant.example/app/CLIENT/page/api/message/call/callback/telecmi", status.getCallbackUrl());
    }

    @Test
    void setupReportsTheInboundFlowUrlAsTheConnectionStatesIt() {

        String flowUrl = "https://tenant.example/app/CLIENT/page/api/entity/processor/open/call/telecmi?t=tok";
        this.connection.setConnectionDetails(Map.of(
                TelecmiApiConfig.APP_ID, "1111112",
                TelecmiApiConfig.SECRET, "s",
                TelecmiApiConfig.HTTP_FLOW_URL, flowUrl));
        when(this.apps.findByClient(any(), any(), any())).thenReturn(Mono.empty());
        when(this.apps.create(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        this.answer(TelecmiApiConfig.balanceUrl(), BALANCE);

        CallAppStatus status =
                this.service.initializeApp(this.access, this.connection).block();

        assertEquals(flowUrl, status.getHttpFlowUrl());
    }

    @Test
    void aRerunTakesTheConnectionsWebhookUrlAgainAndNoneWhenItIsGone() {

        CallProviderApp existing = this.setUpOn("1111112").setCallbackUrl("https://old.example/callback/telecmi");
        this.answer(TelecmiApiConfig.balanceUrl(), BALANCE);

        this.service.initializeApp(this.access, this.connection).block();

        assertNull(existing.getCallbackUrl());
    }

    @Test
    void aRerunRefreshesTheSecretKeepsTheHashAndShowsNoToken() {

        CallProviderApp existing = new CallProviderApp()
                .setProviderAppId("1111112")
                .setProviderAppSecret("old-secret")
                .setProviderMetadata(Map.of(TelecmiApiConfig.META_WEBHOOK_TOKEN_HASH, "kept-hash"));
        when(this.apps.findByClient(any(), any(), any())).thenReturn(Mono.just(existing));
        when(this.apps.update(any(CallProviderApp.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        this.answer(TelecmiApiConfig.balanceUrl(), BALANCE);

        CallAppStatus status =
                this.service.initializeApp(this.access, this.connection).block();

        ArgumentCaptor<CallProviderApp> updated = ArgumentCaptor.forClass(CallProviderApp.class);
        verify(this.apps).update(updated.capture());
        assertEquals("s", updated.getValue().getProviderAppSecret());
        assertEquals(
                "kept-hash", updated.getValue().getProviderMetadata().get(TelecmiApiConfig.META_WEBHOOK_TOKEN_HASH));
        assertEquals(1790620199999L, updated.getValue().getProviderMetadata().get(TelecmiApiConfig.META_EXPIRE));
        assertNull(status.getWebhookToken());
        verify(this.apps, never()).create(any());
    }

    private CallProviderApp setUpOn(String appId) {
        CallProviderApp existing = new CallProviderApp()
                .setConnectionName("calls")
                .setProviderAppId(appId)
                .setProviderAppSecret("old-secret")
                .setProviderMetadata(Map.of(TelecmiApiConfig.META_WEBHOOK_TOKEN_HASH, "kept-hash"));
        when(this.apps.findByClient(any(), any(), any())).thenReturn(Mono.just(existing));
        when(this.apps.update(any(CallProviderApp.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        return existing;
    }

    @Test
    void anotherTelecmiAccountIsRefusedWhileAgentsAreOnTheFirst() {

        this.setUpOn("9999999");
        ProviderUserEndpoint active = new ProviderUserEndpoint().setProviderUserId("1001_9999999");
        active.setUserId(USER);
        when(this.endpoints.findByConnection(any(), any(), any())).thenReturn(Flux.just(active));

        GenericException refused = this.failure(this.service.initializeApp(this.access, this.connection));

        assertEquals(MessageResourceService.TELECMI_APP_CHANGED, refused.getMessage());
        assertEquals(HttpStatus.CONFLICT.value(), refused.getStatusCode().value());
        // Refused before TeleCMI is asked, and nothing rewritten.
        assertTrue(this.paths.isEmpty());
        verify(this.apps, never()).update(any(CallProviderApp.class));
        verify(this.endpoints).findByConnection("app", "CLIENT", "calls");
    }

    @Test
    void anotherTelecmiAccountIsTakenOnceNoAgentIsLeftOnTheFirst() {

        this.setUpOn("9999999");
        ProviderUserEndpoint retired = new ProviderUserEndpoint().setProviderUserId("1001_9999999");
        retired.setUserId(USER);
        retired.setActive(false);
        when(this.endpoints.findByConnection(any(), any(), any())).thenReturn(Flux.just(retired));
        this.answer(TelecmiApiConfig.balanceUrl(), BALANCE);

        this.service.initializeApp(this.access, this.connection).block();

        ArgumentCaptor<CallProviderApp> updated = ArgumentCaptor.forClass(CallProviderApp.class);
        verify(this.apps).update(updated.capture());
        assertEquals("1111112", updated.getValue().getProviderAppId());
        assertEquals("1111112", updated.getValue().getAccountSid());
        assertEquals("s", updated.getValue().getProviderAppSecret());
    }

    @Test
    void theSameAccountNeverChecksForAgents() {

        this.setUpOn("1111112");
        this.answer(TelecmiApiConfig.balanceUrl(), BALANCE);

        this.service.initializeApp(this.access, this.connection).block();

        verify(this.endpoints, never()).findByConnection(any(), any(), any());
        verify(this.apps).update(any(CallProviderApp.class));
    }

    @Test
    void credentialsTelecmiRejectsWriteNothing() {

        this.answer(
                TelecmiApiConfig.balanceUrl(),
                HttpStatus.NOT_FOUND,
                "{\"code\":404,\"msg\":\"Invalid app id or secret\"}");

        GenericException refused = this.failure(this.service.initializeApp(this.access, this.connection));

        assertEquals(MessageResourceService.TELECMI_REQUEST_FAILED, refused.getMessage());
        verify(this.apps, never()).create(any());
        verify(this.apps, never()).update(any(CallProviderApp.class));
    }

    @Test
    void aConnectionWithoutASecretIsRefusedBeforeTelecmiIsAsked() {

        this.connection.setConnectionDetails(Map.of(TelecmiApiConfig.APP_ID, "1111112"));

        GenericException refused = this.failure(this.service.initializeApp(this.access, this.connection));

        assertEquals(MessageResourceService.MISSING_CONNECTION_DETAILS, refused.getMessage());
        assertTrue(this.paths.isEmpty());
    }

    // ---------------------------------------------------------------------------------------
    // Teardown
    // ---------------------------------------------------------------------------------------

    @Test
    void teardownRefusesWhileAnAgentIsProvisioned() {

        ProviderUserEndpoint active = new ProviderUserEndpoint().setProviderUserId(AGENT);
        active.setUserId(USER);
        when(this.endpoints.findByConnection(any(), any(), any())).thenReturn(Flux.just(active));

        GenericException refused = this.failure(this.service.teardownApp(this.access, this.connection));

        assertEquals(MessageResourceService.TELECMI_AGENTS_STILL_PROVISIONED, refused.getMessage());
        assertEquals(HttpStatus.CONFLICT.value(), refused.getStatusCode().value());
        verify(this.apps, never()).purgeByClient(any(), any(), any());
        assertTrue(this.paths.isEmpty());
    }

    @Test
    void teardownWithNoActiveAgentsForgetsTheSetupLocallyOnly() {

        ProviderUserEndpoint retired = new ProviderUserEndpoint().setProviderUserId(AGENT);
        retired.setUserId(USER);
        retired.setActive(false);
        when(this.endpoints.findByConnection(any(), any(), any())).thenReturn(Flux.just(retired));
        when(this.endpoints.purgeByConnection(any(), any(), any())).thenReturn(Mono.just(2));
        when(this.apps.purgeByClient(any(), any(), any())).thenReturn(Mono.just(1));

        assertTrue(this.service.teardownApp(this.access, this.connection).block());

        verify(this.endpoints).purgeByConnection("app", "CLIENT", "calls");
        verify(this.apps).purgeByClient("app", "CLIENT", "telecmi");
        assertTrue(this.paths.isEmpty());
    }

    // ---------------------------------------------------------------------------------------
    // Browser status
    // ---------------------------------------------------------------------------------------

    private void softphoneEndpoint() {
        ProviderUserEndpoint sip = new ProviderUserEndpoint()
                .setEndpointType(ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP)
                .setProviderUserId(AGENT)
                .setVirtualNumber("+919000000002");
        when(this.endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.just(sip));
    }

    @Test
    void theCheapStatusNeverAsksTelecmi() {

        this.softphoneEndpoint();

        BrowserCallStatus status = this.service
                .browserCallStatus(this.access, this.connection, USER, false)
                .block();

        assertTrue(status.isProvisioned());
        assertFalse(status.isDialReadyChecked());
        assertTrue(this.paths.isEmpty());
    }

    @Test
    void aVerifiedStatusConfirmsTheUserAtTelecmi() {

        this.softphoneEndpoint();
        this.answer(TelecmiApiConfig.userGetUrl(), agentBody("live-pass", "919000000001", true));

        BrowserCallStatus status = this.service
                .browserCallStatus(this.access, this.connection, USER, true)
                .block();

        assertTrue(status.isProvisioned());
        assertTrue(status.isDialReadyChecked());
    }

    @Test
    void aUserDeletedAtTelecmiIsNotProvisioned() {

        this.softphoneEndpoint();
        this.answer(TelecmiApiConfig.userGetUrl(), HttpStatus.NOT_FOUND, "{\"code\":404,\"status\":\"error\"}");

        BrowserCallStatus status = this.service
                .browserCallStatus(this.access, this.connection, USER, true)
                .block();

        assertFalse(status.isProvisioned());
        assertTrue(status.isDialReadyChecked());
    }

    @Test
    void aTelecmiFailureFallsBackToOurOwnAnswer() {

        this.softphoneEndpoint();
        this.answer(
                TelecmiApiConfig.userGetUrl(),
                "{\"code\":500,\"status\":\"error\",\"msg\":\"Sorry, something goes wrong !\"}");

        BrowserCallStatus status = this.service
                .browserCallStatus(this.access, this.connection, USER, true)
                .block();

        assertTrue(status.isProvisioned());
        assertFalse(status.isDialReadyChecked());
    }

    @Test
    void anAgentWithNoSoftphoneIsNotProvisioned() {

        when(this.endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.empty());

        BrowserCallStatus status = this.service
                .browserCallStatus(this.access, this.connection, USER, true)
                .block();

        assertFalse(status.isProvisioned());
        assertEquals("telecmi", status.getProvider());
    }

    // ---------------------------------------------------------------------------------------
    // Logging
    // ---------------------------------------------------------------------------------------

    @Test
    void aResolvedAgentNeverPrintsItsPassword() {

        String printed = new TelecmiIntegrationsService.ResolvedAgent(AGENT, "chosen-pass").toString();

        assertTrue(printed.contains(AGENT));
        assertFalse(printed.contains("chosen-pass"));
    }
}
