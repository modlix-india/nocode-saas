package com.fincity.saas.message.service.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.call.provider.exotel.ExotelCallService;
import com.fincity.saas.message.service.call.provider.telecmi.TelecmiCallService;
import java.math.BigInteger;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * One recording route for every provider: the URL carries our code and nothing of the provider's,
 * so each provider is asked in turn, and the first that holds the call answers.
 */
class CallRecordingRegistryTest {

    private static final MessageAccess TENANT = MessageAccess.of("app", "CLIENT", Boolean.TRUE);

    private final ResponseEntity<Flux<DataBuffer>> played = ResponseEntity.ok(Flux.empty());

    private ExotelCallService exotel;
    private TelecmiCallService telecmi;
    private CallService service;

    @BeforeEach
    void setUp() {

        this.exotel = mock(ExotelCallService.class);
        this.telecmi = mock(TelecmiCallService.class);
        when(this.exotel.recording(any(), any(), any())).thenReturn(Mono.empty());
        when(this.telecmi.recording(any(), any(), any())).thenReturn(Mono.empty());

        MessageResourceService messages = mock(MessageResourceService.class, invocation -> {
            if (!"throwMessage".equals(invocation.getMethod().getName())) return null;
            @SuppressWarnings("unchecked")
            Function<String, GenericException> error = invocation.getArgument(0);
            return Mono.error(error.apply(invocation.getArgument(1)));
        });

        this.service = new CallService(null, null, this.exotel, this.telecmi);
        ReflectionTestUtils.setField(this.service, "msgService", messages);
        this.service.init();
    }

    private ResponseEntity<Flux<DataBuffer>> play(String code) {
        return this.service.recording(TENANT, code, null).block();
    }

    @Test
    void anExotelCallIsPlayedByExotel() {

        when(this.exotel.recording(TENANT, "EXO1", null)).thenReturn(Mono.just(this.played));

        assertSame(this.played, this.play("EXO1"));
    }

    @Test
    void aTelecmiCallIsPlayedByTelecmiOnceExotelDoesNotHaveIt() {

        when(this.telecmi.recording(TENANT, "CMI1", null)).thenReturn(Mono.just(this.played));

        assertSame(this.played, this.play("CMI1"));
    }

    @Test
    void aCodeNoProviderHasIsNotFound() {

        GenericException refused = assertThrows(GenericException.class, () -> this.play("NOBODYS"));

        assertEquals(HttpStatus.NOT_FOUND.value(), refused.getStatusCode().value());
        assertEquals(MessageResourceService.CALL_RECORDING_NOT_AVAILABLE, refused.getMessage());
    }

    private static ContextAuthentication session(boolean signedIn, String phone) {
        ContextAuthentication session = new ContextAuthentication()
                .setUser(new ContextUser().setId(BigInteger.valueOf(9)).setPhoneNumber(phone))
                .setUrlAppCode("app")
                .setClientCode("CLIENT");
        // Spring's Authentication declares this setter, so it does not chain.
        session.setAuthenticated(signedIn);
        return session;
    }

    private ResponseEntity<Flux<DataBuffer>> playAs(ContextAuthentication session) {
        return this.service
                .recording("EXO1", null)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(session))
                .block();
    }

    @Test
    void someoneSignedInIsAskedForWithTheirSessionsTenant() {

        when(this.exotel.recording(any(), eq("EXO1"), any())).thenReturn(Mono.just(this.played));

        assertSame(this.played, this.playAs(session(true, "+919000000001")));

        ArgumentCaptor<MessageAccess> asked = ArgumentCaptor.forClass(MessageAccess.class);
        verify(this.exotel).recording(asked.capture(), eq("EXO1"), any());
        assertEquals("app", asked.getValue().getAppCode());
        assertEquals("CLIENT", asked.getValue().getClientCode());
    }

    @Test
    void someoneSignedInWithNoPhoneNumberIsRefusedAsEveryRouteRefusesThem() {

        // hasAccess(), as every route in this service: a known limitation for listening.
        GenericException refused = assertThrows(GenericException.class, () -> this.playAs(session(true, null)));

        assertEquals(HttpStatus.BAD_REQUEST.value(), refused.getStatusCode().value());
        assertEquals(MessageResourceService.PHONE_NUMBER_REQUIRED, refused.getMessage());
        verify(this.exotel, never()).recording(any(), any(), any());
    }

    @Test
    void someoneNotSignedInIsRefusedAndNoProviderIsAsked() {

        GenericException refused =
                assertThrows(GenericException.class, () -> this.playAs(session(false, "+919000000001")));

        assertEquals(HttpStatus.FORBIDDEN.value(), refused.getStatusCode().value());
        assertEquals(MessageResourceService.LOGIN_REQUIRED, refused.getMessage());
        verify(this.exotel, never()).recording(any(), any(), any());
    }

    @Test
    void aProviderThatHasTheCallButNoRecordingEndsTheSearch() {

        when(this.exotel.recording(TENANT, "EXO1", null))
                .thenReturn(Mono.error(new GenericException(HttpStatus.NOT_FOUND, "not available")));

        assertThrows(GenericException.class, () -> this.play("EXO1"));
        verify(this.telecmi, never()).recording(any(), any(), any());
    }

    @Test
    void theAnsweringProviderIsNotCancelled() {

        // Emits the entity, then completes a moment later, as a response still bound to its connection can.
        AtomicBoolean cancelled = new AtomicBoolean();
        Mono<ResponseEntity<Flux<DataBuffer>>> answer = Mono.fromDirect(Flux.just(this.played)
                .concatWith(Mono.delay(Duration.ofMillis(20)).then(Mono.empty()))
                .doOnCancel(() -> cancelled.set(true)));
        when(this.exotel.recording(TENANT, "EXO1", null)).thenReturn(answer);

        StepVerifier.create(this.service.recording(TENANT, "EXO1", null))
                .expectNext(this.played)
                .verifyComplete();
        assertFalse(cancelled.get(), "cancelling the answer can release the connection its body streams over");
    }

    @Test
    void theUrlHandedOverIsRelativeSoThePagesPathCarriesTheTenant() {

        // An absolute /api/... drops the /<app>/<client>/page prefix on hosts that address the app by path.
        assertEquals("api/message/call/recording/CODE1", ICallRecordingService.recordingUri("CODE1", true));
        assertNull(ICallRecordingService.recordingUri("CODE1", false));
    }
}
