package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseProcessorController;
import com.fincity.saas.entity.processor.dao.TicketDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.ticket.TicketReassignRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketStatusRequest;
import com.fincity.saas.entity.processor.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public Mono<ResponseEntity<Ticket>> createFromRequest(@Valid @RequestBody TicketRequest ticketRequest) {
        return this.service.create(ticketRequest).map(ResponseEntity::ok);
    }

    @PatchMapping(REQ_PATH_ID + "/stage")
    public Mono<ResponseEntity<Ticket>> updateStageStatus(
            @PathVariable(PATH_VARIABLE_ID) Identity identity,
            @Valid @RequestBody TicketStatusRequest ticketStatusRequest) {
        return this.service.updateStageStatus(identity, ticketStatusRequest).map(ResponseEntity::ok);
    }

    @PatchMapping(REQ_PATH_ID + "/reassign")
    public Mono<ResponseEntity<Ticket>> reassignTicket(
            @PathVariable(PATH_VARIABLE_ID) Identity identity,
            @RequestBody TicketReassignRequest ticketReassignRequest) {
        return this.service.reassignTicket(identity, ticketReassignRequest).map(ResponseEntity::ok);
    }
}
