package com.fincity.saas.entity.processor.service;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.TicketDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.request.TicketRequest;
import com.fincity.saas.entity.processor.model.response.ProcessorResponse;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

@Service
public class TicketService extends BaseProcessorService<EntityProcessorTicketsRecord, Ticket, TicketDAO> {

    private static final String TICKET_CACHE = "ticket";

    private final OwnerService ownerService;
    private final ProductService productService;
    private final StageService stageService;
    private final ProductStageRuleService productStageRuleService;

    public TicketService(
            OwnerService ownerService,
            ProductService productService,
            StageService stageService,
            ProductStageRuleService productStageRuleService) {
        this.ownerService = ownerService;
        this.productService = productService;
        this.stageService = stageService;
        this.productStageRuleService = productStageRuleService;
    }

    @Override
    protected String getCacheName() {
        return TICKET_CACHE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET;
    }

    @Override
    protected Mono<Ticket> checkEntity(Ticket ticket, Tuple3<String, String, ULong> accessInfo) {
        return this.checkTicket(ticket, accessInfo).flatMap(uEntity -> this.setOwner(accessInfo, uEntity));
    }

    @Override
    protected Mono<Ticket> updatableEntity(Ticket ticket) {

        return FlatMapUtil.flatMapMono(() -> this.updateOwner(ticket), super::updatableEntity, (uEntity, existing) -> {
            existing.setDialCode(ticket.getDialCode());
            existing.setPhoneNumber(ticket.getPhoneNumber());
            existing.setEmail(ticket.getEmail());
            existing.setSource(ticket.getSource());
            existing.setSubSource(ticket.getSubSource());
            existing.setStage(ticket.getStage());
            existing.setStatus(ticket.getStatus());

            return Mono.just(existing);
        });
    }

    public Mono<Ticket> create(TicketRequest ticketRequest) {

        Ticket ticket = Ticket.of(ticketRequest);

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> this.productService.checkAndUpdateIdentity(ticketRequest.getProductId()),
                (hasAccess, productIdentity) -> Mono.just(ticket.setProductId(productIdentity.getULongId())),
                (hasAccess, productIdentity, pTicket) -> super.createInternal(pTicket, hasAccess));
    }

    public Mono<ProcessorResponse> createResponse(TicketRequest ticketRequest) {
        return FlatMapUtil.flatMapMono(
                () -> this.create(ticketRequest),
                cTicket -> Mono.just(ProcessorResponse.ofCreated(cTicket.getCode(), this.getEntitySeries())));
    }

    private Mono<Ticket> checkTicket(Ticket ticket, Tuple3<String, String, ULong> accessInfo) {

        if (ticket.getProductId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    productService.getEntityName());

        return FlatMapUtil.flatMapMono(
                () -> this.productService.readById(ticket.getProductId()),
                product -> this.setDefaultStage(ticket, product.getProductTemplateId()),
                (product, sTicket) -> productStageRuleService.getUserAssignment(
                        accessInfo.getT1(),
                        accessInfo.getT2(),
                        product.getId(),
                        sTicket.getStage(),
                        this.getEntityTokenPrefix(accessInfo.getT1()),
                        sTicket.toJson()),
                (product, sTicket, userId) -> this.setTicketAssignment(sTicket, userId));
    }

    private Mono<Ticket> setDefaultStage(Ticket ticket, ULong productTemplateId) {
        return FlatMapUtil.flatMapMonoWithNull(
                () -> stageService.getFirstStage(ticket.getAppCode(), ticket.getClientCode(), productTemplateId),
                stage -> stageService.getFirstStatus(
                        ticket.getAppCode(), ticket.getClientCode(), productTemplateId, stage.getId()),
                (stage, status) -> {
                    ticket.setStatus(status.getId());
                    ticket.setStatus(status.getId());

                    return Mono.just(ticket);
                });
    }

    private Mono<Ticket> setOwner(Tuple3<String, String, ULong> accessInfo, Ticket ticket) {
        return this.ownerService.getOrCreateTicketOwner(accessInfo, ticket);
    }

    private Mono<Ticket> updateOwner(Ticket ticket) {
        return this.ownerService.getOrCreateTicketPhoneOwner(ticket.getAppCode(), ticket.getClientCode(), ticket);
    }

    private Mono<Ticket> setTicketAssignment(Ticket ticket, ULong userId) {
        ticket.setAssignedUserId(userId);
        return Mono.just(ticket);
    }
}
