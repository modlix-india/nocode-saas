package com.fincity.saas.entity.processor.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.entity.processor.controller.base.BaseProcessorController;
import com.fincity.saas.entity.processor.dao.TicketDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.product.ProductComm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.ticket.TicketPartnerRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketBulkReassignRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketReassignRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketStatusRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketTagRequest;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import com.fincity.saas.entity.processor.service.TicketService;
import org.jooq.types.ULong;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/tickets")
public class TicketController
        extends BaseProcessorController<EntityProcessorTicketsRecord, Ticket, TicketDAO, TicketService> {

    public static final String DCRM_REQ_PATH = REQ_PATH + "/DCRM";

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<Ticket>> createRequest(@RequestBody TicketRequest ticketRequest) {
        return this.service.createRequest(ticketRequest).map(ResponseEntity::ok);
    }

    @PatchMapping(REQ_PATH_ID + "/stage")
    public Mono<ResponseEntity<Ticket>> updateStageStatus(
            @PathVariable(PATH_VARIABLE_ID) Identity identity, @RequestBody TicketStatusRequest ticketStatusRequest) {
        return this.service.updateStageStatus(identity, ticketStatusRequest).map(ResponseEntity::ok);
    }

    @PatchMapping(REQ_PATH_ID + "/tag")
    public Mono<ResponseEntity<Ticket>> updateTag(
            @PathVariable(PATH_VARIABLE_ID) Identity identity, @RequestBody TicketTagRequest ticketTagRequest) {
        return this.service.updateTag(identity, ticketTagRequest).map(ResponseEntity::ok);
    }

    @PatchMapping(REQ_PATH_ID + "/reassign")
    public Mono<ResponseEntity<Ticket>> reassignTicket(
            @PathVariable(PATH_VARIABLE_ID) Identity identity,
            @RequestBody TicketReassignRequest ticketReassignRequest) {
        return this.service.reassignTicket(identity, ticketReassignRequest).map(ResponseEntity::ok);
    }

    @GetMapping(REQ_PATH_ID + "/product-comms")
    public Mono<ResponseEntity<ProductComm>> getTicketProductComm(
            @PathVariable(PATH_VARIABLE_ID) Identity identity,
            @RequestParam("connectionType") ConnectionType connectionType,
            @RequestParam("connectionSubType") ConnectionSubType connectionSubType) {
        return this.service
                .getTicketProductComm(identity, connectionType, connectionSubType)
                .map(ResponseEntity::ok);
    }

    @PostMapping(DCRM_REQ_PATH)
    public Mono<ResponseEntity<Ticket>> createFromWebsite(
            @RequestHeader String appCode,
            @RequestHeader String clientCode,
            @RequestBody TicketPartnerRequest ticketPartnerRequest) {
        return this.service
                .createForPartnerImportDCRM(appCode, clientCode, ticketPartnerRequest)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/internal" + PATH_ID)
    public Mono<ResponseEntity<Ticket>> getTicketInternal(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @PathVariable(PATH_VARIABLE_ID) Identity identity) {
        return this.service
                .readByIdentity(ProcessorAccess.of(appCode, clientCode, Boolean.TRUE, null, null), identity)
                .map(ResponseEntity::ok);
    }

    /**
     * Called by the message service whenever a WhatsApp message is exchanged, in either direction.
     * Answers which deal the message belongs to, and moves every deal on that customer's number to
     * the top of the conversation list.
     *
     * <p>A POST rather than a lookup because it writes: it bumps {@code LAST_MESSAGE_AT}, and with
     * {@code createIfMissing} it creates a deal for a customer who has none. Returns 204 when
     * nothing matched and creation was not asked for, in which case the message is still stored but
     * belongs to no deal.
     */
    @PostMapping("/internal/whatsapp/register")
    public Mono<ResponseEntity<Ticket>> registerWhatsappMessage(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestParam(value = "productId", required = false) ULong productId,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam(value = "occurredAt", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurredAt,
            @RequestParam(value = "createIfMissing", defaultValue = "false") boolean createIfMissing) {
        return this.service
                .registerWhatsappMessage(
                        appCode, clientCode, productId, PhoneNumber.of(phoneNumber), occurredAt, createIfMissing)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.noContent().build());
    }

    @PostMapping("/users/query")
    public Mono<ResponseEntity<List<User>>> tiketsUserQuery(@RequestBody Query query, ServerHttpRequest request) {
        String timezone = this.extractTimezone(request);
        return this.service.readTicketUsers(query, timezone).map(ResponseEntity::ok);
    }

    @PatchMapping("/bulk-reassign")
    public Mono<ResponseEntity<Integer>> bulkReassign(
            @RequestBody TicketBulkReassignRequest request, ServerHttpRequest serverRequest) {
        String timezone = this.extractTimezone(serverRequest);
        return this.service
                .bulkReassignTickets(request.getQuery(), request.getUserIds(), request.getComment(), timezone)
                .map(ResponseEntity::ok);
    }
}
