package com.fincity.saas.entity.processor.controller.message;

import com.fincity.saas.entity.processor.model.request.message.CallEventRequest;
import com.fincity.saas.entity.processor.service.message.TicketCallLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Where the message service hands over call events for numbers this service owns.
 *
 * <p>Behind {@code /internal}, which in this codebase means permitAll at the application layer and
 * blocked by nginx, the same arrangement as the WhatsApp inbound endpoint next door.
 *
 * <p>Worth being straight about the difference, though. The WhatsApp handoff is trustworthy because
 * the message service verifies Meta's HMAC over the raw webhook body before dispatching. Exotel
 * offers no equivalent signature, and its callbacks land on a permitAll path with caller-supplied
 * app and client codes, so an event reaching here has been proven to come from Exotel only insofar as
 * the network path is closed. What limits the damage is that this endpoint cannot create a deal: an
 * event for an unknown call gets a row with no deal attached, so a forged callback can add noise to
 * a call log but cannot manufacture a lead or reach anything deal-scoped.
 *
 * <p>A non-2xx is meaningful. The caller keeps its outbox row and retries with backoff, so failing
 * loudly here is correct and swallowing an error is not.
 */
@RestController
@RequestMapping("api/entity/processor/calls/internal")
public class CallEventController {

    private final TicketCallLogService service;

    public CallEventController(TicketCallLogService service) {
        this.service = service;
    }

    @PostMapping("/event")
    public Mono<ResponseEntity<Void>> accept(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestBody CallEventRequest request) {
        return this.service
                .accept(appCode, clientCode, request)
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }
}
