package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.TicketDAO;
import com.fincity.saas.entity.processor.dto.Owner;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.request.TicketRequest;
import com.fincity.saas.entity.processor.model.response.ProcessorResponse;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import org.jooq.types.ULong;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
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
            @Lazy OwnerService ownerService,
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
        return this.checkTicket(ticket, accessInfo)
                .flatMap(uEntity -> this.setOwner(accessInfo, uEntity))
                .flatMap(owner -> this.updateTicketFromOwner(ticket, owner));
    }

    @Override
    protected Mono<Ticket> updatableEntity(Ticket ticket) {
        return super.updatableEntity(ticket).flatMap(existing -> {
            existing.setStage(ticket.getStage());
            existing.setStatus(ticket.getStatus());

            return Mono.just(existing);
        });
    }

    public Flux<Ticket> updateOwnerTickets(Owner owner) {
        Flux<Ticket> ticketsFlux =
                this.dao.getAllOwnerTickets(owner.getId()).flatMap(ticket -> this.updateTicketFromOwner(ticket, owner));

        return this.dao.updateAll(ticketsFlux);
    }

    public Mono<ProcessorResponse> createOpenResponse(TicketRequest ticketRequest) {

        Ticket ticket = Ticket.of(ticketRequest);

        if (ticketRequest.getProductId() == null || ticketRequest.getProductId().isNull())
            return super.hasPublicAccess()
                    .flatMap(access -> setOwner(access.getT1(), ticket))
                    .map(owner -> ProcessorResponse.ofCreated(owner.getCode(), owner.getEntitySeries()));

        return FlatMapUtil.flatMapMono(
                        super::hasPublicAccess,
                        hasAccess -> productService.updateIdentity(ticketRequest.getProductId()),
                        (hasAccess, productIdentity) -> Mono.just(ticket.setProductId(productIdentity.getULongId())),
                        (hasAccess, productIdentity, pTicket) -> super.createInternal(pTicket, hasAccess))
                .map(created -> ProcessorResponse.ofCreated(created.getCode(), created.getEntitySeries()));
    }

    public Mono<Ticket> create(TicketRequest ticketRequest) {

        Ticket ticket = Ticket.of(ticketRequest);

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> this.productService.checkAndUpdateIdentity(ticketRequest.getProductId()),
                (hasAccess, productIdentity) -> Mono.just(ticket.setProductId(productIdentity.getULongId())),
                (hasAccess, productIdentity, pTicket) -> super.createInternal(pTicket, hasAccess));
    }

    private Mono<Ticket> checkTicket(Ticket ticket, Tuple3<String, String, ULong> accessInfo) {

        if (ticket.getProductId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    productService.getEntityName());

        return FlatMapUtil.flatMapMonoWithNull(
                () -> this.productService.readById(ticket.getProductId()),
                product -> this.setDefaultStage(
                        ticket, accessInfo.getT1(), accessInfo.getT2(), product.getProductTemplateId()),
                (product, sTicket) -> productStageRuleService.getUserAssignment(
                        accessInfo.getT1(),
                        accessInfo.getT2(),
                        product.getId(),
                        sTicket.getStage(),
                        this.getEntityTokenPrefix(accessInfo.getT1()),
                        accessInfo.getT3(),
                        sTicket.toJson()),
                (product, sTicket, userId) -> this.setTicketAssignment(sTicket, userId));
    }

    private Mono<Ticket> setDefaultStage(Ticket ticket, String appCode, String clientCode, ULong productTemplateId) {
        return FlatMapUtil.flatMapMonoWithNull(
                () -> stageService.getFirstStage(appCode, clientCode, productTemplateId),
                stage -> stageService.getFirstStatus(appCode, clientCode, productTemplateId, stage.getId()),
                (stage, status) -> {
                    ticket.setStage(stage.getId());
                    if (status != null) ticket.setStatus(status.getId());

                    return Mono.just(ticket);
                });
    }

    private Mono<Owner> setOwner(Tuple3<String, String, ULong> accessInfo, Ticket ticket) {
        return this.ownerService.getOrCreateTicketOwner(accessInfo, ticket);
    }

    private Mono<Ticket> updateTicketFromOwner(Ticket ticket, Owner owner) {
        ticket.setOwnerId(owner.getId());

        if (owner.getDialCode() != null && owner.getPhoneNumber() != null) {
            ticket.setDialCode(owner.getDialCode());
            ticket.setPhoneNumber(owner.getPhoneNumber());
        }

        if (owner.getEmail() != null) ticket.setEmail(owner.getEmail());

        return Mono.just(ticket);
    }

    private Mono<Ticket> setTicketAssignment(Ticket ticket, ULong userId) {
        // Only set assignedUserId if userId is not null and not 0
        if (userId != null && !userId.equals(ULong.valueOf(0))) ticket.setAssignedUserId(userId);

        return Mono.just(ticket);
    }
}
