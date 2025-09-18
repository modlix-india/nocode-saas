package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseProcessorController;
import com.fincity.saas.entity.processor.dao.TicketDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.CampaignRequest;
import com.fincity.saas.entity.processor.model.request.CampaignTicketRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketReassignRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketStatusRequest;
import com.fincity.saas.entity.processor.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/tickets")
public class TicketController
        extends BaseProcessorController<EntityProcessorTicketsRecord, Ticket, TicketDAO, TicketService> {

    public static final String CAMPAIGN_REQ_PATH = REQ_PATH + "/campaigns";
    public static final String WEBSITE_REQ_PATH = REQ_PATH + "/website/{" + PATH_VARIABLE_CODE + "}";
    public static final String TOKEN = "token@123";

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<Ticket>> createFromRequest(@RequestBody TicketRequest ticketRequest) {
        return this.service.create(ticketRequest).map(ResponseEntity::ok);
    }

    @PatchMapping(REQ_PATH_ID + "/stage")
    public Mono<ResponseEntity<Ticket>> updateStageStatus(
            @PathVariable(PATH_VARIABLE_ID) Identity identity, @RequestBody TicketStatusRequest ticketStatusRequest) {
        return this.service.updateStageStatus(identity, ticketStatusRequest).map(ResponseEntity::ok);
    }

    @PatchMapping(REQ_PATH_ID + "/reassign")
    public Mono<ResponseEntity<Ticket>> reassignTicket(
            @PathVariable(PATH_VARIABLE_ID) Identity identity,
            @RequestBody TicketReassignRequest ticketReassignRequest) {
        return this.service.reassignTicket(identity, ticketReassignRequest).map(ResponseEntity::ok);
    }

    @PostMapping(CAMPAIGN_REQ_PATH)
    public Mono<ResponseEntity<Ticket>> createFromCampaigns(@RequestBody CampaignTicketRequest campaignTicketRequest) {
        return this.service.createForCampaign(campaignTicketRequest).map(ResponseEntity::ok);
    }

    @GetMapping(CAMPAIGN_REQ_PATH)
    public Mono<ResponseEntity<String>> verifyCreateFromCampaign(
            @RequestParam(name = "hub.mode") String mode,
            @RequestParam(name = "hub.verify_token") String verifyToken,
            @RequestParam(name = "hub.challenge") String challenge) {

        return Mono.just(
                "subscribe".equals(mode) && TOKEN.equals(verifyToken)
                        ? ResponseEntity.ok(challenge)
                        : ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification token mismatch"));
    }

    @PostMapping(WEBSITE_REQ_PATH)
    public Mono<ResponseEntity<Ticket>> createFromWebsite(
            @PathVariable(PATH_VARIABLE_CODE) String code, @RequestBody CampaignTicketRequest ticketRequest) {
        return this.service.createForWebsite(ticketRequest, code).map(ResponseEntity::ok);
    }

    @GetMapping(WEBSITE_REQ_PATH)
    public Mono<ResponseEntity<String>> verifyCreateFromWebsite(
            @RequestParam(name = "hub.mode") String mode,
            @RequestParam(name = "hub.verify_token") String verifyToken,
            @RequestParam(name = "hub.challenge") String challenge) {

        return Mono.just(
                "subscribe".equals(mode) && TOKEN.equals(verifyToken)
                        ? ResponseEntity.ok(challenge)
                        : ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification token mismatch"));
    }
}
