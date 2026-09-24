package com.fincity.saas.entity.processor.controller.open;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.model.request.CampaignTicketRequest;
import com.fincity.saas.entity.processor.service.EntityIntegrationService;
import com.fincity.saas.entity.processor.service.TicketService;
import java.util.Map;
import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/**
 * Which rejections these endpoints acknowledge, and which they report.
 *
 * <p>Worth pinning down because both callers are Meta and Google, which read any non-2xx as a
 * failed webhook delivery and redeliver the same lead with backoff. A duplicate lead has already
 * had its re-inquiry committed by the time it fails, so answering with an error earned another
 * re-inquiry row on every redelivery and never stopped the provider trying again - it has to be a
 * 200. Every other rejection must stay an error, or a lead that genuinely never landed would
 * disappear behind a 200 with nothing to show it ever arrived.
 */
class TicketOpenControllerTest {

    private static final String PRODUCT_CODE = "PROD_1";

    private TicketService ticketService;
    private TicketOpenController controller;

    @BeforeEach
    void setUp() {
        ticketService = mock(TicketService.class);
        controller = new TicketOpenController(ticketService, mock(EntityIntegrationService.class));
    }

    private static Ticket ticketWithId(long id) {
        Ticket ticket = new Ticket();
        ticket.setId(ULong.valueOf(id));
        return ticket;
    }

    private void campaignIntakeFailsWith(HttpStatus status) {
        when(ticketService.createForCampaign(any()))
                .thenReturn(Mono.error(new GenericException(status, "rejected with " + status)));
    }

    @Test
    @DisplayName("A new campaign lead returns 200 with the created ticket")
    void newCampaignLead() {
        when(ticketService.createForCampaign(any())).thenReturn(Mono.just(ticketWithId(101)));

        ResponseEntity<Object> response =
                controller.createFromCampaigns(new CampaignTicketRequest()).block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertInstanceOf(Ticket.class, response.getBody());
        assertEquals(ULong.valueOf(101), ((Ticket) response.getBody()).getId());
    }

    /**
     * The body has to be non-empty, and that is not cosmetic. The collector forwards the lead here
     * and reads the reply with {@code bodyToMono(Map)}, which completes empty on a zero-length body;
     * the forward chain then held a null and threw, so a duplicate the processor had already
     * recorded still came back to the caller as a 500.
     */
    @Test
    @DisplayName("A duplicate campaign lead is acknowledged with 200 and a non-empty marker body")
    void duplicateCampaignLeadIsAcknowledged() {
        campaignIntakeFailsWith(HttpStatus.CONFLICT);

        ResponseEntity<Object> response =
                controller.createFromCampaigns(new CampaignTicketRequest()).block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("duplicate", Boolean.TRUE), response.getBody());
        assertFalse(
                response.getBody().toString().contains("id"),
                "the existing ticket id must not be handed to a third party");
    }

    @Test
    @DisplayName("A duplicate website lead is acknowledged with 200 and a non-empty marker body")
    void duplicateWebsiteLeadIsAcknowledged() {
        when(ticketService.createForWebsite(any(), eq(PRODUCT_CODE)))
                .thenReturn(Mono.error(new GenericException(HttpStatus.CONFLICT, "duplicate")));

        ResponseEntity<Object> response = controller
                .createFromWebsite(PRODUCT_CODE, new CampaignTicketRequest())
                .block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("duplicate", Boolean.TRUE), response.getBody());
    }

    /**
     * The lead never landed in these three, so a 200 would drop it silently. An inactive product is
     * the realistic one: a campaign left running on Meta after someone deactivates the product.
     */
    @Test
    @DisplayName("An inactive product is reported, not acknowledged")
    void inactiveProductStillFails() {
        campaignIntakeFailsWith(HttpStatus.BAD_REQUEST);

        GenericException e = assertThrows(
                GenericException.class,
                () -> controller.createFromCampaigns(new CampaignTicketRequest()).block());
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    @DisplayName("An unknown campaign is reported, keeping a mis-pointed integration visible")
    void unknownCampaignStillFails() {
        campaignIntakeFailsWith(HttpStatus.NOT_FOUND);

        GenericException e = assertThrows(
                GenericException.class,
                () -> controller.createFromCampaigns(new CampaignTicketRequest()).block());
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    @DisplayName("A server error is reported, so the provider retries it")
    void serverErrorStillFails() {
        campaignIntakeFailsWith(HttpStatus.INTERNAL_SERVER_ERROR);

        GenericException e = assertThrows(
                GenericException.class,
                () -> controller.createFromCampaigns(new CampaignTicketRequest()).block());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatusCode());
    }
}
