package com.fincity.saas.entity.processor.service.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.message.CallDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.message.Call;
import com.fincity.saas.entity.processor.feign.IFeignMessageService;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.TicketService;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.Function;
import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

/**
 * What a placed call sends to the message service and how its row is stamped.
 *
 * <p>Two things phase 7 changed: the agent is always the caller (decision #37), since TeleCMI
 * rings that user's mobile first; and the provider comes from the placed call, which used to be
 * assumed to be Exotel's.
 */
class TicketCallLogPlacementTest {

    private TicketService tickets;
    private CallDAO calls;
    private IFeignMessageService message;
    private TicketCallLogService service;
    private Ticket deal;

    @BeforeEach
    void setUp() {

        ProcessorAccess agent = new ProcessorAccess()
                .setAppCode("app")
                .setClientCode("CLIENT")
                .setUserId(ULong.valueOf(7))
                .setHasAccessFlag(true);

        this.deal = new Ticket().setPhoneNumber("9000000003").setProductId(ULong.valueOf(3));
        this.deal.setId(ULong.valueOf(11));

        this.tickets = mock(TicketService.class);
        when(this.tickets.hasAccess()).thenReturn(Mono.just(agent));
        when(this.tickets.readByIdentity(any(ProcessorAccess.class), any(Identity.class)))
                .thenAnswer(invocation -> Mono.just(this.deal));

        this.calls = mock(CallDAO.class);
        when(this.calls.readByProviderCallId(any(), any(), any())).thenReturn(Mono.empty());
        when(this.calls.create(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        this.message = mock(IFeignMessageService.class);

        this.service = new TicketCallLogService(
                this.tickets,
                this.calls,
                this.message,
                // Every refusal surfaces as the GenericException its guard builds, message = the key.
                mock(ProcessorMessageResourceService.class, invocation -> {
                    if (!"throwMessage".equals(invocation.getMethod().getName())) return null;
                    @SuppressWarnings("unchecked")
                    Function<String, GenericException> error = invocation.getArgument(0);
                    return Mono.error(error.apply(invocation.getArgument(1)));
                }),

                // As Spring Boot configures the one injected in production: unknown fields ignored,
                // which is what lets a provider's placed call carry fields this side does not map.
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
    }

    private Call created() {
        ArgumentCaptor<Call> created = ArgumentCaptor.forClass(Call.class);
        verify(this.calls).create(created.capture());
        return created.getValue();
    }

    @Test
    void theAgentPlacingTheCallIsTheUserSentAndNothingFromTheRequest() {

        when(this.message.makeCallInternal(any(), any(), any()))
                .thenReturn(Mono.just(Map.of("providerCallId", "REQ-1", "callProvider", "TELECMI")));

        this.service
                .makeCall(Identity.of(BigInteger.valueOf(11)), "calls", null)
                .block();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> sent = ArgumentCaptor.forClass(Map.class);
        verify(this.message).makeCallInternal(eq("app"), eq("CLIENT"), sent.capture());
        assertEquals(BigInteger.valueOf(7), sent.getValue().get("userId"));
        assertEquals("calls", sent.getValue().get("connectionName"));
        assertFalse(sent.getValue().containsKey("callerId"));
    }

    @Test
    void aTelecmiCallIsStampedTelecmiFromWhatWasPlaced() {

        when(this.message.makeCallInternal(any(), any(), any()))
                .thenReturn(Mono.just(Map.of("providerCallId", "REQ-1", "callProvider", "TELECMI")));

        this.service
                .makeCall(Identity.of(BigInteger.valueOf(11)), "calls", null)
                .block();

        Call call = this.created();
        assertEquals("TELECMI", call.getCallProvider());
        assertEquals("REQ-1", call.getProviderCallId());
        assertEquals(ULong.valueOf(11), call.getTicketId());
    }

    @Test
    void anExotelCallWhichNamesNoProviderIsStillStampedExotel() {

        when(this.message.browserDialInternal(any(), any(), any()))
                .thenReturn(Mono.just(Map.of("sid", "SID-1", "exotelCallStatus", "in-progress")));

        this.service
                .makeBrowserCall(Identity.of(BigInteger.valueOf(11)), "calls")
                .block();

        Call call = this.created();
        assertEquals("EXOTEL", call.getCallProvider());
        assertEquals("SID-1", call.getProviderCallId());
    }

    @Test
    void aDealNumberThatDoesNotParseIsARequestErrorAndNothingIsPlaced() {

        this.deal.setPhoneNumber("not-a-number");

        GenericException refused = assertThrows(
                GenericException.class,
                () -> this.service
                        .makeCall(Identity.of(BigInteger.valueOf(11)), "calls", null)
                        .block());

        assertEquals(HttpStatus.BAD_REQUEST.value(), refused.getStatusCode().value());
        assertEquals(ProcessorMessageResourceService.MISSING_PARAMETERS, refused.getMessage());
        verify(this.message, never()).makeCallInternal(any(), any(), any());
    }
}
