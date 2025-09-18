package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.TicketDAO;
import com.fincity.saas.entity.processor.dto.Owner;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.CampaignRequest;
import com.fincity.saas.entity.processor.model.request.CampaignTicketRequest;
import com.fincity.saas.entity.processor.model.request.content.INoteRequest;
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
import reactor.util.context.Context;

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
    private final CampaignService campaignService;

    public TicketService(
            @Lazy OwnerService ownerService,
            ProductService productService,
            StageService stageService,
            ProductStageRuleService productStageRuleService,
            ActivityService activityService,
            @Lazy TaskService taskService,
            @Lazy NoteService noteService,
            @Lazy CampaignService campaignService) {
        this.ownerService = ownerService;
        this.productService = productService;
        this.stageService = stageService;
        this.productStageRuleService = productStageRuleService;
        this.activityService = activityService;
        this.taskService = taskService;
        this.noteService = noteService;
        this.campaignService = campaignService;
    }

    @Override
    protected String getCacheName() {
        return TICKET_CACHE;
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.TRUE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET;
    }

    @Override
    protected Mono<Ticket> checkEntity(Ticket ticket, ProcessorAccess access) {
        return this.checkTicket(ticket, access)
                .flatMap(uEntity -> this.setOwner(access, uEntity))
                .flatMap(owner -> this.updateTicketFromOwner(ticket, owner))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.checkEntity"));
    }

    @Override
    protected Mono<Ticket> updatableEntity(Ticket ticket) {
        return super.updatableEntity(ticket)
                .flatMap(existing -> {
                    existing.setStage(ticket.getStage());
                    existing.setStatus(ticket.getStatus());

                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updatableEntity"));
    }

    public Flux<Ticket> updateOwnerTickets(Owner owner) {
        Flux<Ticket> ticketsFlux =
                this.dao.getAllOwnerTickets(owner.getId()).flatMap(ticket -> this.updateTicketFromOwner(ticket, owner));

        return this.dao
                .updateAll(ticketsFlux)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateOwnerTickets"));
    }

    public Mono<ProcessorResponse> createOpenResponse(TicketRequest ticketRequest) {

        Ticket ticket = Ticket.of(ticketRequest);

        if (ticketRequest.getProductId() == null || ticketRequest.getProductId().isNull())
            return super.hasPublicAccess()
                    .flatMap(access -> setOwner(access, ticket))
                    .map(owner -> ProcessorResponse.ofCreated(owner.getCode(), owner.getEntitySeries()))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.createOpenResponse"));

        return FlatMapUtil.flatMapMono(
                        super::hasPublicAccess,
                        access -> this.productService.updateIdentity(ticketRequest.getProductId()),
                        (access, productIdentity) -> Mono.just(ticket.setProductId(productIdentity.getULongId())),
                        (access, productIdentity, pTicket) -> super.createInternal(access, pTicket),
                        (access, productIdentity, pTicket, created) -> this.createNote(access, ticketRequest, created),
                        (access, productIdentity, pTicket, created, noteCreated) -> this.activityService
                                .acCreate(created)
                                .thenReturn(created)
                                .thenReturn(created))
                .map(created -> ProcessorResponse.ofCreated(created.getCode(), created.getEntitySeries()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.createOpenResponse"));
    }

    public Mono<Ticket> create(TicketRequest ticketRequest) {

        if (!ticketRequest.hasIdentifyInfo() && !ticketRequest.hasSourceInfo()) return this.identityMissingError();

        Ticket ticket = Ticket.of(ticketRequest);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.productService.checkAndUpdateIdentityWithAccess(
                                access, ticketRequest.getProductId()),
                        (access, productIdentity) -> this.checkDuplicate(access, ticketRequest),
                        (access, productIdentity, isDuplicate) ->
                                Mono.just(ticket.setProductId(productIdentity.getULongId())),
                        (access, productIdentity, isDuplicate, pTicket) -> super.createInternal(access, pTicket),
                        (access, productIdentity, isDuplicate, pTicket, created) ->
                                this.createNote(access, ticketRequest, created),
                        (access, productIdentity, isDuplicate, pTicket, created, noteCreated) ->
                                this.activityService.acCreate(created).thenReturn(created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.create[TicketRequest]"));
    }

    public Mono<Ticket> updateStageStatus(Identity ticketId, TicketStatusRequest ticketStatusRequest) {

        if (ticketStatusRequest.getStageId() == null
                || ticketStatusRequest.getStageId().isNull())
            return this.identityMissingError(this.stageService.getEntityName());

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.readIdentityWithOwnerAccess(access, ticketId),
                        (access, ticket) -> {
                            boolean statusPresent = ticketStatusRequest.getStatusId() != null
                                    && !ticketStatusRequest.getStatusId().isNull();

                            if (ticket.getStage()
                                            .equals(ticketStatusRequest
                                                    .getStageId()
                                                    .getULongId())
                                    && (statusPresent
                                            && ticket.getStatus()
                                                    .equals(ticketStatusRequest
                                                            .getStatusId()
                                                            .getULongId()))) return Mono.just(ticket);

                            if (ticket.getStage()
                                            .equals(ticketStatusRequest
                                                    .getStageId()
                                                    .getULongId())
                                    && !statusPresent) return Mono.just(ticket);

                            return FlatMapUtil.flatMapMono(
                                    () -> Mono.just(access),
                                    pAccess -> Mono.just(ticket),
                                    (pAccess, cTicket) -> this.productService.readById(pAccess, cTicket.getProductId()),
                                    (pAccess, cTicket, product) -> {
                                        if (product.getProductTemplateId() == null)
                                            return this.msgService.throwMessage(
                                                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                                    ProcessorMessageResourceService.PRODUCT_TEMPLATE_TYPE_MISSING,
                                                    product.getId());

                                        return this.stageService
                                                .getParentChild(
                                                        pAccess,
                                                        product.getProductTemplateId(),
                                                        ticketStatusRequest.getStageId(),
                                                        ticketStatusRequest.getStatusId())
                                                .switchIfEmpty(this.msgService.throwMessage(
                                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                                        ProcessorMessageResourceService.STAGE_MISSING));
                                    },
                                    (pAccess, cTicket, product, stageStatusEntity) -> Mono.just(cTicket.getStage()),
                                    (pAccess, cTicket, product, stageStatusEntity, oldStage) -> {
                                        cTicket.setStage(
                                                stageStatusEntity.getKey().getId());

                                        if (!stageStatusEntity.getValue().isEmpty())
                                            cTicket.setStatus(stageStatusEntity
                                                    .getValue()
                                                    .getFirst()
                                                    .getId());

                                        return super.updateInternal(cTicket);
                                    },
                                    (pAccess, cTicket, product, stageStatusEntity, oldStage, uTicket) ->
                                            this.createTask(pAccess, ticketStatusRequest, uTicket),
                                    (pAccess, cTicket, product, stageStatusEntity, oldStage, uTicket, cTask) ->
                                            this.activityService
                                                    .acStageStatus(uTicket, ticketStatusRequest.getComment(), oldStage)
                                                    .thenReturn(uTicket));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateStageStatus"));
    }

    private Mono<Boolean> createTask(ProcessorAccess access, TicketStatusRequest ticketStatusRequest, Ticket ticket) {
        if (!ticketStatusRequest.hasTask()) return Mono.just(Boolean.FALSE);

        TaskRequest taskRequest = ticketStatusRequest.getTaskRequest();

        taskRequest.setTicketId(ticket.getIdentity());
        taskRequest.setOwnerId(null);

        return this.taskService
                .createInternal(access, taskRequest)
                .map(cTask -> Boolean.TRUE)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.createTask"));
    }

    public Mono<Ticket> reassignTicket(Identity ticketId, TicketReassignRequest ticketReassignRequest) {

        if (ticketReassignRequest.getUserId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    "reassign user");

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.readIdentityWithOwnerAccess(access, ticketId),
                        (access, ticket) -> {
                            if (!access.getUserInherit().getSubOrg().contains(ticketReassignRequest.getUserId()))
                                return this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.INVALID_USER_ACCESS);

                            ULong oldUserId = ticket.getAssignedUserId();

                            if (oldUserId != null && oldUserId.equals(ticketReassignRequest.getUserId()))
                                return Mono.just(ticket);

                            return FlatMapUtil.flatMapMono(
                                    () -> this.setTicketAssignment(ticket, ticketReassignRequest.getUserId()),
                                    super::updateInternal,
                                    (aTicket, uTicket) -> this.createNote(access, ticketReassignRequest, uTicket),
                                    (aTicket, uTicket, cNote) -> this.activityService
                                            .acReassign(
                                                    uTicket.getId(),
                                                    ticketReassignRequest.getComment(),
                                                    oldUserId,
                                                    uTicket.getAssignedUserId())
                                            .thenReturn(uTicket));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.reassignTicket"));
    }

    private <T extends INoteRequest> Mono<Boolean> createNote(ProcessorAccess access, T noteRequest, Ticket ticket) {

        if (!noteRequest.hasNote()) return Mono.just(Boolean.FALSE);

        NoteRequest note = noteRequest.getNoteRequest() == null ? new NoteRequest() : noteRequest.getNoteRequest();

        return this.createNote(access, note, noteRequest.getComment(), ticket);
    }

    private Mono<Boolean> createNote(ProcessorAccess access, NoteRequest noteRequest, String comment, Ticket ticket) {

        if (noteRequest.getContent() == null || noteRequest.getContent().isEmpty()) noteRequest.setContent(comment);
        noteRequest.setTicketId(ticket.getIdentity());
        noteRequest.setOwnerId(null);

        return this.noteService
                .createInternal(access, noteRequest)
                .map(cNote -> Boolean.TRUE)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.createNote"));
    }

    private Mono<Ticket> checkTicket(Ticket ticket, ProcessorAccess access) {

        if (ticket.getProductId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    this.productService.getEntityName());

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> this.productService.readById(ticket.getProductId()),
                        product -> this.setDefaultStage(access, ticket, product.getProductTemplateId()),
                        (product, sTicket) -> this.productStageRuleService.getUserAssignment(
                                access,
                                product.getId(),
                                sTicket.getStage(),
                                this.getEntityPrefix(access.getAppCode()),
                                access.getUserId(),
                                sTicket.toJson()),
                        (product, sTicket, userId) ->
                                this.setTicketAssignment(sTicket, userId != null ? userId : access.getUserId()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.checkTicket"));
    }

    private Mono<Ticket> setDefaultStage(ProcessorAccess access, Ticket ticket, ULong productTemplateId) {

        if (productTemplateId == null) return Mono.just(ticket);

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> this.stageService.getFirstStage(access, productTemplateId),
                        stage -> this.stageService.getFirstStatus(access, productTemplateId, stage.getId()),
                        (stage, status) -> {
                            if (stage != null) ticket.setStage(stage.getId());
                            if (status != null) ticket.setStatus(status.getId());

                            return Mono.just(ticket);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.setDefaultStage"));
    }

    private Mono<Boolean> checkDuplicate(ProcessorAccess access, TicketRequest ticketRequest) {
        return this.dao
                .readByNumberAndEmail(
                        access,
                        ticketRequest.getProductId().getULongId(),
                        ticketRequest.getPhoneNumber() != null
                                ? ticketRequest.getPhoneNumber().getCountryCode()
                                : null,
                        ticketRequest.getPhoneNumber() != null
                                ? ticketRequest.getPhoneNumber().getNumber()
                                : null,
                        ticketRequest.getEmail() != null
                                ? ticketRequest.getEmail().getAddress()
                                : null)
                .flatMap(existing -> {
                    if (existing.getId() != null)
                        return this.activityService
                                .acReInquiry(existing, ticketRequest)
                                .then(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.DUPLICATE_ENTITY,
                                        this.getEntityPrefix(access.getAppCode()),
                                        existing.getId(),
                                        this.getEntityPrefix(access.getClientCode())));
                    return Mono.just(Boolean.FALSE);
                })
                .switchIfEmpty(Mono.just(Boolean.FALSE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.checkDuplicate"));
    }

    private Mono<Owner> setOwner(ProcessorAccess access, Ticket ticket) {
        return this.ownerService
                .getOrCreateTicketOwner(access, ticket)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.setOwner"));
    }

    private Mono<Ticket> updateTicketFromOwner(Ticket ticket, Owner owner) {
        ticket.setOwnerId(owner.getId());

        if (owner.getDialCode() != null && owner.getPhoneNumber() != null) {
            ticket.setDialCode(owner.getDialCode());
            ticket.setPhoneNumber(owner.getPhoneNumber());
        }

        if (owner.getEmail() != null) ticket.setEmail(owner.getEmail());

        return Mono.just(ticket).contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateTicketFromOwner"));
    }

    private Mono<Ticket> setTicketAssignment(Ticket ticket, ULong userId) {
        // Only set assignedUserId if userId is not null and not 0
        if (userId != null && !userId.equals(ULong.valueOf(0))) ticket.setAssignedUserId(userId);

        return Mono.just(ticket);
    }

    public Mono<Ticket> createForCampaign(CampaignTicketRequest campaignTicketRequest) {

        ProcessorAccess access = ProcessorAccess.of(
                campaignTicketRequest.getAppCode(), campaignTicketRequest.getClientCode(), true, null, null);

        return FlatMapUtil.flatMapMono(
                        () -> this.campaignService.readByCampaignId(
                                access, campaignTicketRequest.getCampaignDetails().getCampaignId()),

                        (campaign) -> this.productService.readById(campaign.getProductId()),

                        (campaign, product) ->
                                Mono.just(Ticket.of(campaignTicketRequest).setCampaignId(campaign.getId())),

                        (campaign, product, ticket) ->
                                Mono.just(TicketRequest.of(campaignTicketRequest, campaign.getId(), product.getId())),

                        (campaign, product, ticket, ticketRequest) -> this.checkDuplicate(access, ticketRequest),

                        (campaign, product, ticket, ticketRequest, isDuplicate) ->  Mono.just(ticket.setProductId(product.getId())),

                        (campaign, product, ticket, ticketRequest, isDuplicate, pTicket) ->
                                super.createInternal(access, pTicket),

                        (campaign, product, ticket, ticketRequest, isDuplicate, pTicket, created) ->
                                this.createNote(access, ticketRequest, created),

                        (campaign, product, ticket, ticketRequest, isDuplicate, pTicket, created, noteCreated) ->
                                this.activityService.acCreate(created, null, access).thenReturn(created))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketService.createForCampaign[CampaignTicketRequest]"));
    }

    public Mono<Ticket> createForWebsite(CampaignTicketRequest cTicketRequest, String productCode) {

        if (cTicketRequest.getCampaignDetails() != null )
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.WEBSITE_ENTITY_DATA_INVALID);

        ProcessorAccess access = ProcessorAccess.of(
                cTicketRequest.getAppCode(), cTicketRequest.getClientCode(), true, null, null);

        return FlatMapUtil.flatMapMono(

                () -> this.productService.readByCode(productCode),

                product -> {
                    TicketRequest ticket = TicketRequest.of(cTicketRequest, product.getId(), null);

                    if (ticket.getSource() == null)
                        ticket.setSource("Website");

                    return Mono.just(ticket);
                },

                (product, ticketRequest) -> Mono.just(Ticket.of(ticketRequest)),

                (product, ticketRequest, ticket) -> this.checkDuplicate(access, ticketRequest),

                (product, ticketRequest, ticket, isDuplicate) -> Mono.just(ticket.setProductId(product.getId())),

                (product, ticketRequest, ticket, isDuplicate, pTicket) -> super.createInternal(access, pTicket),

                (product, ticketRequest, ticket, isDuplicate, pTicket, created) ->
                        this.createNote(access, ticketRequest, created),

                (product, ticketRequest, ticket, isDuplicate, pTicket, created, noteCreated) ->
                        this.activityService.acCreate(created, null, access).thenReturn(created))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketService.createForWebsite[CampaignTicketRequest]"));

    }
}
