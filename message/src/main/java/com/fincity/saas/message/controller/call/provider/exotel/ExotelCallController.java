package com.fincity.saas.message.controller.call.provider.exotel;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.dto.call.provider.exotel.ExotelCall;
import com.fincity.saas.message.model.request.call.BrowserDialRequest;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.model.request.call.IncomingCallRequest;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelConnectAppletResponse;
import com.fincity.saas.message.service.call.provider.exotel.ExotelCallService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * The three Exotel call operations, and nothing else.
 *
 * <p>Hand-written rather than extending {@code BaseUpdatableController}, for the same reason
 * {@code ProvisioningController} is: the base class brings a full generic data API —
 * {@code GET /{id}}, {@code /code/{code}}, {@code /eager}, {@code POST /eager/query},
 * {@code PUT /code/{code}}, {@code DELETE /req/{id}} — scoped by nothing but app and client. Every
 * row in {@code message_exotel_calls} carries {@code recordingUrl}, {@code customerPhoneNumber},
 * both call legs and {@code price}, so that API let any authenticated user in the tenant enumerate,
 * rewrite and delete the whole tenant's call history including its recordings. The eager paths
 * return {@code rec.intoMap()} straight off the JOOQ record, so no Jackson annotation on the DTO
 * could have filtered them.
 *
 * <p>Nothing consumed that API: entity-processor reaches this service only through the two
 * {@code /internal} routes below, and the CRM reads call history through
 * {@code TicketCallLogService}, which applies the deal-visibility rules this class cannot see.
 * Call history is legitimately readable there, per deal, by users who can see the deal.
 */
@RestController
@RequestMapping("/api/message/call/exotel")
public class ExotelCallController {

    private final ExotelCallService service;

    public ExotelCallController(ExotelCallService service) {
        this.service = service;
    }

    /**
     * Answers the Connect applet with the destination an inbound call should ring.
     *
     * <p>Behind {@code /internal} because entity-processor is its only caller. Exotel never reaches
     * this route: the provider posts to {@code /api/entity/processor/open/call}, which resolves the
     * assigned user and then calls this over Feign. Being {@code permitAll} was therefore not
     * required for inbound calling to work, and cost real exposure — the route takes {@code appCode}
     * and {@code clientCode} from headers and {@code userId} from the body, resolves that user via
     * {@code getUserInternal(userId, null)} with no client scoping at all, and returns their phone
     * number in {@code destination.numbers}. Unauthenticated, that is a phone-number lookup over
     * every user on the platform, plus a forged call row per request with an attacker-chosen
     * {@code CallSid}.
     */
    @PostMapping("/internal/connect")
    public Mono<ExotelConnectAppletResponse> connectCall(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestBody IncomingCallRequest request) {
        return this.service.connectCall(appCode, clientCode, request);
    }

    @PostMapping("/make")
    public Mono<ResponseEntity<Call>> makeCall(@RequestBody CallRequest request) {
        return this.service.makeCall(request).map(ResponseEntity::ok);
    }

    /**
     * Places a call for an agent's browser, for a service that has already checked the deal.
     *
     * <p>Behind {@code /internal} and that is load-bearing. The caller supplies both the agent and
     * the customer's number, and this service verifies neither: it cannot see deals, so it has no
     * way to confirm the agent may ring that number. entity-processor does that check, reading the
     * ticket under the caller's own access before calling here. Reachable from a browser, this would
     * let any provisioned agent dial any number on the tenant's account and have it logged against
     * whichever deal they named.
     *
     * <p>Returns the provider-shaped call, exactly as {@code /internal/make} does and for the same
     * reason: the caller keys its own record on the provider's {@code Sid}, and the neutral call has
     * nowhere to carry one. Returning the neutral form left every row on the calling side with a null
     * provider call id, so the callback matched nothing and recorded each call twice.
     */
    @PostMapping("/internal/browser-dial")
    public Mono<ResponseEntity<ExotelCall>> browserDialInternal(
            @RequestParam String appCode, @RequestParam String clientCode, @RequestBody BrowserDialRequest request) {
        return this.service
                .browserDialInternal(
                        appCode,
                        clientCode,
                        request.getConnectionName(),
                        ULongUtil.valueOf(request.getUserId()),
                        request.getToNumber())
                .map(ResponseEntity::ok);
    }

    /**
     * Places a call for a service that has already checked the caller may make it.
     *
     * <p>Returns the provider-shaped call rather than the thin wrapper above, because the caller
     * needs the provider's Sid to key its own record on, and its callbacks are routed back by the
     * owner recorded here.
     *
     * <p>Behind {@code /internal}: permitAll at the application layer, blocked by nginx. That is
     * load-bearing rather than incidental, because this endpoint places real, billable calls with no
     * check of its own.
     */
    @PostMapping("/internal/make")
    public Mono<ResponseEntity<ExotelCall>> makeCallInternal(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestBody CallRequest request,
            @RequestParam(required = false, defaultValue = "entity-processor") String ownerService) {
        return this.service
                .makeCallInternal(appCode, clientCode, request, ownerService)
                .map(ResponseEntity::ok);
    }
}
