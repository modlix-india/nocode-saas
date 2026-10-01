package com.fincity.saas.message.service.call.provider.telecmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.message.configuration.call.telecmi.TelecmiApiConfig;
import com.fincity.saas.message.dao.call.CallProviderAppDAO;
import com.fincity.saas.message.dao.call.ProviderUserEndpointDAO;
import com.fincity.saas.message.dto.call.CallProviderApp;
import com.fincity.saas.message.dto.call.ProviderUserEndpoint;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.call.provider.telecmi.TelecmiUserRequest;
import com.fincity.saas.message.model.response.call.BrowserCallToken;
import com.fincity.saas.message.model.response.call.CallAppStatus;
import com.fincity.saas.message.model.response.call.provider.telecmi.TelecmiResponse;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.util.PhoneUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jooq.types.ULong;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * TeleCMI's contract as the live tests found it, and the rules provisioning builds on it.
 *
 * <p>The response bodies are the ones TeleCMI returned on the test account (2026-09-25/28), with
 * identifiers replaced. Two of them are the reason this class exists: a refused add arrives as
 * {@code code: 400} inside an HTTP 200, and an update that changes nothing is a 404 — so success
 * must be read from the body, and "not found" must not be taken at its word on update.
 */
class TelecmiIntegrationsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static TelecmiResponse read(String json) throws Exception {
        return MAPPER.readValue(json, TelecmiResponse.class);
    }

    @Test
    void aSuccessfulAddCarriesTheNewAgentId() throws Exception {

        TelecmiResponse add = read("{\"code\":200,\"status\":\"success\",\"msg\":\"Saved successfully\","
                + "\"agent\":{\"agent_id\":\"1001_1111112\",\"extension\":1001}}");

        assertTrue(add.isSuccess());
        assertEquals("1001_1111112", add.getAgent().getAgentId());
    }

    @Test
    void aDuplicateExtensionIsARefusalInsideA200() throws Exception {

        TelecmiResponse duplicate = read("{\"code\":400,\"status\":\"error\",\"msg\":\"Extension Already Exists !\"}");

        assertFalse(duplicate.isSuccess());
        assertTrue(duplicate.refused(TelecmiApiConfig.MSG_EXTENSION_EXISTS));
        assertFalse(duplicate.refused(TelecmiApiConfig.MSG_EMAIL_EXISTS));
    }

    @Test
    void anUpdateThatChangesNothingReadsAsNotFound() throws Exception {

        TelecmiResponse noChange =
                read("{\"code\":404,\"status\":\"error\",\"msg\":\"Agent not found or no changes made\"}");

        assertFalse(noChange.isSuccess());
        assertTrue(noChange.isNotFound());
    }

    @Test
    void theListingReturnsPasswordsAndBothPhoneForms() throws Exception {

        TelecmiResponse all = read("{\"code\":200,\"status\":\"success\",\"count\":2,\"agents\":["
                + "{\"agent_id\":\"1001_1111112\",\"extension\":1001,\"password\":\"p\",\"phone\":\"919000000001\"},"
                + "{\"agent_id\":\"1002_1111112\",\"extension\":1002,\"phone\":\"9000000002\",\"followme\":false}]}");

        assertTrue(all.isSuccess());
        assertEquals(2, all.getAgents().size());
        assertEquals("p", all.getAgents().get(0).getPassword());
        assertFalse(all.getAgents().get(1).getFollowme());
        // Matching must survive the missing country code, which is why it goes through isSameNumber.
        assertTrue(PhoneUtil.isSameNumber(all.getAgents().get(1).getPhone(), "919000000002"));
    }

    @Test
    void theBalanceCheckProvesCredentialsWhateverTheBalance() throws Exception {

        // Zero balance is still a valid app: the dashboard showed funds while this read 0.
        TelecmiResponse balance = read("{\"code\":200,\"expire\":1790620199999,\"sms\":0,\"balance\":0}");

        assertTrue(balance.isSuccess());
        assertEquals(0.0, balance.getBalance());
        assertEquals(1790620199999L, balance.getExpire());
    }

    @Test
    void requestsUseTelecmisNamesAndOmitWhatIsUnset() throws Exception {

        String update = MAPPER.writeValueAsString(
                TelecmiUserRequest.ofAgent(1111112L, "s", "1001_1111112").setFollowme(Boolean.TRUE));

        assertEquals(
                MAPPER.readTree("{\"appid\":1111112,\"secret\":\"s\",\"agent_id\":\"1001_1111112\",\"followme\":true}"),
                MAPPER.readTree(update));
    }

    @Test
    void extensionsStartAtTheBottomOfTheV3RangeAndSkipTakenOnes() {

        assertEquals(1000, TelecmiIntegrationsService.lowestFreeExtension(Set.of()));
        assertEquals(1002, TelecmiIntegrationsService.lowestFreeExtension(Set.of(1000, 1001, 5001)));
        assertEquals(1000, TelecmiIntegrationsService.lowestFreeExtension(Set.of(502, 5001)));

        Set<Integer> full = IntStream.rangeClosed(TelecmiApiConfig.MIN_EXTENSION, TelecmiApiConfig.MAX_EXTENSION)
                .boxed()
                .collect(Collectors.toCollection(HashSet::new));
        assertNull(TelecmiIntegrationsService.lowestFreeExtension(full));
    }

    @Test
    void phoneNumbersGoToTelecmiWithoutThePlus() {
        assertEquals("919000000001", TelecmiIntegrationsService.telecmiPhone(PhoneUtil.parse("+919000000001")));
    }

    @Test
    void webhookTokensAreUnique() {
        assertNotEquals(TelecmiIntegrationsService.newWebhookToken(), TelecmiIntegrationsService.newWebhookToken());
    }

    @Test
    void theWebhookTokenIsStoredAsItsSha256() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                TelecmiIntegrationsService.sha256Hex("abc"));
    }

    @Test
    void theStatusReportsBalanceAndExpiryButNeverTheTokenHash() {

        TelecmiIntegrationsService service = new TelecmiIntegrationsService(null, null, null, null, null);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(TelecmiApiConfig.META_BALANCE, 0);
        metadata.put(TelecmiApiConfig.META_EXPIRE, 1790620199999L);
        metadata.put(TelecmiApiConfig.META_WEBHOOK_TOKEN_HASH, "hash");

        CallAppStatus status = service.appStatusOf(new CallProviderApp().setProviderMetadata(metadata));

        assertTrue(status.isInitialized());
        assertEquals("telecmi", status.getProvider());
        assertEquals(0.0, status.getBalance());
        assertEquals(
                1790620199999L,
                status.getExpiresAt()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli());
        assertNull(status.getWebhookToken());
    }

    @Test
    void theSoftphoneGetsThePasswordAndTheSbcNotAToken() {

        CallProviderAppDAO apps = mock(CallProviderAppDAO.class);
        ProviderUserEndpointDAO endpoints = mock(ProviderUserEndpointDAO.class);

        ProviderUserEndpoint sip = new ProviderUserEndpoint()
                .setEndpointType(ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP)
                .setProviderUserId("1001_1111112")
                .setProviderMetadata(Map.of(TelecmiApiConfig.META_PASSWORD, "agent-password"));

        when(apps.findByClient(any(), any(), any())).thenReturn(Mono.just(new CallProviderApp()));
        when(endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.just(sip));

        Connection connection = new Connection().setConnectionDetails(Map.of(TelecmiApiConfig.APP_ID, "1111112"));
        connection.setName("calls");

        // Every guard builds its refusal up front, as Exotel's do, so the message service must exist;
        // any refusal reaching it here fails the test.
        MessageResourceService messages =
                mock(MessageResourceService.class, invocation -> Mono.error(new IllegalStateException("refused")));

        BrowserCallToken token = new TelecmiIntegrationsService(null, apps, endpoints, null, messages)
                .generateBrowserToken(MessageAccess.of("app", "CLIENT", Boolean.TRUE), connection, ULong.valueOf(7))
                .block();

        assertEquals("agent-password", token.getToken());
        assertEquals("1001_1111112", token.getProviderUserId());
        assertEquals("telecmi", token.getProvider());
        assertEquals(TelecmiApiConfig.DEFAULT_SBC_URI, token.getRegion());
        assertNull(token.getExpiresIn());
    }
}
