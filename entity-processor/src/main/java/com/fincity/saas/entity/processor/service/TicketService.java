package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.TicketDAO;
import com.fincity.saas.entity.processor.dto.Owner;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.product.ProductComm;
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
import com.fincity.saas.entity.processor.model.request.ticket.TicketPartnerRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketReassignRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketStatusRequest;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import com.fincity.saas.entity.processor.service.content.NoteService;
import com.fincity.saas.entity.processor.service.content.TaskService;
import com.fincity.saas.entity.processor.service.product.ProductCommService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import com.fincity.saas.entity.processor.service.product.ProductTicketCRuleService;
import com.fincity.saas.entity.processor.service.rule.TicketDuplicationRuleService;
import java.util.Optional;
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
    private final ProductTicketCRuleService productTicketCRuleService;
    private final TicketDuplicationRuleService ticketDuplicationRuleService;
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
            ProductTicketCRuleService productTicketCRuleService,
            TicketDuplicationRuleService ticketDuplicationRuleService,
            ActivityService activityService,
            @Lazy TaskService taskService,
            @Lazy NoteService noteService,
            @Lazy CampaignService campaignService,
            @Lazy PartnerService partnerService,
            ProductCommService productCommService) {
        this.ownerService = ownerService;
        this.productService = productService;
        this.stageService = stageService;
        this.productTicketCRuleService = productTicketCRuleService;
        this.ticketDuplicationRuleService = ticketDuplicationRuleService;
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

        if (ticket.getProductId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    this.productService.getEntityName());

        return FlatMapUtil.flatMapMono(
                        () -> this.setAssignmentAndStage(ticket, access),
                        aTicket -> this.ownerService.getOrCreateTicketOwner(access, aTicket),
                        this::updateTicketFromOwner)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.checkEntity"));
    }

    private Mono<Ticket> setAssignmentAndStage(Ticket ticket, ProcessorAccess access) {

        if (ticket.getAssignedUserId() != null && ticket.getStage() != null) return Mono.just(ticket);

        if (ticket.getAssignedUserId() != null)
            return this.setDefaultStage(access, ticket)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.checkTicket"));

        ULong loggedInAssignedUser = access.isOutsideUser() ? null : access.getUserId();

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> this.setDefaultStage(access, ticket),
                        sTicket -> this.productTicketCRuleService.getUserAssignment(
                                access,
                                sTicket.getProductId(),
                                sTicket.getStage(),
                                this.getEntityPrefix(access.getAppCode()),
                                loggedInAssignedUser,
                                sTicket.toJsonElement()),
                        (sTicket, userId) -> this.setTicketAssignment(
                                access, sTicket, userId != null ? userId : loggedInAssignedUser))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.checkTicket"));
    }

    private Mono<Ticket> setDefaultStage(ProcessorAccess access, Ticket ticket) {

        if (ticket.getStage() != null) return Mono.just(ticket);

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> this.productService.readById(access, ticket.getProductId()),
                        product -> this.stageService.getFirstStage(access, product.getProductTemplateId()),
                        (product, stage) ->
                                this.stageService.getFirstStatus(access, product.getProductTemplateId(), stage.getId()),
                        (product, stage, status) -> {
                            if (stage == null)
                                return this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.TICKET_STAGE_MISSING,
                                        this.getEntityPrefix(access.getAppCode()));

                            ticket.setStage(stage.getId());

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

        return Mono.just(ticket.setAssignedUserId(userId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.setTicketAssignment"));
    }

    private Mono<Ticket> updateTicketFromOwner(Ticket ticket, Owner owner) {

        ticket.setOwnerId(owner.getId());

        if (owner.getName() != null && !owner.getName().equals(ticket.getName())) ticket.setName(owner.getName());

        if (owner.getEmail() != null && !owner.getEmail().equals(ticket.getEmail())) ticket.setEmail(owner.getEmail());

        if (owner.getPhoneNumber() != null && !owner.getPhoneNumber().equals(ticket.getPhoneNumber())) {
            ticket.setDialCode(owner.getDialCode());
            ticket.setPhoneNumber(owner.getPhoneNumber());
        }

        return Mono.just(ticket).contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateTicketFromOwner"));
    }

    public Flux<Ticket> updateOwnerTickets(ProcessorAccess access, Owner owner) {
        return this.dao
                .getAllOwnerTickets(owner.getId())
                .flatMap(ticket -> this.updateTicketFromOwner(ticket, owner))
                .transform(tickets -> super.updateAll(access, tickets))
                .flatMap(updatedTicket -> this.evictCache(updatedTicket).thenReturn(updatedTicket))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateOwnerTickets"));
    }

    @Override
    protected Mono<Ticket> updatableEntity(Ticket ticket) {
        return super.updatableEntity(ticket)
                .flatMap(existing -> {
                    existing.setEmail(ticket.getEmail());
                    existing.setAssignedUserId(ticket.getAssignedUserId());
                    existing.setStage(ticket.getStage());
                    existing.setStatus(ticket.getStatus());
                    existing.setSubSource(ticket.getSubSource());

                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updatableEntity"));
    }

    public Mono<Ticket> create(TicketRequest ticketRequest) {

        if (!ticketRequest.hasSourceInfo())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    "Source");

        Ticket ticket = Ticket.of(ticketRequest);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> Mono.zip(
                                this.productService.readByIdentity(access, ticketRequest.getProductId()),
                                this.getDnc(access, ticketRequest)),
                        (access, productIdentity) -> this.checkDuplicate(
                                access,
                                productIdentity.getT1().getId(),
                                ticketRequest.getPhoneNumber(),
                                ticketRequest.getEmail(),
                                ticketRequest.getSource(),
                                ticketRequest.getSubSource()),
                        (access, productIdentity, isDuplicate) -> Mono.just(
                                ticket.setProductId(productIdentity.getT1().getId())
                                        .setDnc(productIdentity.getT2())),
                        (access, productIdentity, isDuplicate, pTicket) -> super.create(access, pTicket),
                        (access, productIdentity, isDuplicate, pTicket, created) ->
                                this.createNote(access, ticketRequest, created),
                        (access, productIdentity, isDuplicate, pTicket, created, noteCreated) ->
                                this.activityService.acCreate(created).thenReturn(created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.create[TicketRequest]"));
    }

    @Override
    public Mono<Ticket> update(Ticket entity) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess, access -> super.update(access, entity), this.ownerService::updateTicketOwner);
    }

    public Mono<Ticket> createForCampaign(CampaignTicketRequest cTicketRequest) {

        ProcessorAccess access =
                ProcessorAccess.of(cTicketRequest.getAppCode(), cTicketRequest.getClientCode(), true, null, null);

        return FlatMapUtil.flatMapMono(
                        () -> this.campaignService.readByCampaignId(
                                access, cTicketRequest.getCampaignDetails().getCampaignId()),
                        campaign -> this.productService.readById(access, campaign.getProductId()),
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
                        (campaign, product, ticket, isDuplicate, pTicket) -> super.create(access, pTicket),
                        (campaign, product, ticket, isDuplicate, pTicket, created) ->
                                this.createNote(access, cTicketRequest, created),
                        (campaign, product, ticket, isDuplicate, pTicket, created, noteCreated) -> this.activityService
                                .acCreate(access, created, null)
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
                        () -> this.productService.readByCode(access, productCode),
                        product -> Mono.just(Ticket.of(cTicketRequest)),
                        (product, ticket) -> this.checkDuplicate(
                                access,
                                product.getId(),
                                cTicketRequest.getLeadDetails().getPhone(),
                                cTicketRequest.getLeadDetails().getEmail(),
                                cTicketRequest.getLeadDetails().getSource(),
                                cTicketRequest.getLeadDetails().getSubSource()),
                        (product, ticket, isDuplicate) -> Mono.just(ticket.setProductId(product.getId())),
                        (product, ticket, isDuplicate, pTicket) -> super.create(access, pTicket),
                        (product, ticket, isDuplicate, pTicket, created) ->
                                this.createNote(access, cTicketRequest, created),
                        (product, ticket, isDuplicate, pTicket, created, noteCreated) -> this.activityService
                                .acCreate(access, created, null)
                                .thenReturn(created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.createForWebsite[CampaignTicketRequest]"));
    }

    public Mono<Ticket> createForPartnerImportDCRM(String appCode, String clientCode, TicketPartnerRequest request) {

        ProcessorAccess access = ProcessorAccess.of(appCode, clientCode, true, null, null);

        return FlatMapUtil.flatMapMono(
                () -> this.productService.readByIdentity(access, request.getProductId()),
                product -> this.stageService
                        .getParentChild(
                                access, product.getProductTemplateId(), request.getStageId(), request.getStatusId())
                        .switchIfEmpty(this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.STAGE_MISSING)),
                (product, stageStatusEntity) -> request.getClientId() != null
                        ? this.securityService
                                .getClientById(request.getClientId().toBigInteger())
                                .map(Optional::of)
                        : Mono.just(Optional.of(new Client())),
                (product, stageStatusEntity, partnerClient) -> this.securityService.getUserInternal(
                        request.getAssignedUserId().toBigInteger(), null),
                (product, stageStatusEntity, partnerClient, assignedUser) -> this.getTicket(
                                access, product.getId(), request.getPhoneNumber(), request.getEmail())
                        .flatMap(existing -> existing.getId() != null
                                ? super.throwDuplicateError(access, existing)
                                : Mono.just(Boolean.FALSE))
                        .switchIfEmpty(Mono.just(Boolean.TRUE)),
                (product, stageStatusEntity, partnerClient, assignedUser, existing) -> {
                    Client partner = partnerClient.orElse(new Client());

                    return Mono.just((Ticket) new Ticket()
                            .setName(request.getName())
                            .setDescription(request.getDescription())
                            .setAssignedUserId(ULongUtil.valueOf(assignedUser.getId()))
                            .setDialCode(request.getPhoneNumber().getCountryCode())
                            .setPhoneNumber(request.getPhoneNumber().getNumber())
                            .setEmail(
                                    request.getEmail() != null
                                            ? request.getEmail().getAddress()
                                            : null)
                            .setSource(request.getSource())
                            .setSubSource(request.getSubSource())
                            .setProductId(product.getId())
                            .setStage(stageStatusEntity.getKey().getId())
                            .setStatus(stageStatusEntity.getValue().getFirst().getId())
                            .setClientId(partner.getId() != null ? ULongUtil.valueOf(partner.getId()) : null)
                            .setCreatedBy(ULongUtil.valueOf(assignedUser.getId()))
                            .setCreatedAt(request.getCreatedDate()));
                },
                (product, stageStatusEntity, partnerClient, assignedUser, existing, ticket) -> this.ownerService
                        .getOrCreateTicketOwner(access, ticket)
                        .flatMap(owner -> this.updateTicketFromOwner(ticket, owner)),
                (product, stageStatusEntity, partnerClient, assignedUser, existing, ticket, oTicket) ->
                        super.createInternal(access, ticket),
                (product, stageStatusEntity, partnerClient, assignedUser, existing, ticket, oTicket, created) ->
                        this.activityService
                                .acDcrmImport(access, created, null, request.getActivityJson())
                                .thenReturn(created));
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

        if (ticketPhone == null && ticketMail == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_INFO_MISSING,
                    this.getEntityName());

        return this.ticketDuplicationRuleService
                .getDuplicateRuleCondition(access, productId, source, subSource)
                .flatMap(ruleCondition -> this.handleDuplicateCheckWithRule(
                        access, productId, ticketPhone, ticketMail, ruleCondition, source, subSource))
                .switchIfEmpty(this.checkWithoutRule(access, productId, ticketPhone, ticketMail, source, subSource))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.checkDuplicate"));
    }

    private Mono<Boolean> handleDuplicateCheckWithRule(
            ProcessorAccess access,
            ULong productId,
            PhoneNumber ticketPhone,
            Email ticketMail,
            AbstractCondition ruleCondition,
            String source,
            String subSource) {

        if (ruleCondition != null && ruleCondition.isNonEmpty()) {
            return this.getTicket(ruleCondition, access, productId, ticketPhone, ticketMail)
                    .hasElement()
                    .flatMap(existing -> {
                        if (Boolean.TRUE.equals(existing)) return Mono.just(Boolean.FALSE);

                        // Not found with rule - check without rule to see if it's a real duplicate
                        return this.checkWithoutRule(access, productId, ticketPhone, ticketMail, source, subSource);
                    });
        }

        // No rule - check directly
        return this.checkWithoutRule(access, productId, ticketPhone, ticketMail, source, subSource);
    }

    private Mono<Boolean> checkWithoutRule(
            ProcessorAccess access,
            ULong productId,
            PhoneNumber ticketPhone,
            Email ticketMail,
            String source,
            String subSource) {

        return this.getTicket(access, productId, ticketPhone, ticketMail)
                .flatMap(existing -> {
                    if (existing == null || existing.getId() == null) return Mono.just(Boolean.FALSE);

                    return this.activityService
                            .acReInquiry(access, existing, null, source, subSource)
                            .then(super.throwDuplicateError(access, existing));
                })
                .switchIfEmpty(Mono.just(Boolean.FALSE));
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
                        access -> super.readByIdentity(access, ticketId),
                        (access, ticket) -> this.productService.readById(access, ticket.getProductId()),
                        (access, ticket, product) -> {
                            if (product.getProductTemplateId() == null)
                                return this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.PRODUCT_TEMPLATE_TYPE_MISSING,
                                        product.getId());

                            return this.stageService
                                    .getParentChild(
                                            access,
                                            product.getProductTemplateId(),
                                            ticketStatusRequest.getStageId(),
                                            ticketStatusRequest.getStatusId())
                                    .switchIfEmpty(this.msgService.throwMessage(
                                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                            ProcessorMessageResourceService.STAGE_MISSING));
                        },
                        (access, ticket, product, stageStatusEntity) -> {
                            ULong resolvedStageId = stageStatusEntity.getKey().getId();
                            ULong resolvedStatusId = !stageStatusEntity
                                            .getValue()
                                            .isEmpty()
                                    ? stageStatusEntity.getValue().getFirst().getId()
                                    : null;

                            boolean statusPresent = ticketStatusRequest.getStatusId() != null
                                    && !ticketStatusRequest.getStatusId().isNull();

                            boolean stageUnchanged = ticket.getStage().equals(resolvedStageId);
                            boolean statusUnchanged =
                                    !statusPresent || (ticket.getStatus().equals(resolvedStatusId));

                            if (stageUnchanged && statusUnchanged) return Mono.just(ticket);

                            return this.updateTicketStage(
                                    access,
                                    ticket,
                                    null,
                                    resolvedStageId,
                                    resolvedStatusId,
                                    ticketStatusRequest.getTaskRequest(),
                                    ticketStatusRequest.getComment());
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

        boolean doReassignment = !oldStage.equals(stageId);

        ticket.setStage(stageId);
        ticket.setStatus(statusId);

        return FlatMapUtil.flatMapMono(
                        () -> super.updateInternal(access, ticket),
                        uTicket -> taskRequest != null
                                ? this.createTask(access, taskRequest, uTicket)
                                : Mono.just(Boolean.FALSE),
                        (uTicket, cTask) -> this.activityService
                                .acStageStatus(access, uTicket, comment, oldStage)
                                .thenReturn(uTicket),
                        (uTicket, cTask, fTicket) -> doReassignment
                                ? this.reassignForStage(access, fTicket, reassignUserId, true)
                                : Mono.just(fTicket))
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
                        super::hasAccess, access -> super.readByIdentity(access, ticketId), (access, ticket) -> {
                            if (!access.getUserInherit().getSubOrg().contains(ticketReassignRequest.getUserId()))
                                return this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.INVALID_USER_ACCESS);

                            return this.updateTicketForReassignment(
                                    access,
                                    ticket,
                                    ticketReassignRequest.getUserId(),
                                    ticketReassignRequest.getComment(),
                                    false);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.reassignTicket"));
    }

    public Mono<Ticket> reassignForStage(ProcessorAccess access, Ticket ticket, ULong userId, boolean isAutomatic) {

        if (userId != null)
            return this.updateTicketForReassignment(access, ticket, userId, AUTOMATIC_REASSIGNMENT, isAutomatic);

        return FlatMapUtil.flatMapMono(
                        () -> this.productTicketCRuleService.getUserAssignment(
                                access,
                                ticket.getProductId(),
                                ticket.getStage(),
                                this.getEntityPrefix(access.getAppCode()),
                                access.getUserId(),
                                ticket.toJsonElement()),
                        ruleUserId -> ruleUserId == null
                                ? Mono.just(ticket)
                                : this.updateTicketForReassignment(
                                        access, ticket, ruleUserId, AUTOMATIC_REASSIGNMENT, isAutomatic))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.reassignForStage"));
    }

    private Mono<Ticket> updateTicketForReassignment(
            ProcessorAccess access, Ticket ticket, ULong userId, String comment, boolean isAutomatic) {

        ULong oldUserId = ticket.getAssignedUserId();

        if (oldUserId != null && oldUserId.equals(userId)) return Mono.just(ticket);

        return FlatMapUtil.flatMapMono(
                        () -> this.setTicketAssignment(access, ticket, userId),
                        aTicket -> super.updateInternal(access, aTicket),
                        (aTicket, uTicket) -> this.activityService
                                .acReassign(
                                        access,
                                        uTicket.getId(),
                                        comment,
                                        oldUserId,
                                        uTicket.getAssignedUserId(),
                                        isAutomatic)
                                .thenReturn(uTicket))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateTicketForReassignment"));
    }

    private Mono<Ticket> getTicket(
            AbstractCondition condition,
            ProcessorAccess access,
            ULong productId,
            PhoneNumber ticketPhone,
            Email ticketMail) {
        return this.dao
                .readByNumberAndEmail(
                        condition,
                        access,
                        productId,
                        ticketPhone != null ? ticketPhone.getCountryCode() : null,
                        ticketPhone != null ? ticketPhone.getNumber() : null,
                        ticketMail != null ? ticketMail.getAddress() : null)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.getTicket"));
    }

    public Mono<Ticket> getTicket(ProcessorAccess access, ULong productId, PhoneNumber ticketPhone, Email ticketMail) {
        return this.getTicket(null, access, productId, ticketPhone, ticketMail)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.getTicket"));
    }

    public Flux<Ticket> updateTicketDncByClientId(ProcessorAccess access, ULong clientId, Boolean dnc) {
        return this.dao
                .getAllClientTicketsByDnc(clientId, !dnc)
                .map(ticket -> ticket.setDnc(dnc))
                .transform(tickets -> super.updateAll(access, tickets))
                .flatMap(uTicket -> super.evictCache(uTicket).thenReturn(uTicket))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateTicketDncByClientId"));
    }

    public Mono<ProductComm> getTicketProductComm(
            Identity ticketId, ConnectionType connectionType, ConnectionSubType connectionSubType) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.readByIdentity(access, ticketId),
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
