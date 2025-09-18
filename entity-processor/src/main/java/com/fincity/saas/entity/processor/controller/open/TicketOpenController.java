package com.fincity.saas.entity.processor.controller.open;

import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.model.request.CampaignTicketRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketRequest;
import com.fincity.saas.entity.processor.model.response.ProcessorResponse;
import com.fincity.saas.entity.processor.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/open/tickets")
public class TicketOpenController {

    public static final String REQ_PATH = "/req";
    public static final String CAMPAIGN_REQ_PATH = REQ_PATH + "/campaigns";
    public static final String PATH_VARIABLE_CODE = "code";
    public static final String WEBSITE_REQ_PATH = REQ_PATH + "/website/{" + PATH_VARIABLE_CODE + "}";

    public static final String TOKEN = "token@123";
    private final TicketService ticketService;

    public TicketOpenController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<ProcessorResponse>> createFromRequest(@RequestBody TicketRequest ticketRequest) {
        return this.ticketService.createOpenResponse(ticketRequest).map(ResponseEntity::ok);
    }

    @PostMapping(CAMPAIGN_REQ_PATH)
    public Mono<ResponseEntity<Ticket>> createFromCampaigns(@RequestBody CampaignTicketRequest campaignTicketRequest) {
        return this.ticketService.createForCampaign(campaignTicketRequest).map(ResponseEntity::ok);
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
        return this.ticketService.createForWebsite(ticketRequest, code).map(ResponseEntity::ok);
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
