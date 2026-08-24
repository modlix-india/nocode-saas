package com.fincity.saas.entity.processor.controller.message;

import com.fincity.saas.entity.processor.dto.message.Call;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.service.message.TicketCallLogService;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * The only public way into a deal's calls. Thin by design: the access check lives in the service,
 * per this codebase's convention that authorization belongs on the service layer.
 */
@RestController
@RequestMapping("api/entity/processor/calls")
public class TicketCallLogController {

    private final TicketCallLogService service;

    public TicketCallLogController(TicketCallLogService service) {
        this.service = service;
    }

    /**
     * A deal's call log.
     *
     * <p>Returns the calls for every deal sharing the customer's number that the caller can see, not
     * just this deal's slice, matching how the WhatsApp thread reads. The customer made one set of
     * calls; splitting them per deal would show the agent a fragment.
     */
    @GetMapping("/{ticketId}")
    public Mono<ResponseEntity<Page<Map<String, Object>>>> readTicketCalls(
            @PathVariable("ticketId") Identity ticketId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return this.service.readTicketCalls(ticketId, PageRequest.of(page, size)).map(ResponseEntity::ok);
    }

    /**
     * Places a call to the deal's customer.
     *
     * <p>The number is taken from the deal, not the body. A caller can therefore only ring the
     * customers of deals they can already see, which is the whole point of moving this endpoint:
     * the one it replaces would dial anything it was given.
     */
    @PostMapping("/{ticketId}/make")
    public Mono<ResponseEntity<Call>> makeCall(
            @PathVariable("ticketId") Identity ticketId,
            @RequestBody(required = false) Map<String, Object> request) {

        Map<String, Object> body = request == null ? Map.of() : request;

        return this.service
                .makeCall(ticketId, asString(body.get("connectionName")), asString(body.get("callerId")))
                .map(ResponseEntity::ok);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
