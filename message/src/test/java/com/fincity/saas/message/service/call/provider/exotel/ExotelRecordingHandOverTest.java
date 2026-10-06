package com.fincity.saas.message.service.call.provider.exotel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.message.dao.call.ProviderUserEndpointDAO;
import com.fincity.saas.message.dao.call.provider.exotel.ExotelDAO;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.dto.call.provider.exotel.ExotelCall;
import com.fincity.saas.message.enums.call.provider.exotel.option.ExotelDirection;
import com.fincity.saas.message.enums.dispatch.DispatchEventType;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.call.IncomingCallRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelCallStatusCallback;
import com.fincity.saas.message.model.request.dispatch.CallEventDispatch;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.call.CallConnectionService;
import com.fincity.saas.message.service.call.CallService;
import com.fincity.saas.message.service.call.ICallRecordingService;
import com.fincity.saas.message.service.call.event.CallEventService;
import com.fincity.saas.message.service.dispatch.EventDispatcher;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * What makes an Exotel recording playable in the CRM: the owning service is handed our URL, never
 * Exotel's, and the call row names the connection the recording is fetched with.
 */
class ExotelRecordingHandOverTest {

    private static final String EXOTEL_URL = "https://recordings.exotel.com/exotelrecordings/acct/SID-1.mp3";

    private final List<CallEventDispatch> handedOver = new ArrayList<>();
    private final List<Call> callRows = new ArrayList<>();

    private ExotelCallService service;
    private ExotelCall stored;

    @BeforeEach
    void setUp() {

        this.stored = new ExotelCall()
                .setSid("SID-1")
                .setDirection(ExotelDirection.OUTBOUND_API.name())
                .setOwnerService("entity-processor");
        this.stored.setId(ULong.valueOf(11));
        this.stored.setCode("EXO1");
        this.stored.setAppCode("app").setClientCode("CLIENT");
        this.stored.setUserId(ULong.valueOf(7));

        ExotelDAO dao = mock(ExotelDAO.class);
        when(dao.findByUniqueField("SID-1")).thenAnswer(invocation -> Mono.just(this.stored));
        when(dao.update(any(ExotelCall.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(dao.existsByUniqueField(any(MessageAccess.class), any(String.class)))
                .thenReturn(Mono.just(Boolean.FALSE));
        when(dao.create(any(ExotelCall.class))).thenAnswer(invocation -> {
            ExotelCall created = invocation.getArgument(0);
            created.setId(ULong.valueOf(12));
            return Mono.just(created);
        });

        EventDispatcher dispatcher = mock(EventDispatcher.class);
        when(dispatcher.enqueueAndDispatch(any(), any(), any(DispatchEventType.class), any(), any()))
                .thenAnswer(invocation -> {
                    this.handedOver.add(invocation.getArgument(4));
                    return Mono.empty();
                });

        CallService calls = mock(CallService.class);
        when(calls.createInternal(any(MessageAccess.class), any(), any(Call.class)))
                .thenAnswer(invocation -> {
                    this.callRows.add(invocation.getArgument(2));
                    return Mono.just(invocation.getArgument(2));
                });

        IFeignSecurityService security = mock(IFeignSecurityService.class);
        when(security.getUserInternal(any(), any()))
                .thenReturn(Mono.just(new User().setId(BigInteger.valueOf(7)).setPhoneNumber("+919000000001")));

        ProviderUserEndpointDAO endpoints = mock(ProviderUserEndpointDAO.class);
        when(endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.empty());

        Connection connection = new Connection()
                .setConnectionType(ConnectionType.CALL)
                .setConnectionSubType(ConnectionSubType.EXOTEL)
                .setConnectionDetails(new HashMap<>(Map.of("accountSid", "acct")));
        connection.setName("exotelCalls");
        CallConnectionService connections = mock(CallConnectionService.class);
        when(connections.getCoreDocument(any(), any(), any())).thenReturn(Mono.just(connection));

        this.service = new ExotelCallService();
        this.service.setEventDispatcher(dispatcher);
        this.service.setProviderUserEndpointDAO(endpoints);
        ReflectionTestUtils.setField(this.service, "dao", dao);
        ReflectionTestUtils.setField(this.service, "securityService", security);
        ReflectionTestUtils.setField(
                this.service, "cacheService", mock(CacheService.class, invocation -> Mono.just(Boolean.TRUE)));
        ReflectionTestUtils.setField(
                this.service, "callEventService", mock(CallEventService.class, invocation -> Mono.empty()));
        ReflectionTestUtils.setField(this.service, "callService", calls);
        ReflectionTestUtils.setField(this.service, "callConnectionService", connections);
        ReflectionTestUtils.setField(this.service, "defaultCallOwnerService", "entity-processor");
    }

    private CallEventDispatch statusWith(String recordingUrl) {
        this.service
                .processCallStatusCallback(
                        MessageAccess.of("app", "CLIENT", true),
                        new ExotelCallStatusCallback().setCallSid("SID-1").setRecordingUrl(recordingUrl))
                .block();
        return this.handedOver.getLast();
    }

    @Test
    void theOwnerIsHandedOurRecordingUrlNeverExotels() {

        CallEventDispatch dispatch = this.statusWith(EXOTEL_URL);

        assertEquals(ICallRecordingService.RECORDING_URI + "EXO1", dispatch.getRecordingUrl());
        assertEquals(EXOTEL_URL, this.stored.getRecordingUrl(), "our row keeps Exotel's, which is what we fetch");
    }

    @Test
    void aCallWithoutARecordingHandsOverNone() {

        assertNull(this.statusWith(null).getRecordingUrl());
    }

    @Test
    void anInboundCallsRowCarriesTheExotelRowItBelongsTo() {

        IncomingCallRequest request = new IncomingCallRequest()
                .setProviderIncomingRequest(Map.of("CallSid", "SID-IN", "From", "09000000003", "To", "08012345678"));
        request.setUserId(ULong.valueOf(7));
        request.setConnectionName("exotelCalls");

        this.service.connectCall("app", "CLIENT", request).block();

        Call row = this.callRows.getFirst();
        assertEquals(ULong.valueOf(12), row.getExotelCallId(), "not null, so the recording can find its connection");
        assertEquals("exotelCalls", row.getConnectionName());
    }
}
