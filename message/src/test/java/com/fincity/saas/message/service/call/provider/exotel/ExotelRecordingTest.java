package com.fincity.saas.message.service.call.provider.exotel;

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
import com.fincity.saas.message.configuration.call.exotel.ExotelApiConfig;
import com.fincity.saas.message.dao.call.provider.exotel.ExotelDAO;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.dto.call.provider.exotel.ExotelCall;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.call.CallConnectionService;
import com.fincity.saas.message.service.call.CallService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jooq.types.ULong;
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
 * Playing an Exotel recording through our proxy, Exotel stubbed at the HTTP layer.
 *
 * <p>Exotel's recording URL answers 401 with a Basic challenge unless it carries the account's API
 * key and token, which a browser shows as a sign-in prompt. What matters most is where those
 * credentials go: to Exotel's recording host and nowhere else, because the URL itself arrives in a
 * status callback anyone holding a CallSid can post.
 */
class ExotelRecordingTest {

    private static final String API_KEY = "the-api-key";
    private static final String API_TOKEN = "the-api-token";
    private static final String RECORDING = "https://recordings.exotel.com/exotelrecordings/acct/SID-1.mp3";
    private static final MessageAccess TENANT = MessageAccess.of("app", "CLIENT", Boolean.TRUE);

    private final List<URI> requested = new ArrayList<>();
    private final List<HttpHeaders> requestHeaders = new ArrayList<>();
    private Function<URI, Mono<ClientResponse>> exotelAnswers;

    private ExotelCallService service;
    private ExotelCall call;
    private Call callRow;

    @BeforeEach
    void setUp() {

        // The real client, so the Basic credentials are the ones the service would send; only the
        // network is replaced.
        WebClientConfig real = new WebClientConfig();
        WebClientConfig clients = mock(WebClientConfig.class);
        when(clients.createExotelRecordingWebClient(any())).thenAnswer(invocation -> real
                .createExotelRecordingWebClient(invocation.getArgument(0))
                .map(client -> client.mutate()
                        .exchangeFunction(request -> {
                            this.requested.add(request.url());
                            this.requestHeaders.add(request.headers());
                            return this.exotelAnswers.apply(request.url());
                        })
                        .build()));

        MessageResourceService messages = mock(MessageResourceService.class, invocation -> {
            if (!"throwMessage".equals(invocation.getMethod().getName())) return null;
            @SuppressWarnings("unchecked")
            Function<String, GenericException> error = invocation.getArgument(0);
            return Mono.error(error.apply(invocation.getArgument(1)));
        });

        Connection connection = new Connection()
                .setConnectionType(ConnectionType.CALL)
                .setConnectionSubType(ConnectionSubType.EXOTEL)
                .setConnectionDetails(Map.of("accountSid", "acct", "apiKey", API_KEY, "apiToken", API_TOKEN));
        connection.setName("exotelCalls");
        CallConnectionService connections = mock(CallConnectionService.class);
        when(connections.getCoreDocument(eq("app"), eq("CLIENT"), eq("exotelCalls")))
                .thenReturn(Mono.just(connection));

        this.call = new ExotelCall().setSid("SID-1").setRecordingUrl(RECORDING);
        this.call.setId(ULong.valueOf(11));
        this.call.setCode("EXO1");

        ExotelDAO dao = mock(ExotelDAO.class);
        when(dao.readInternal(any(MessageAccess.class), any(String.class))).thenReturn(Mono.empty());
        when(dao.readInternal(eq(TENANT), eq("EXO1"))).thenAnswer(invocation -> Mono.just(this.call));

        this.callRow = new Call().setConnectionName("exotelCalls").setExotelCallId(ULong.valueOf(11));
        CallService calls = mock(CallService.class);
        when(calls.readByExotelCallId(any(), any())).thenReturn(Mono.empty());
        when(calls.readByExotelCallId(eq(TENANT), eq(ULong.valueOf(11))))
                .thenAnswer(invocation -> Mono.justOrEmpty(this.callRow));

        this.service = new ExotelCallService();
        ReflectionTestUtils.setField(this.service, "dao", dao);
        ReflectionTestUtils.setField(this.service, "msgService", messages);
        ReflectionTestUtils.setField(this.service, "callConnectionService", connections);
        ReflectionTestUtils.setField(this.service, "callService", calls);
        ReflectionTestUtils.setField(this.service, "webClientConfig", clients);

        this.exotelAnswers = url -> Mono.just(audio(HttpStatus.OK, "ID3-audio-bytes"));
    }

    private static ClientResponse audio(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length()))
                .header("Set-Cookie", "exotel-session=abc")
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

    private void assertNotAvailableAndExotelNotAsked() {
        GenericException refused = this.refused("EXO1");
        assertEquals(HttpStatus.NOT_FOUND.value(), refused.getStatusCode().value());
        assertEquals(MessageResourceService.CALL_RECORDING_NOT_AVAILABLE, refused.getMessage());
        assertTrue(this.requested.isEmpty(), "the credentials were not sent anywhere");
    }

    @Test
    void theRecordingStreamsThroughWithOnlyThePlayersHeaders() {

        ResponseEntity<Flux<DataBuffer>> played = this.play("EXO1", null);

        assertEquals(HttpStatus.OK, played.getStatusCode());
        assertEquals("audio/mpeg", played.getHeaders().getContentType().toString());
        assertEquals("15", played.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH));
        assertNull(played.getHeaders().getFirst("Set-Cookie"));
        assertTrue(played.getHeaders().getCacheControl().contains("no-store"));
        assertEquals("ID3-audio-bytes", bodyOf(played));
    }

    @Test
    void theAccountsBasicCredentialsGoToTheRecordingUrlAndNotBack() {

        ResponseEntity<Flux<DataBuffer>> played = this.play("EXO1", null);

        String basic = "Basic "
                + Base64.getEncoder()
                        .encodeToString((API_KEY + ":" + API_TOKEN).getBytes(StandardCharsets.UTF_8));
        assertEquals(URI.create(RECORDING), this.requested.getFirst());
        assertEquals(basic, this.requestHeaders.getFirst().getFirst(HttpHeaders.AUTHORIZATION));
        assertFalse(played.getHeaders().toString().contains(API_TOKEN));
        assertFalse(played.getHeaders().containsKey(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void aRangeIsForwardedAndAPartialAnswerPassedBack() {

        this.exotelAnswers = url -> Mono.just(ClientResponse.create(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                .header(HttpHeaders.CONTENT_RANGE, "bytes 0-3/15")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body("ID3-")
                .build());

        ResponseEntity<Flux<DataBuffer>> played = this.play("EXO1", "bytes=0-3");

        assertEquals("bytes=0-3", this.requestHeaders.getFirst().getFirst(HttpHeaders.RANGE));
        assertEquals(HttpStatus.PARTIAL_CONTENT, played.getStatusCode());
        assertEquals("bytes 0-3/15", played.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
    }

    @Test
    void aUrlOnAnyOtherHostIsNeverFetchedWithTheCredentials() {

        this.call.setRecordingUrl("https://attacker.example.test/exotelrecordings/acct/SID-1.mp3");

        this.assertNotAvailableAndExotelNotAsked();
    }

    @Test
    void aPlainHttpUrlIsNeverFetched() {

        this.call.setRecordingUrl("http://recordings.exotel.com/exotelrecordings/acct/SID-1.mp3");

        this.assertNotAvailableAndExotelNotAsked();
    }

    @Test
    void aCallWithoutARecordingIsNotAvailable() {

        this.call.setRecordingUrl(null);

        this.assertNotAvailableAndExotelNotAsked();
    }

    @Test
    void aCallNoRowNamesAConnectionForIsNotAvailable() {

        // Every inbound call recorded before the call row carried the Exotel row's id.
        this.callRow = null;

        this.assertNotAvailableAndExotelNotAsked();
    }

    @Test
    void exotelRefusingIsNotAvailableNotPassedOn() {

        this.exotelAnswers = url -> Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"exotel\"")
                .body("unauthorised")
                .build());

        GenericException refused = this.refused("EXO1");

        assertEquals(HttpStatus.NOT_FOUND.value(), refused.getStatusCode().value());
        assertEquals(MessageResourceService.CALL_RECORDING_NOT_AVAILABLE, refused.getMessage());
    }

    @Test
    void anAnswerThatIsNotAudioIsNotAvailable() {

        this.exotelAnswers = url -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "text/html")
                .body("<html>sign in</html>")
                .build());

        assertEquals(
                HttpStatus.NOT_FOUND.value(), this.refused("EXO1").getStatusCode().value());
    }

    @Test
    void failingToReachExotelIsA502ThatNamesNoCredentials() {

        this.exotelAnswers = url -> Mono.error(new IllegalStateException("Connection refused: " + url));

        GenericException refused = this.refused("EXO1");

        assertEquals(HttpStatus.BAD_GATEWAY.value(), refused.getStatusCode().value());
        assertFalse(refused.getMessage().contains(API_TOKEN));
        assertFalse(String.valueOf(refused.getCause()).contains(API_TOKEN));
    }

    @Test
    void aCodeThatIsNotExotelsIsEmptySoTheNextProviderIsAsked() {

        // Another tenant's code included: the read is scoped to the caller's app and client.
        assertNull(this.service.recording(TENANT, "NOT-EXOTELS", null).block());
        assertTrue(this.requested.isEmpty());
    }

    @Test
    void aBrowserCallsRegionalRecordingHostIsExotels() {

        // The host Exotel's Integrations engine reports in CallRecordings on live browser calls.
        this.call.setRecordingUrl("https://recordings.mum1.exotel.com/exotelrecordings/acct/SID-1.mp3");

        assertEquals(HttpStatus.OK, this.play("EXO1", null).getStatusCode());
    }

    @Test
    void onlyHttpsOnExotelsDomainWithNoUserInfoIsARecordingUrl() {

        List<String> domains = List.of(ExotelApiConfig.DEFAULT_RECORDING_DOMAINS);

        assertTrue(ExotelApiConfig.isRecordingUrl(RECORDING, domains));
        assertTrue(ExotelApiConfig.isRecordingUrl("https://recordings.mum1.exotel.com/x.mp3", domains));
        assertTrue(ExotelApiConfig.isRecordingUrl("https://RECORDINGS.exotel.com:443/x.mp3", domains));
        assertFalse(ExotelApiConfig.isRecordingUrl("https://recordings.exotel.com@attacker.example.test/x", domains));
        assertFalse(ExotelApiConfig.isRecordingUrl("https://user:pass@recordings.exotel.com/x.mp3", domains));
        assertFalse(ExotelApiConfig.isRecordingUrl("https://recordings.exotel.com.attacker.test/x.mp3", domains));
        assertFalse(ExotelApiConfig.isRecordingUrl("https://notexotel.com/x.mp3", domains));
        assertFalse(ExotelApiConfig.isRecordingUrl("https://recordings.exotel.com:8443/x.mp3", domains));
        assertFalse(ExotelApiConfig.isRecordingUrl("ftp://recordings.exotel.com/x.mp3", domains));
        assertFalse(ExotelApiConfig.isRecordingUrl("not a url", domains));
        assertFalse(ExotelApiConfig.isRecordingUrl(" ", domains));
        assertFalse(ExotelApiConfig.isRecordingUrl(null, domains));
        assertFalse(ExotelApiConfig.isRecordingUrl(RECORDING, List.of(" ")));
        // A blank entry in the setting names no domain: it must not match a host ending in a dot.
        assertFalse(ExotelApiConfig.isRecordingUrl("https://attacker.example.test./x.mp3", List.of("exotel.com", " ")));
    }

    @Test
    void anOperatorCanNameAFurtherDomain() {

        this.call.setRecordingUrl("https://recordings.other.example.test/exotelrecordings/acct/SID-1.mp3");
        ReflectionTestUtils.setField(
                this.service,
                "recordingDomains",
                new String[] {ExotelApiConfig.DEFAULT_RECORDING_DOMAINS, "other.example.test"});

        assertEquals(HttpStatus.OK, this.play("EXO1", null).getStatusCode());
    }
}
