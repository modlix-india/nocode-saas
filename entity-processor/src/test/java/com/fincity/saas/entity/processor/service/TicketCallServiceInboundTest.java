package com.fincity.saas.entity.processor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.product.ProductComm;
import com.fincity.saas.entity.processor.feign.IFeignMessageService;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import com.fincity.saas.entity.processor.oserver.message.model.ExotelConnectAppletResponse;
import com.fincity.saas.entity.processor.oserver.message.model.IncomingCallRequest;
import com.fincity.saas.entity.processor.service.message.TicketCallLogService;
import com.fincity.saas.entity.processor.service.product.ProductCommService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import java.util.Map;
import java.util.function.Function;
import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

/**
 * Exotel's inbound connect, pinned before its core is shared with TeleCMI.
 *
 * <p>The refactor that extracts the provider-neutral core must leave every call below exactly as it
 * was: which product and deal are found, what reaches the message service, what is recorded, and
 * what goes back to Exotel.
 */
class TicketCallServiceInboundTest {

    private static final Map<String, String> EXOTEL_APPLET = Map.of(
            "CallSid", "SID-1",
            "From", "09000000003",
            "To", "08012345678",
            "Direction", "incoming");

    private ProductCommService productComms;
    private TicketService tickets;
    private IFeignMessageService message;
    private TicketCallLogService callLog;
    private ActivityService activities;
    private TicketCallService service;

    private Ticket ticket;

    @BeforeEach
    void setUp() {

        this.productComms = mock(ProductCommService.class);
        ProductComm productComm = new ProductComm().setConnectionName("calls");
        productComm.setProductId(ULong.valueOf(3));
        when(this.productComms.getByPhoneNumber(any(), eq(ConnectionType.CALL), eq(ConnectionSubType.EXOTEL), any()))
                .thenReturn(Mono.just(productComm));

        this.ticket = new Ticket().setProductId(ULong.valueOf(3)).setAssignedUserId(ULong.valueOf(7));
        this.ticket.setId(ULong.valueOf(11));

        this.tickets = mock(TicketService.class);
        when(this.tickets.getTicket(any(), any(), any(), any())).thenReturn(Mono.just(this.ticket));
        when(this.tickets.validateAssignedUser(any(), any()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(1)));

        this.message = mock(IFeignMessageService.class);
        when(this.message.connectCall(any(), any(), any())).thenReturn(Mono.just(new ExotelConnectAppletResponse()));

        this.callLog = mock(TicketCallLogService.class);
        when(this.callLog.recordIncomingCall(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());

        this.activities = mock(ActivityService.class);
        when(this.activities.acCallLog(any(), any(), any())).thenReturn(Mono.empty());

        ProcessorMessageResourceService messages = mock(ProcessorMessageResourceService.class, invocation -> {
            if (!"throwMessage".equals(invocation.getMethod().getName())) return null;
            @SuppressWarnings("unchecked")
            Function<String, GenericException> error = invocation.getArgument(0);
            return Mono.error(error.apply(invocation.getArgument(1)));
        });

        this.service = new TicketCallService(
                this.tickets,
                this.productComms,
                mock(ProductService.class),
                this.message,
                messages,
                this.activities,
                this.callLog,
                null);
    }

    @Test
    void anExotelCallToAKnownNumberConnectsTheDealsAgentAndRecordsTheCall() {

        ExotelConnectAppletResponse reply = new ExotelConnectAppletResponse();
        when(this.message.connectCall(any(), any(), any())).thenReturn(Mono.just(reply));

        ExotelConnectAppletResponse answered =
                this.service.incomingExotelCall("app", "CLIENT", EXOTEL_APPLET).block();

        assertSame(reply, answered);

        ArgumentCaptor<PhoneNumber> did = ArgumentCaptor.forClass(PhoneNumber.class);
        verify(this.productComms)
                .getByPhoneNumber(any(), eq(ConnectionType.CALL), eq(ConnectionSubType.EXOTEL), did.capture());
        assertEquals("+918012345678", did.getValue().getNumber());

        ArgumentCaptor<IncomingCallRequest> sent = ArgumentCaptor.forClass(IncomingCallRequest.class);
        verify(this.message).connectCall(eq("app"), eq("CLIENT"), sent.capture());
        assertEquals(EXOTEL_APPLET, sent.getValue().getProviderIncomingRequest());
        assertEquals("calls", sent.getValue().getConnectionName());
        assertEquals(ULong.valueOf(7), sent.getValue().getUserId());

        ArgumentCaptor<PhoneNumber> from = ArgumentCaptor.forClass(PhoneNumber.class);
        verify(this.callLog)
                .recordIncomingCall(
                        any(),
                        eq(this.ticket),
                        eq("EXOTEL"),
                        eq("SID-1"),
                        eq("calls"),
                        from.capture(),
                        any(),
                        eq(Map.copyOf(EXOTEL_APPLET)));
        assertEquals("+919000000003", from.getValue().getNumber());
        verify(this.activities).acCallLog(any(), eq(this.ticket), eq(null));
    }

    @Test
    void anExotelCallToAnUnknownNumberIsRefusedBeforeAnyDealIsTouched() {

        when(this.productComms.getByPhoneNumber(any(), any(), any(), any())).thenReturn(Mono.empty());

        assertThrows(GenericException.class, () -> this.service
                .incomingExotelCall("app", "CLIENT", EXOTEL_APPLET)
                .block());

        verify(this.tickets, never()).getTicket(any(), any(), any(), any());
        verify(this.message, never()).connectCall(any(), any(), any());
    }

    @Test
    void anExotelCallWithoutACallSidIsRefused() {

        assertThrows(GenericException.class, () -> this.service
                .incomingExotelCall("app", "CLIENT", Map.of("From", "09000000003", "To", "08012345678"))
                .block());

        verify(this.productComms, never()).getByPhoneNumber(any(), any(), any(), any());
    }

    // -----------------------------------------------------------------------------------------
    // TeleCMI's HTTP flow: the same routing, with the origin proven before any deal is touched
    // -----------------------------------------------------------------------------------------

    private static final Map<String, String> TELECMI_FLOW = Map.of(
            "from", "919000000003",
            "to", "918012345678",
            "cmiuuid", "CONV-1",
            "appid", "1111112");

    private void telecmiNumberIsKnown() {
        ProductComm productComm = new ProductComm().setConnectionName("telecmi-calls");
        productComm.setProductId(ULong.valueOf(3));
        when(this.productComms.getByPhoneNumber(any(), eq(ConnectionType.CALL), eq(ConnectionSubType.TELECMI), any()))
                .thenReturn(Mono.just(productComm));
    }

    @Test
    void aTelecmiCallIsVerifiedThenRoutedToTheDealsAgentAndItsReplyPassedBack() {

        this.telecmiNumberIsKnown();
        Map<String, Object> reply =
                Map.of("code", 200, "result", java.util.List.of(Map.of("agent_id", "1001_1111112")));
        when(this.message.verifyTelecmiFlow(any(), any(), any())).thenReturn(Mono.just(Boolean.TRUE));
        when(this.message.connectCallReply(any(), any(), any())).thenReturn(Mono.just(reply));

        Map<String, Object> answered = this.service
                .incomingTelecmiCall("app", "CLIENT", "tok", TELECMI_FLOW)
                .block();

        assertSame(reply, answered);

        ArgumentCaptor<Map<String, String>> verification = ArgumentCaptor.forClass(Map.class);
        verify(this.message).verifyTelecmiFlow(eq("app"), eq("CLIENT"), verification.capture());
        assertEquals(Map.of("token", "tok", "appId", "1111112"), verification.getValue());

        ArgumentCaptor<PhoneNumber> did = ArgumentCaptor.forClass(PhoneNumber.class);
        verify(this.productComms)
                .getByPhoneNumber(any(), eq(ConnectionType.CALL), eq(ConnectionSubType.TELECMI), did.capture());
        assertEquals("+918012345678", did.getValue().getNumber());

        ArgumentCaptor<IncomingCallRequest> sent = ArgumentCaptor.forClass(IncomingCallRequest.class);
        verify(this.message).connectCallReply(eq("app"), eq("CLIENT"), sent.capture());
        assertEquals(TELECMI_FLOW, sent.getValue().getProviderIncomingRequest());
        assertEquals("telecmi-calls", sent.getValue().getConnectionName());
        assertEquals(ULong.valueOf(7), sent.getValue().getUserId());
        verify(this.message, never()).connectCall(any(), any(), any());

        ArgumentCaptor<PhoneNumber> from = ArgumentCaptor.forClass(PhoneNumber.class);
        verify(this.callLog)
                .recordIncomingCall(
                        any(),
                        eq(this.ticket),
                        eq("TELECMI"),
                        eq("CONV-1"),
                        eq("telecmi-calls"),
                        from.capture(),
                        any(),
                        eq(Map.copyOf(TELECMI_FLOW)));
        assertEquals("+919000000003", from.getValue().getNumber());
    }

    @Test
    void aTelecmiCallThatFailsVerificationTouchesNoDeal() {

        this.telecmiNumberIsKnown();
        when(this.message.verifyTelecmiFlow(any(), any(), any()))
                .thenReturn(Mono.error(new GenericException(org.springframework.http.HttpStatus.UNAUTHORIZED, "no")));

        assertThrows(GenericException.class, () -> this.service
                .incomingTelecmiCall("app", "CLIENT", "wrong", TELECMI_FLOW)
                .block());

        verify(this.productComms, never()).getByPhoneNumber(any(), any(), any(), any());
        verify(this.tickets, never()).getTicket(any(), any(), any(), any());
        verify(this.tickets, never()).create(any(), any());
        verify(this.message, never()).connectCallReply(any(), any(), any());
    }

    @Test
    void aTelecmiCallWithoutItsCallIdIsRefusedBeforeVerification() {

        assertThrows(GenericException.class, () -> this.service
                .incomingTelecmiCall("app", "CLIENT", "tok", Map.of("from", "919000000003", "to", "918012345678"))
                .block());

        verify(this.message, never()).verifyTelecmiFlow(any(), any(), any());
    }
}
