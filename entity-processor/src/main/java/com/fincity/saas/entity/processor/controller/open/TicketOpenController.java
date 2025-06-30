package com.fincity.saas.entity.processor.controller.open;

import com.fincity.saas.entity.processor.model.request.ticket.TicketRequest;
import com.fincity.saas.entity.processor.model.response.ProcessorResponse;
import com.fincity.saas.entity.processor.service.TicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/open/tickets")
public class TicketOpenController {

    public static final String REQ_PATH = "/req";
    private final TicketService ticketService;

    public TicketOpenController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<ProcessorResponse>> createFromRequest(@RequestBody TicketRequest ticketRequest) {
        return this.ticketService.createOpenResponse(ticketRequest).map(ResponseEntity::ok);
    }
}
