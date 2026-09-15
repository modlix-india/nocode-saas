package com.fincity.saas.entity.processor.controller.open;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.jooq.enums.EntityProcessorIntegrationsInSourceType;
import com.fincity.saas.entity.processor.model.request.CampaignTicketRequest;
import com.fincity.saas.entity.processor.service.EntityIntegrationService;
import com.fincity.saas.entity.processor.service.TicketService;
import org.springframework.beans.factory.annotation.Value;
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

    private static final String SUBSCRIBE = "subscribe";
    private static final String MISMATCH_BODY = "Verification token mismatch";

    private final TicketService ticketService;
    private final EntityIntegrationService entityIntegrationService;

    /** Fallback when no per-tenant integration matches; preserves pre-Phase-5 behaviour. */
    @Value("${meta.webhook.verify-token:token@123}")
    private String fallbackToken;

    public TicketOpenController(TicketService ticketService, EntityIntegrationService entityIntegrationService) {
        this.ticketService = ticketService;
        this.entityIntegrationService = entityIntegrationService;
    }

    @PostMapping(CAMPAIGN_REQ_PATH)
    public Mono<ResponseEntity<Ticket>> createFromCampaigns(@RequestBody CampaignTicketRequest campaignTicketRequest) {
        return this.ticketService
                .createForCampaign(campaignTicketRequest)
                .map(ResponseEntity::ok)
                .onErrorResume(GenericException.class, TicketOpenController::acknowledgeDuplicate);
    }

    @GetMapping(CAMPAIGN_REQ_PATH)
    public Mono<ResponseEntity<String>> verifyCreateFromCampaign(
            @RequestParam(name = "hub.mode") String mode,
            @RequestParam(name = "hub.verify_token") String verifyToken,
            @RequestParam(name = "hub.challenge") String challenge) {
        return verifyChallenge(mode, verifyToken, challenge);
    }

    @PostMapping(WEBSITE_REQ_PATH)
    public Mono<ResponseEntity<Ticket>> createFromWebsite(
            @PathVariable(PATH_VARIABLE_CODE) String code, @RequestBody CampaignTicketRequest ticketRequest) {
        return this.ticketService
                .createForWebsite(ticketRequest, code)
                .map(ResponseEntity::ok)
                .onErrorResume(GenericException.class, TicketOpenController::acknowledgeDuplicate);
    }

    /**
     * Acknowledges a duplicate lead with 200 so Meta and Google stop redelivering it.
     *
     * <p>By the time a 409 reaches here the processor has already committed the re-inquiry activity
     * against the existing ticket, so the intake succeeded and only the response was wrong.
     * Providers read any non-2xx as a failed webhook delivery and redeliver the same lead with
     * backoff, and each redelivery committed another re-inquiry row before failing again - so the
     * error response, not the dedup logic, is what turned one lead into a storm of re-inquiries.
     *
     * <p>Only 409 is acknowledged. Everything else still propagates, deliberately: a 404 keeps a
     * mis-pointed campaign visible, an inactive product or missing identity info stays a real
     * error instead of a silently dropped lead, and a 5xx stays an error precisely because
     * retrying it may succeed.
     *
     * <p>The re-inquiry activity row is the durable record of this outcome, so nothing is logged
     * here. Returning an empty body also keeps the internal ticket id and stack trace out of a
     * third party's hands.
     */
    private static Mono<ResponseEntity<Ticket>> acknowledgeDuplicate(GenericException e) {

        if (e.getStatusCode() != HttpStatus.CONFLICT) return Mono.error(e);

        return Mono.just(ResponseEntity.ok().build());
    }

    @GetMapping(WEBSITE_REQ_PATH)
    public Mono<ResponseEntity<String>> verifyCreateFromWebsite(
            @RequestParam(name = "hub.mode") String mode,
            @RequestParam(name = "hub.verify_token") String verifyToken,
            @RequestParam(name = "hub.challenge") String challenge) {
        return verifyChallenge(mode, verifyToken, challenge);
    }

    /**
     * Webhook handshake: accept if the {@code hub.verify_token} matches any
     * active integration row's {@code PRIMARY_VERIFY_TOKEN}/{@code SECONDARY_VERIFY_TOKEN}.
     * Falls back to a configured global token to preserve pre-Phase-5 setups.
     */
    private Mono<ResponseEntity<String>> verifyChallenge(String mode, String verifyToken, String challenge) {
        if (!SUBSCRIBE.equals(mode)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(MISMATCH_BODY));
        }
        return this.entityIntegrationService
                .findActiveByVerifyToken(verifyToken, EntityProcessorIntegrationsInSourceType.FACEBOOK_FORM)
                .map(integration -> ResponseEntity.ok(challenge))
                .switchIfEmpty(Mono.fromSupplier(() -> verifyToken.equals(fallbackToken)
                        ? ResponseEntity.ok(challenge)
                        : ResponseEntity.status(HttpStatus.FORBIDDEN).body(MISMATCH_BODY)));
    }
}
