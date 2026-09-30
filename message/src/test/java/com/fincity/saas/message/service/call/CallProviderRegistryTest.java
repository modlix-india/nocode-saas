package com.fincity.saas.message.service.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fincity.saas.message.enums.call.CallStatus;
import com.fincity.saas.message.model.request.dispatch.CallEventDispatch;
import com.fincity.saas.message.model.response.call.BrowserCallStatus;
import com.fincity.saas.message.model.response.call.BrowserCallToken;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.call.provider.exotel.ExotelCallService;
import com.fincity.saas.message.service.call.provider.telecmi.TelecmiCallService;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * How {@link CallService} finds a provider, and what the multi-provider fields add to payloads.
 *
 * <p>Providers are registered explicitly in {@code init()}, as {@code EmailService} and
 * {@code RestService} do. The case worth protecting is that each provider is found under its own
 * subtype for both plain and browser calling — the browser registry is derived from the plain one, so a provider
 * cannot be half-registered.
 *
 * <p>The payload cases guard the other half of "Exotel is untouched": every new field is absent
 * from what Exotel sends and receives unless a provider sets it.
 */
class CallProviderRegistryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static Connection connection(ConnectionSubType subType, Map<String, Object> details) {
        Connection connection = new Connection().setConnectionSubType(subType).setConnectionDetails(details);
        connection.setName("calls");
        return connection;
    }

    @Test
    void eachProviderIsFoundForPlainAndBrowserCalling() {

        ExotelCallService exotel = mock(ExotelCallService.class);
        TelecmiCallService telecmi = mock(TelecmiCallService.class);

        CallService service = new CallService(null, null, exotel, telecmi);
        service.init();

        assertSame(
                exotel,
                service.callServiceFor(connection(ConnectionSubType.EXOTEL, Map.of()))
                        .block());
        assertSame(
                exotel,
                service.browserServiceFor(connection(ConnectionSubType.EXOTEL, Map.of()))
                        .block());
        assertSame(
                telecmi,
                service.callServiceFor(connection(ConnectionSubType.TELECMI, Map.of()))
                        .block());
        assertSame(
                telecmi,
                service.browserServiceFor(connection(ConnectionSubType.TELECMI, Map.of()))
                        .block());
    }

    @Test
    void statusCarriesTheConnectionAndItsLibraryUrl() {

        BrowserCallStatus status = CallService.withConnection(
                BrowserCallStatus.of("telecmi", "agent", "91XXXXXXXXXX"),
                connection(
                        ConnectionSubType.TELECMI,
                        Map.of(CallService.CONNECTION_DETAIL_SDK_URL, "https://example.invalid/piopiy.min.js")));

        assertEquals("calls", status.getConnectionName());
        assertEquals("https://example.invalid/piopiy.min.js", status.getSdkUrl());
    }

    @Test
    void aConnectionWithoutALibraryUrlLeavesThePageSettingInCharge() throws Exception {

        // Exotel connections set no sdkUrl; the page's own "Calling Library URL" must keep winning,
        // so the field is absent rather than empty.
        BrowserCallStatus status = CallService.withConnection(
                BrowserCallStatus.of("exotel", "agent", "91XXXXXXXXXX"), connection(ConnectionSubType.EXOTEL, null));

        assertNull(status.getSdkUrl());
        assertFalse(MAPPER.writeValueAsString(status).contains("sdkUrl"));
        assertEquals("calls", status.getConnectionName());
    }

    @Test
    void anExotelEventIsSerialisedExactlyAsBefore() throws Exception {

        CallEventDispatch exotel =
                new CallEventDispatch().setProviderCallId("sid").setCallStatus("completed");

        assertFalse(MAPPER.writeValueAsString(exotel).contains("normalizedCallStatus"));

        CallEventDispatch mapped =
                new CallEventDispatch().setProviderCallId("request").setNormalizedCallStatus(CallStatus.BUSY);

        assertTrue(MAPPER.writeValueAsString(mapped).contains("\"normalizedCallStatus\":\"BUSY\""));
    }

    @Test
    void aTokenWithoutARegionOmitsIt() throws Exception {

        BrowserCallToken exotel = BrowserCallToken.of("t", "agent", 60L, "exotel");
        assertFalse(MAPPER.writeValueAsString(exotel).contains("region"));

        BrowserCallToken telecmi =
                BrowserCallToken.of("t", "agent", null, "telecmi").setRegion("sbcind.telecmi.com");
        assertTrue(MAPPER.writeValueAsString(telecmi).contains("\"region\":\"sbcind.telecmi.com\""));
    }
}
