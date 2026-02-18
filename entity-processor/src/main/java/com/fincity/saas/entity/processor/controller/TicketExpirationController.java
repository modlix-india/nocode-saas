package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.dto.ExpireTicketsResult;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.TicketExpirationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/tickets/expiration")
public class TicketExpirationController {

    private final TicketExpirationService ticketExpirationService;

    public TicketExpirationController(TicketExpirationService ticketExpirationService) {
        this.ticketExpirationService = ticketExpirationService;
    }

    @PostMapping("/run")
    public Mono<ResponseEntity<ExpireTicketsResult>> runExpiration(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode) {
        ProcessorAccess access = ProcessorAccess.of(appCode, clientCode, true, null, null);
        return ticketExpirationService.runExpiration(access).map(ResponseEntity::ok);
    }
}
