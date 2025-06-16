package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.entity.processor.dao.TicketDAO;
import com.fincity.saas.entity.processor.dto.Owner;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.TicketRequest;
import com.fincity.saas.entity.processor.model.response.ProcessorResponse;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import org.jooq.types.ULong;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class TicketService extends BaseProcessorService<EntityProcessorTicketsRecord, Ticket, TicketDAO> {

    private static final String TICKET_CACHE = "ticket";

    private final OwnerService ownerService;
    private final ProductService productService;
    private final StageService stageService;
    private final ProductStageRuleService productStageRuleService;
    private final ActivityService activityService;

    public TicketService(
            @Lazy OwnerService ownerService,
            ProductService productService,
            StageService stageService,
            ProductStageRuleService productStageRuleService,
            ActivityService activityService) {
        this.ownerService = ownerService;
        this.productService = productService;
        this.stageService = stageService;
        this.productStageRuleService = productStageRuleService;
        this.activityService = activityService;
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
    protected Mono<Ticket> checkEntity(Ticket ticket, ProcessorAccess access) {
        return this.checkTicket(ticket, access)
                .flatMap(uEntity -> this.setOwner(access, uEntity))
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
                    .flatMap(access -> setOwner(access, ticket))
                    .map(owner -> ProcessorResponse.ofCreated(owner.getCode(), owner.getEntitySeries()));

        return FlatMapUtil.flatMapMono(
                        super::hasPublicAccess,
                        access -> productService.updateIdentity(ticketRequest.getProductId()),
                        (access, productIdentity) -> Mono.just(ticket.setProductId(productIdentity.getULongId())),
                        (access, productIdentity, pTicket) -> super.createInternal(access, pTicket))
                .map(created -> ProcessorResponse.ofCreated(created.getCode(), created.getEntitySeries()));
    }

    public Mono<Ticket> create(TicketRequest ticketRequest) {

        Ticket ticket = Ticket.of(ticketRequest);

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.productService.checkAndUpdateIdentityWithAccess(access, ticketRequest.getProductId()),
                (access, productIdentity) -> this.checkDuplicate(
                                access.getAppCode(), access.getClientCode(), ticketRequest),
                (access, productIdentity, isDuplicate) -> Mono.just(ticket.setProductId(productIdentity.getULongId())),
                (access, productIdentity, isDuplicate, pTicket) -> super.createInternal(access, pTicket),
                (access, productIdentity, isDuplicate, pTicket, created) ->
                        this.activityService.acCreate(created).thenReturn(created));
    }

    private Mono<Ticket> checkTicket(Ticket ticket, ProcessorAccess access) {

        if (ticket.getProductId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    productService.getEntityName());

        return FlatMapUtil.flatMapMonoWithNull(
                () -> this.productService.readById(ticket.getProductId()),
                product -> this.setDefaultStage(
                        ticket, access.getAppCode(), access.getClientCode(), product.getProductTemplateId()),
                (product, sTicket) -> productStageRuleService.getUserAssignment(
                        access.getAppCode(),
                        access.getClientCode(),
                        product.getId(),
                        sTicket.getStage(),
                        this.getEntityPrefix(access.getAppCode()),
                        access.getUserId(),
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

    private Mono<Boolean> checkDuplicate(String appCode, String clientCode, TicketRequest ticketRequest) {
        return this.dao
                .readByNumberAndEmail(
                        appCode,
                        clientCode,
                        ticketRequest.getProductId().getULongId(),
                        ticketRequest.getPhoneNumber().getCountryCode(),
                        ticketRequest.getPhoneNumber().getNumber(),
                        ticketRequest.getEmail().getAddress())
                .flatMap(existing -> {
                    if (existing.getId() != null)
                        return activityService
                                .acReInquiry(existing, ticketRequest)
                                .then(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.DUPLICATE_TICKET,
                                        this.getEntityPrefix(appCode),
                                        existing.getCode(),
                                        this.getEntityPrefix(appCode)));
                    return Mono.just(Boolean.FALSE);
                });
    }

    private Mono<Owner> setOwner(ProcessorAccess access, Ticket ticket) {
        return this.ownerService.getOrCreateTicketOwner(access, ticket);
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
