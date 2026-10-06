package com.fincity.saas.message.service.call.provider.telecmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.message.configuration.WebClientConfig;
import com.fincity.saas.message.configuration.call.telecmi.TelecmiApiConfig;
import com.fincity.saas.message.dao.call.provider.telecmi.TelecmiDAO;
import com.fincity.saas.message.dto.call.provider.telecmi.TelecmiCall;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.call.CallConnectionService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Playing a recording through our proxy, TeleCMI stubbed at the HTTP layer.
 *
 * <p>What matters most is what never leaves: {@code /v2/play} carries the app secret in its query
 * string, so it must reach TeleCMI and nothing else — not the browser's headers, not an error.
 */
class TelecmiRecordingTest {

    private static final String SECRET = "the-app-secret";
    private static final MessageAccess TENANT = MessageAccess.of("app", "CLIENT", Boolean.TRUE);

    private final List<URI> requested = new ArrayList<>();
    private final List<HttpHeaders> requestHeaders = new ArrayList<>();
    private Function<URI, Mono<ClientResponse>> telecmiAnswers;

    private TelecmiDAO dao;
    private TelecmiCallService service;

    private TelecmiCall call;

    private Connection connection;

    @BeforeEach
    void setUp() {

        WebClient telecmi = WebClient.builder()
                .baseUrl(TelecmiApiConfig.DEFAULT_REST_BASE)
                .exchangeFunction(request -> {
                    this.requested.add(request.url());
                    this.requestHeaders.add(request.headers());
                    return this.telecmiAnswers.apply(request.url());
                })
                .build();

        WebClientConfig clients = mock(WebClientConfig.class);
        when(clients.createTelecmiWebClient(any())).thenReturn(Mono.just(telecmi));

        MessageResourceService messages = mock(MessageResourceService.class, invocation -> {
            if (!"throwMessage".equals(invocation.getMethod().getName())) return null;
            @SuppressWarnings("unchecked")
            Function<String, GenericException> error = invocation.getArgument(0);
            return Mono.error(error.apply(invocation.getArgument(1)));
        });

        this.connection = new Connection()
                .setConnectionType(ConnectionType.CALL)
                .setConnectionSubType(ConnectionSubType.TELECMI)
                .setConnectionDetails(Map.of(TelecmiApiConfig.APP_ID, "1111112", TelecmiApiConfig.SECRET, SECRET));
        this.connection.setName("calls");
        CallConnectionService connections = mock(CallConnectionService.class);
        when(connections.getCoreDocument(eq("app"), eq("CLIENT"), eq("calls"))).thenReturn(Mono.just(this.connection));

        this.call = new TelecmiCall().setConnectionName("calls").setRecordingFile("REC-1.mp3");
        this.call.setAppCode("app").setClientCode("CLIENT");

        this.dao = mock(TelecmiDAO.class);
        when(this.dao.readInternal(any(MessageAccess.class), any(String.class))).thenReturn(Mono.empty());
        when(this.dao.readInternal(eq(TENANT), eq("CODE1"))).thenAnswer(invocation -> Mono.just(this.call));

        this.service = new TelecmiCallService();
        this.service.setIntegrationsService(new TelecmiIntegrationsService(clients, null, null, null, messages));
        ReflectionTestUtils.setField(this.service, "dao", this.dao);
        ReflectionTestUtils.setField(this.service, "msgService", messages);
        ReflectionTestUtils.setField(this.service, "callConnectionService", connections);

        this.telecmiAnswers = url -> Mono.just(audio(HttpStatus.OK, "ID3-audio-bytes"));
    }

    private static ClientResponse audio(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length()))
                .header("Set-Cookie", "telecmi-session=abc")
                .body(body)
                .build();
    }

    private ResponseEntity<Flux<DataBuffer>> play(String code, String range) {
        return this.service.recording(TENANT, code, range).block();
    }

    private static String bodyOf(ResponseEntity<Flux<DataBuffer>> entity) {
        return DataBufferUtils.join(entity.getBody())
                .map(buffer -> {
                    String text = buffer.toString(StandardCharsets.UTF_8);
                    DataBufferUtils.release(buffer);
                    return text;
                })
                .block();
    }

    private GenericException refused(String code) {
        return assertThrows(GenericException.class, () -> this.play(code, null));
    }

    @Test
    void theRecordingStreamsThroughWithOnlyThePlayersHeaders() {

        ResponseEntity<Flux<DataBuffer>> played = this.play("CODE1", null);

        assertEquals(HttpStatus.OK, played.getStatusCode());
        assertEquals("audio/mpeg", played.getHeaders().getContentType().toString());
        assertEquals("15", played.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH));
        assertNull(played.getHeaders().getFirst("Set-Cookie"));
        assertTrue(played.getHeaders().getCacheControl().contains("no-store"));
        assertEquals("ID3-audio-bytes", bodyOf(played));
    }

    @Test
    void theSecretGoesToTelecmiInItsQueryAndNowhereElse() {

        ResponseEntity<Flux<DataBuffer>> played = this.play("CODE1", null);

        URI asked = this.requested.getFirst();
        assertEquals("/v2/play", asked.getPath());
        assertTrue(asked.getQuery().contains("appid=1111112"));
        assertTrue(asked.getQuery().contains("secret=" + SECRET));
        assertTrue(asked.getQuery().contains("file=REC-1.mp3"));
        assertFalse(played.getHeaders().toString().contains(SECRET));
    }

    @Test
    void aRangeIsForwardedAndAPartialAnswerPassedBack() {

        this.telecmiAnswers = url -> Mono.just(ClientResponse.create(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                .header(HttpHeaders.CONTENT_RANGE, "bytes 0-3/15")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body("ID3-")
                .build());

        ResponseEntity<Flux<DataBuffer>> played = this.play("CODE1", "bytes=0-3");

        assertEquals("bytes=0-3", this.requestHeaders.getFirst().getFirst(HttpHeaders.RANGE));
        assertEquals(HttpStatus.PARTIAL_CONTENT, played.getStatusCode());
        assertEquals("bytes 0-3/15", played.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
        assertEquals("bytes", played.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
    }

    @Test
    void aRefusalInAJsonBodyIsNotAvailableNotPassedOn() {

        this.telecmiAnswers = url -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body("{\"code\":404,\"msg\":\"File not found\"}")
                .build());

        GenericException refused = this.refused("CODE1");

        assertEquals(HttpStatus.NOT_FOUND.value(), refused.getStatusCode().value());
        assertEquals(MessageResourceService.CALL_RECORDING_NOT_AVAILABLE, refused.getMessage());
    }

    @Test
    void aRecordingTelecmiNoLongerHasIsNotAvailable() {

        this.telecmiAnswers = url -> Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND)
                .header(HttpHeaders.CONTENT_TYPE, "text/html")
                .body("not found")
                .build());

        assertEquals(
                HttpStatus.NOT_FOUND.value(),
                this.refused("CODE1").getStatusCode().value());
    }

    @Test
    void failingToReachTelecmiIsA502ThatNamesNoUrl() {

        this.telecmiAnswers = url -> Mono.error(new IllegalStateException("Connection refused: " + url));

        GenericException refused = this.refused("CODE1");

        assertEquals(HttpStatus.BAD_GATEWAY.value(), refused.getStatusCode().value());
        assertFalse(refused.getMessage().contains(SECRET));
        assertFalse(String.valueOf(refused.getCause()).contains(SECRET));
    }

    @Test
    void aCallWithoutARecordingIsNotAvailableAndTelecmiIsNotAsked() {

        this.call.setRecordingFile(null);

        assertEquals(
                HttpStatus.NOT_FOUND.value(),
                this.refused("CODE1").getStatusCode().value());
        assertTrue(this.requested.isEmpty());
    }

    @Test
    void anotherTenantsCallReadsAsNotTelecmisSoTheNextProviderIsAsked() {

        // readInternal is scoped to the caller's app and client; another tenant's row reads as empty,
        // exactly as an unknown code does, and CallService answers not found once no provider has it.
        assertNull(this.play("CODE-OF-ANOTHER-TENANT", null));
        assertTrue(this.requested.isEmpty());
    }

    @Test
    void aConnectionThatIsNotTelecmisIsRefusedAndTelecmiIsNotAsked() {

        // A connection renamed or repointed after the call: its details must not be sent to /v2/play.
        this.connection.setConnectionSubType(ConnectionSubType.EXOTEL);

        GenericException refused = assertThrows(GenericException.class, () -> this.play("CODE1", null));

        assertEquals(HttpStatus.BAD_REQUEST.value(), refused.getStatusCode().value());
        assertEquals(MessageResourceService.INVALID_CONNECTION_TYPE, refused.getMessage());
        assertTrue(this.requested.isEmpty());
    }
}
