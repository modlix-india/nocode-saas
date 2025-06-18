package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseProcessorController;
import com.fincity.saas.entity.processor.dao.TicketDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.request.TicketRequest;
import com.fincity.saas.entity.processor.service.TicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/tickets")
public class TicketController
        extends BaseProcessorController<EntityProcessorTicketsRecord, Ticket, TicketDAO, TicketService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<Ticket>> createFromRequest(@RequestBody TicketRequest ticketRequest) {
        return this.service.create(ticketRequest).map(ResponseEntity::ok);
    }
}
