package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.TicketDAO;
import com.fincity.saas.entity.processor.dto.Owner;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.content.NoteRequest;
import com.fincity.saas.entity.processor.model.request.content.TaskRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketReassignRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketStatusRequest;
import com.fincity.saas.entity.processor.model.response.ProcessorResponse;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import com.fincity.saas.entity.processor.service.content.NoteService;
import com.fincity.saas.entity.processor.service.content.TaskService;
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
    private final TaskService taskService;
    private final NoteService noteService;

    public TicketService(
            @Lazy OwnerService ownerService,
            ProductService productService,
            StageService stageService,
            ProductStageRuleService productStageRuleService,
            ActivityService activityService,
            @Lazy TaskService taskService,
            @Lazy NoteService noteService) {
        this.ownerService = ownerService;
        this.productService = productService;
        this.stageService = stageService;
        this.productStageRuleService = productStageRuleService;
        this.activityService = activityService;
        this.taskService = taskService;
        this.noteService = noteService;
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
                        access -> this.productService.updateIdentity(ticketRequest.getProductId()),
                        (access, productIdentity) -> Mono.just(ticket.setProductId(productIdentity.getULongId())),
                        (access, productIdentity, pTicket) -> super.createInternal(access, pTicket))
                .map(created -> ProcessorResponse.ofCreated(created.getCode(), created.getEntitySeries()));
    }

    public Mono<Ticket> create(TicketRequest ticketRequest) {

        Ticket ticket = Ticket.of(ticketRequest);

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.productService.checkAndUpdateIdentityWithAccess(access, ticketRequest.getProductId()),
                (access, productIdentity) ->
                        this.checkDuplicate(access.getAppCode(), access.getClientCode(), ticketRequest),
                (access, productIdentity, isDuplicate) -> Mono.just(ticket.setProductId(productIdentity.getULongId())),
                (access, productIdentity, isDuplicate, pTicket) -> super.createInternal(access, pTicket),
                (access, productIdentity, isDuplicate, pTicket, created) ->
                        this.activityService.acCreate(created).thenReturn(created));
    }

    public Mono<Ticket> updateStageStatus(Identity ticketId, TicketStatusRequest ticketStatusRequest) {

        return FlatMapUtil.flatMapMono(
                super::hasAccess, access -> super.readIdentityWithOwnerAccess(access, ticketId), (access, ticket) -> {
                    if (ticket.getStage()
                                    .equals(ticketStatusRequest.getStageId().getULongId())
                            && ticket.getStatus()
                                    .equals(ticketStatusRequest.getStatusId().getULongId())) return Mono.just(ticket);

                    return FlatMapUtil.flatMapMono(
                            () -> Mono.just(access),
                            pAccess -> Mono.just(ticket),
                            (pAccess, cTicket) -> this.stageService
                                    .getParentChild(
                                            pAccess,
                                            ticketStatusRequest.getStageId(),
                                            ticketStatusRequest.getStatusId())
                                    .switchIfEmpty(this.msgService.throwMessage(
                                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                            ProcessorMessageResourceService.INVALID_STAGE_STATUS)),
                            (pAccess, cTicket, stageStatusEntity) -> Mono.just(cTicket.getStage()),
                            (pAccess, cTicket, stageStatusEntity, oldStage) -> {
                                cTicket.setStage(stageStatusEntity.getT1().getId());
                                cTicket.setStatus(stageStatusEntity.getT2().getId());
                                return super.updateInternal(cTicket);
                            },
                            (pAccess, cTicket, stageStatusEntity, oldStage, uTicket) ->
                                    this.createTask(pAccess, ticketStatusRequest, uTicket),
                            (pAccess, cTicket, stageStatusEntity, oldStage, uTicket, cTask) -> this.activityService
                                    .acStageStatus(uTicket, ticketStatusRequest.getComment(), oldStage)
                                    .thenReturn(uTicket));
                });
    }

    private Mono<Boolean> createTask(ProcessorAccess access, TicketStatusRequest ticketStatusRequest, Ticket ticket) {
        if (!ticketStatusRequest.hasTask()) return Mono.just(Boolean.FALSE);

        TaskRequest taskRequest = ticketStatusRequest.getTaskRequest();

        taskRequest.setTicketId(ticket.getIdentity());
        taskRequest.setOwnerId(null);

        return this.taskService.createInternal(access, taskRequest).map(cTask -> Boolean.TRUE);
    }

    public Mono<Ticket> reassignTicket(Identity ticketId, TicketReassignRequest ticketReassignRequest) {

        if (ticketReassignRequest.getUserId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    "reassign user");

        return FlatMapUtil.flatMapMono(
                super::hasAccess, access -> super.readIdentityWithOwnerAccess(access, ticketId), (access, ticket) -> {
                    if (ticket.getAssignedUserId() != null
                            && ticket.getAssignedUserId().equals(ticketReassignRequest.getUserId()))
                        return Mono.just(ticket);

                    return FlatMapUtil.flatMapMono(
                            () -> Mono.just(access),
                            pAccess -> Mono.just(ticket),
                            (pAccess, cTicket) -> this.setTicketAssignment(cTicket, ticketReassignRequest.getUserId()),
                            (pAccess, cTicket, aTicket) -> super.updateInternal(aTicket),
                            (pAccess, cTicket, aTicket, uTicket) ->
                                    this.createNote(pAccess, ticketReassignRequest, uTicket),
                            (pAccess, cTicket, aTicket, uTicket, cNote) -> this.activityService
                                    .acReassign(
                                            uTicket.getId(),
                                            ticketReassignRequest.getComment(),
                                            cTicket.getAssignedUserId(),
                                            uTicket.getAssignedUserId())
                                    .thenReturn(uTicket));
                });
    }

    private Mono<Boolean> createNote(
            ProcessorAccess access, TicketReassignRequest ticketReassignRequest, Ticket ticket) {

        if (!ticketReassignRequest.hasNote() && ticketReassignRequest.getComment() == null)
            return Mono.just(Boolean.FALSE);

        NoteRequest noteRequest = ticketReassignRequest.getNoteRequest() != null
                ? ticketReassignRequest.getNoteRequest()
                : new NoteRequest();

        if (noteRequest.getContent() == null || noteRequest.getContent().isEmpty())
            noteRequest.setContent(ticketReassignRequest.getComment());
        noteRequest.setTicketId(ticket.getIdentity());
        noteRequest.setOwnerId(null);

        return this.noteService.createInternal(access, noteRequest).map(cNote -> Boolean.TRUE);
    }

    private Mono<Ticket> checkTicket(Ticket ticket, ProcessorAccess access) {

        if (ticket.getProductId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    this.productService.getEntityName());

        return FlatMapUtil.flatMapMonoWithNull(
                () -> this.productService.readById(ticket.getProductId()),
                product -> this.setDefaultStage(
                        ticket, access.getAppCode(), access.getClientCode(), product.getProductTemplateId()),
                (product, sTicket) -> this.productStageRuleService.getUserAssignment(
                        access.getAppCode(),
                        access.getClientCode(),
                        product.getId(),
                        sTicket.getStage(),
                        this.getEntityPrefix(access.getAppCode()),
                        access.getUserId(),
                        sTicket.toJson()),
                (product, sTicket, userId) ->
                        this.setTicketAssignment(sTicket, userId != null ? userId : access.getUserId()));
    }

    private Mono<Ticket> setDefaultStage(Ticket ticket, String appCode, String clientCode, ULong productTemplateId) {

        if (productTemplateId == null) return Mono.just(ticket);

        return FlatMapUtil.flatMapMonoWithNull(
                () -> this.stageService.getFirstStage(appCode, clientCode, productTemplateId),
                stage -> this.stageService.getFirstStatus(appCode, clientCode, productTemplateId, stage.getId()),
                (stage, status) -> {
                    if (stage != null) ticket.setStage(stage.getId());
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
                        return this.activityService
                                .acReInquiry(existing, ticketRequest)
                                .then(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.DUPLICATE_ENTITY,
                                        this.getEntityPrefix(appCode),
                                        existing.getId(),
                                        this.getEntityPrefix(appCode)));
                    return Mono.just(Boolean.FALSE);
                })
                .switchIfEmpty(Mono.just(Boolean.FALSE));
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
