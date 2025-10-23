package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.TicketDAO;
import com.fincity.saas.entity.processor.dto.Owner;
import com.fincity.saas.entity.processor.dto.ProductComm;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.CampaignTicketRequest;
import com.fincity.saas.entity.processor.model.request.content.INoteRequest;
import com.fincity.saas.entity.processor.model.request.content.NoteRequest;
import com.fincity.saas.entity.processor.model.request.content.TaskRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketReassignRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketStatusRequest;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
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
    private static final String AUTOMATIC_REASSIGNMENT = "Automatic Reassignment for Stage update.";

    private final OwnerService ownerService;
    private final ProductService productService;
    private final StageService stageService;
    private final ProductStageRuleService productStageRuleService;
    private final ActivityService activityService;
    private final TaskService taskService;
    private final NoteService noteService;
    private final CampaignService campaignService;
    private final PartnerService partnerService;
    private final ProductCommService productCommService;

    public TicketService(
            @Lazy OwnerService ownerService,
            ProductService productService,
            StageService stageService,
            ProductStageRuleService productStageRuleService,
            ActivityService activityService,
            @Lazy TaskService taskService,
            @Lazy NoteService noteService,
            @Lazy CampaignService campaignService,
            @Lazy PartnerService partnerService,
            ProductCommService productCommService) {
        this.ownerService = ownerService;
        this.productService = productService;
        this.stageService = stageService;
        this.productStageRuleService = productStageRuleService;
        this.activityService = activityService;
        this.taskService = taskService;
        this.noteService = noteService;
        this.campaignService = campaignService;
        this.partnerService = partnerService;
        this.productCommService = productCommService;
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

    private Mono<Ticket> checkTicket(Ticket ticket, ProcessorAccess access) {

        if (ticket.getProductId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    this.productService.getEntityName());

        if (ticket.getAssignedUserId() != null && ticket.getStage() != null) return Mono.just(ticket);

        if (ticket.getAssignedUserId() != null)
            return FlatMapUtil.flatMapMono(
                            () -> this.productService.readById(ticket.getProductId()),
                            product -> this.setDefaultStage(access, ticket, product.getProductTemplateId()))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.checkTicket"));

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> this.productService.readById(ticket.getProductId()),
                        product -> this.setDefaultStage(access, ticket, product.getProductTemplateId()),
                        (product, sTicket) -> this.productStageRuleService.getUserAssignment(
                                access,
                                product.getId(),
                                sTicket.getStage(),
                                this.getEntityPrefix(access.getAppCode()),
                                access.getUserId(),
                                sTicket.toJsonElement()),
                        (product, sTicket, userId) ->
                                this.setTicketAssignment(access, sTicket, userId != null ? userId : access.getUserId()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.checkTicket"));
    }

    private Mono<Ticket> setDefaultStage(ProcessorAccess access, Ticket ticket, ULong productTemplateId) {

        if (productTemplateId == null || ticket.getStage() != null) return Mono.just(ticket);

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

    private Mono<Ticket> setTicketAssignment(ProcessorAccess access, Ticket ticket, ULong userId) {

        if (userId == null || userId.equals(ULong.valueOf(0)))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.TICKET_ASSIGNMENT_MISSING,
                    this.getEntityPrefix(access.getAppCode()));

        return Mono.just(ticket.setAssignedUserId(userId));
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

    public Flux<Ticket> updateOwnerTickets(Owner owner) {
        Flux<Ticket> ticketsFlux =
                this.dao.getAllOwnerTickets(owner.getId()).flatMap(ticket -> this.updateTicketFromOwner(ticket, owner));

        return this.dao
                .updateAll(ticketsFlux)
                .flatMap(uTicket -> super.evictCache(uTicket).map(updated -> uTicket))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateOwnerTickets"));
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

    public Mono<Ticket> create(TicketRequest ticketRequest) {

        if (!ticketRequest.hasIdentifyInfo())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_INFO_MISSING,
                    this.getEntityName());

        if (!ticketRequest.hasSourceInfo())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    "Source");

        Ticket ticket = Ticket.of(ticketRequest);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> Mono.zip(
                                this.productService.updateIdentity(ticketRequest.getProductId()),
                                this.getDnc(access, ticketRequest)),
                        (access, productIdentity) -> this.checkDuplicate(
                                access,
                                productIdentity.getT1().getULongId(),
                                ticketRequest.getPhoneNumber(),
                                ticketRequest.getEmail(),
                                ticketRequest.getSource(),
                                ticketRequest.getSubSource()),
                        (access, productIdentity, isDuplicate) -> Mono.just(
                                ticket.setProductId(productIdentity.getT1().getULongId())
                                        .setDnc(productIdentity.getT2())),
                        (access, productIdentity, isDuplicate, pTicket) -> super.createInternal(access, pTicket),
                        (access, productIdentity, isDuplicate, pTicket, created) ->
                                this.createNote(access, ticketRequest, created),
                        (access, productIdentity, isDuplicate, pTicket, created, noteCreated) ->
                                this.activityService.acCreate(created).thenReturn(created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.create[TicketRequest]"));
    }

    public Mono<Ticket> createForCampaign(CampaignTicketRequest cTicketRequest) {

        ProcessorAccess access =
                ProcessorAccess.of(cTicketRequest.getAppCode(), cTicketRequest.getClientCode(), true, null, null);

        return FlatMapUtil.flatMapMono(
                        () -> this.campaignService.readByCampaignId(
                                access, cTicketRequest.getCampaignDetails().getCampaignId()),
                        campaign -> this.productService.readById(campaign.getProductId()),
                        (campaign, product) ->
                                Mono.just(Ticket.of(cTicketRequest).setCampaignId(campaign.getId())),
                        (campaign, product, ticket) -> this.checkDuplicate(
                                access,
                                campaign.getProductId(),
                                cTicketRequest.getLeadDetails().getPhone(),
                                cTicketRequest.getLeadDetails().getEmail(),
                                cTicketRequest.getLeadDetails().getSource(),
                                cTicketRequest.getLeadDetails().getSubSource()),
                        (campaign, product, ticket, isDuplicate) -> Mono.just(ticket.setProductId(product.getId())),
                        (campaign, product, ticket, isDuplicate, pTicket) -> super.createInternal(access, pTicket),
                        (campaign, product, ticket, isDuplicate, pTicket, created) ->
                                this.createNote(access, cTicketRequest, created),
                        (campaign, product, ticket, isDuplicate, pTicket, created, noteCreated) -> this.activityService
                                .acCreate(created, null, access)
                                .thenReturn(created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.createForCampaign[cTicketRequest]"));
    }

    public Mono<Ticket> createForWebsite(CampaignTicketRequest cTicketRequest, String productCode) {

        if (cTicketRequest.getCampaignDetails() != null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.WEBSITE_ENTITY_DATA_INVALID);

        if (cTicketRequest.getLeadDetails().getSource() == null)
            cTicketRequest.getLeadDetails().setSource("Website");

        ProcessorAccess access =
                ProcessorAccess.of(cTicketRequest.getAppCode(), cTicketRequest.getClientCode(), true, null, null);

        return FlatMapUtil.flatMapMono(
                        () -> this.productService.readByCode(productCode),
                        product -> Mono.just(Ticket.of(cTicketRequest)),
                        (product, ticket) -> this.checkDuplicate(
                                access,
                                product.getId(),
                                cTicketRequest.getLeadDetails().getPhone(),
                                cTicketRequest.getLeadDetails().getEmail(),
                                cTicketRequest.getLeadDetails().getSource(),
                                cTicketRequest.getLeadDetails().getSubSource()),
                        (product, ticket, isDuplicate) -> Mono.just(ticket.setProductId(product.getId())),
                        (product, ticket, isDuplicate, pTicket) -> super.createInternal(access, pTicket),
                        (product, ticket, isDuplicate, pTicket, created) ->
                                this.createNote(access, cTicketRequest, created),
                        (product, ticket, isDuplicate, pTicket, created, noteCreated) -> this.activityService
                                .acCreate(created, null, access)
                                .thenReturn(created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.createForWebsite[CampaignTicketRequest]"));
    }

    private Mono<Boolean> getDnc(ProcessorAccess access, TicketRequest ticketRequest) {
        if (!access.isOutsideUser()) return Mono.just(Boolean.FALSE);

        return ticketRequest.getDnc() != null
                ? Mono.just(ticketRequest.getDnc())
                : this.partnerService.getPartnerDnc(access);
    }

    private Mono<Boolean> checkDuplicate(
            ProcessorAccess access,
            ULong productId,
            PhoneNumber ticketPhone,
            Email ticketMail,
            String source,
            String subSource) {

        if (ticketMail == null && ticketPhone == null) return Mono.just(Boolean.FALSE);

        return this.getTicket(access, productId, ticketPhone, ticketMail)
                .flatMap(existing -> {
                    if (existing.getId() != null)
                        return this.activityService
                                .acReInquiry(access, existing, null, source, subSource)
                                .then(super.throwDuplicateError(access, existing));
                    return Mono.just(Boolean.FALSE);
                })
                .switchIfEmpty(Mono.just(Boolean.FALSE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.checkDuplicate"));
    }

    private <T extends INoteRequest> Mono<Boolean> createNote(ProcessorAccess access, T noteRequest, Ticket ticket) {

        if (!noteRequest.hasNote()) return Mono.just(Boolean.FALSE);

        NoteRequest note = noteRequest.getNoteRequest() == null ? new NoteRequest() : noteRequest.getNoteRequest();

        return this.createNote(access, note, noteRequest.getComment(), ticket)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.createNote"));
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
                                    (pAccess, cTicket, product, stageStatusEntity) -> this.updateTicketStage(
                                            access,
                                            cTicket,
                                            null,
                                            stageStatusEntity.getKey().getId(),
                                            !stageStatusEntity.getValue().isEmpty()
                                                    ? stageStatusEntity
                                                            .getValue()
                                                            .getFirst()
                                                            .getId()
                                                    : null,
                                            ticketStatusRequest.getTaskRequest(),
                                            ticketStatusRequest.getComment()));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateStageStatus"));
    }

    public Mono<Ticket> updateTicketStage(
            ProcessorAccess access,
            Ticket ticket,
            ULong reassignUserId,
            ULong stageId,
            ULong statusId,
            TaskRequest taskRequest,
            String comment) {

        ULong oldStage = CloneUtil.cloneObject(ticket.getStage());

        ticket.setStage(stageId);
        ticket.setStatus(statusId);

        return FlatMapUtil.flatMapMono(
                () -> super.updateInternal(ticket),
                uTicket ->
                        taskRequest != null ? this.createTask(access, taskRequest, uTicket) : Mono.just(Boolean.FALSE),
                (uTicket, cTask) -> this.activityService
                        .acStageStatus(uTicket, comment, oldStage)
                        .thenReturn(uTicket),
                (uTicket, cTask, fTicket) -> this.reassignForStage(access, ticket, reassignUserId))
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateTicketStage"));
    }

    private Mono<Boolean> createTask(ProcessorAccess access, TaskRequest taskRequest, Ticket ticket) {

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

                            return this.updateTicketForReassignment(
                                    access,
                                    ticket,
                                    ticketReassignRequest.getUserId(),
                                    ticketReassignRequest.getComment());
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.reassignTicket"));
    }

	public Mono<Ticket> reassignForStage(ProcessorAccess access, Ticket ticket, ULong userId) {

		if (userId != null) return this.updateTicketForReassignment(access, ticket, userId, AUTOMATIC_REASSIGNMENT);

		return FlatMapUtil.flatMapMono(
				() -> this.productStageRuleService.getUserAssignment(
						access,
						ticket.getProductId(),
						ticket.getStage(),
						this.getEntityPrefix(access.getAppCode()),
						access.getUserId(),
						ticket.toJsonElement()),
				ruleUserId -> ruleUserId == null
						? Mono.just(ticket)
						: this.updateTicketForReassignment(access, ticket, ruleUserId, AUTOMATIC_REASSIGNMENT))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.reassignForStage"));
	}

    private Mono<Ticket> updateTicketForReassignment(
            ProcessorAccess access, Ticket ticket, ULong userId, String comment) {

        ULong oldUserId = ticket.getAssignedUserId();

        if (oldUserId != null && oldUserId.equals(userId)) return Mono.just(ticket);

        return FlatMapUtil.flatMapMono(
                () -> this.setTicketAssignment(access, ticket, userId),
                super::updateInternal,
                (aTicket, uTicket) -> this.activityService
                        .acReassign(uTicket.getId(), comment, oldUserId, uTicket.getAssignedUserId())
                        .thenReturn(uTicket))
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateTicketForReassignment"));
    }

    public Mono<Ticket> getTicket(ProcessorAccess access, ULong productId, PhoneNumber ticketPhone, Email ticketMail) {
        return this.dao.readByNumberAndEmail(
                access,
                productId,
                ticketPhone != null ? ticketPhone.getCountryCode() : null,
                ticketPhone != null ? ticketPhone.getNumber() : null,
                ticketMail != null ? ticketMail.getAddress() : null)
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.getTicket"));
    }

    public Flux<Ticket> updateTicketDncByClientId(ULong clientId, Boolean dnc) {
        Flux<Ticket> tickets =
                this.dao.getAllClientTicketsByDnc(clientId, !dnc).flatMap(ticket -> Mono.just(ticket.setDnc(dnc)));

        return this.dao
                .updateAll(tickets)
                .flatMap(uTicket -> super.evictCache(uTicket).map(updated -> uTicket))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateTicketDncByClientId"));
    }

    public Mono<ProductComm> getTicketProductComm(
            Identity ticketId, ConnectionType connectionType, ConnectionSubType connectionSubType) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.readIdentityWithAccess(access, ticketId),
                        (access, ticket) -> this.productCommService.getProductComm(
                                access,
                                ticket.getProductId(),
                                connectionType,
                                connectionSubType,
                                ticket.getSource(),
                                ticket.getSubSource()))
                .switchIfEmpty(Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.getTicketProductComm"));
    }
}
