package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.functions.AbstractServiceFunction;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.repository.ListFunctionRepository;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.aspect.ReactiveTime;
import com.fincity.saas.entity.processor.dao.TicketDAO;
import com.fincity.saas.entity.processor.dto.Owner;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.product.ProductComm;
import com.fincity.saas.entity.processor.constant.BusinessPartnerConstant;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.Tag;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.common.RuleResult;
import com.fincity.saas.entity.processor.model.request.CampaignTicketRequest;
import com.fincity.saas.entity.processor.model.request.content.INoteRequest;
import com.fincity.saas.entity.processor.model.request.content.NoteRequest;
import com.fincity.saas.entity.processor.model.request.content.TaskRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketPartnerRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketReassignRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketStatusRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketTagRequest;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import com.fincity.saas.entity.processor.service.content.NoteService;
import com.fincity.saas.entity.processor.service.content.TaskService;
import com.fincity.saas.entity.processor.service.product.ProductCommService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import com.fincity.saas.entity.processor.service.product.ProductTicketCRuleService;
import com.fincity.saas.entity.processor.service.product.ProductTicketExRuleService;
import com.fincity.saas.entity.processor.service.rule.TicketDuplicationRuleService;
import com.fincity.saas.entity.processor.util.EntityProcessorArgSpec;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TicketService extends BaseProcessorService<EntityProcessorTicketsRecord, Ticket, TicketDAO>
        implements IRepositoryProvider {

    private static final String TICKET_CACHE = "ticket";
    private static final String DIAG_ACTION_ASSIGNMENT_INITIAL = "ASSIGNMENT_INITIAL";
    private static final String DIAG_REASON_RULE = "Initial assignment via rule";
    private static final String DIAG_REASON_LOGGED_IN_USER = "Initial assignment to logged-in user";
    private static final String AUTOMATIC_REASSIGNMENT = "Automatic Reassignment for Stage update.";
    private static final String NAMESPACE = "EntityProcessor.Ticket";
    private static final ClassSchema classSchema =
            ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());
    private final List<ReactiveFunction> functions = new ArrayList<>();
    private final Gson gson;
    private final OwnerService ownerService;
    private final ProductService productService;
    private final StageService stageService;
    private final ProductTicketCRuleService productTicketCRuleService;
    private final TicketDuplicationRuleService ticketDuplicationRuleService;
    private final ActivityService activityService;
    private final TaskService taskService;
    private final NoteService noteService;
    private final CampaignService campaignService;
    private final AdsetService adsetService;
    private final AdService adService;
    private final PartnerService partnerService;
    private final ProductCommService productCommService;
    private final DiagnosticsService diagnosticsService;

    private ProductTicketExRuleService productTicketExRuleService;

    @Autowired
    @Lazy
    public void setProductTicketExRuleService(ProductTicketExRuleService productTicketExRuleService) {
        this.productTicketExRuleService = productTicketExRuleService;
    }

    @Autowired
    @Lazy
    private TicketService self;

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
            @Lazy AdsetService adsetService,
            @Lazy AdService adService,
            @Lazy PartnerService partnerService,
            ProductCommService productCommService,
            DiagnosticsService diagnosticsService,
            Gson gson) {
        this.ownerService = ownerService;
        this.productService = productService;
        this.stageService = stageService;
        this.productTicketCRuleService = productTicketCRuleService;
        this.ticketDuplicationRuleService = ticketDuplicationRuleService;
        this.activityService = activityService;
        this.taskService = taskService;
        this.noteService = noteService;
        this.campaignService = campaignService;
        this.adsetService = adsetService;
        this.adService = adService;
        this.partnerService = partnerService;
        this.productCommService = productCommService;
        this.diagnosticsService = diagnosticsService;
        this.gson = gson;
    }

    @PostConstruct
    private void init() {

        this.functions.addAll(super.getCommonFunctions(NAMESPACE, Ticket.class, classSchema, gson));

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "CreateRequest",
                ClassSchema.ArgSpec.ofRef("ticketRequest", TicketRequest.class, classSchema),
                "created",
                Schema.ofRef("EntityProcessor.DTO.Ticket"),
                gson,
                self::createRequest));

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "CreateForCampaign",
                ClassSchema.ArgSpec.ofRef("campaignTicketRequest", CampaignTicketRequest.class, classSchema),
                "created",
                Schema.ofRef("EntityProcessor.DTO.Ticket"),
                gson,
                self::createForCampaign));

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "CreateForWebsite",
                ClassSchema.ArgSpec.string("productCode"),
                ClassSchema.ArgSpec.ofRef("campaignTicketRequest", CampaignTicketRequest.class, classSchema),
                "created",
                Schema.ofRef("EntityProcessor.DTO.Ticket"),
                gson,
                (productCode, req) -> self.createForWebsite(req, productCode)));

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "UpdateStageStatus",
                EntityProcessorArgSpec.identity("ticketId"),
                ClassSchema.ArgSpec.ofRef("ticketStatusRequest", TicketStatusRequest.class, classSchema),
                "result",
                Schema.ofRef("EntityProcessor.DTO.Ticket"),
                gson,
                self::updateStageStatus));

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "ReassignTicket",
                EntityProcessorArgSpec.identity("ticketId"),
                ClassSchema.ArgSpec.ofRef("ticketReassignRequest", TicketReassignRequest.class, classSchema),
                "result",
                Schema.ofRef("EntityProcessor.DTO.Ticket"),
                gson,
                self::reassignTicket));

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "GetTicketProductComm",
                EntityProcessorArgSpec.identity("ticketId"),
                ClassSchema.ArgSpec.ofRef("connectionType", ConnectionType.class, classSchema),
                ClassSchema.ArgSpec.ofRef("connectionSubType", ConnectionSubType.class, classSchema),
                "result",
                Schema.ofRef("EntityProcessor.DTO.Product.ProductComm"),
                gson,
                self::getTicketProductComm));

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "UpdateTag",
                EntityProcessorArgSpec.identity("ticketId"),
                ClassSchema.ArgSpec.ofRef("ticketTagRequest", TicketTagRequest.class, classSchema),
                "result",
                Schema.ofRef("EntityProcessor.DTO.Ticket"),
                gson,
                self::updateTag));
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

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> this.setAssignmentAndStage(ticket, access),
                        aTicket -> this.ownerService.getOrCreateTicketOwner(access, aTicket),
                        this::updateTicketFromOwner,
                        (aTicket, owner, oTicket) -> this.computeAndSetExpiresOn(access, oTicket))
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
                                sTicket),
                        (sTicket, ruleResult) -> {
                            ULong assignedUserId =
                                    ruleResult != null ? ruleResult.getUserId() : loggedInAssignedUser;
                            return this.setTicketAssignment(access, sTicket, assignedUserId, ruleResult);
                        })
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

    private Mono<Ticket> setTicketAssignment(
            ProcessorAccess access, Ticket ticket, ULong userId, RuleResult ruleResult) {

        if (userId == null || userId.equals(ULong.valueOf(0)))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.TICKET_ASSIGNMENT_MISSING,
                    this.getEntityPrefix(access.getAppCode()));

        ticket.setAssignedUserId(userId);
        ticket.setAssignmentRuleResult(ruleResult);

        return Mono.just(ticket)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.setTicketAssignment"));
    }

    private Mono<Ticket> updateTicketFromOwner(Ticket ticket, Owner owner) {

        ticket.setOwnerId(owner.getId());

        if (ticket.getName() == null && owner.getName() != null) ticket.setName(owner.getName());

        if (ticket.getEmail() == null && owner.getEmail() != null) ticket.setEmail(owner.getEmail());

        if (ticket.getPhoneNumber() == null && owner.getPhoneNumber() != null) {
            ticket.setDialCode(owner.getDialCode());
            ticket.setPhoneNumber(owner.getPhoneNumber());
        }

        return Mono.just(ticket).contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateTicketFromOwner"));
    }

    public Flux<Ticket> updateOwnerTickets(ProcessorAccess access, Owner owner) {
        return this.dao
                .getAllOwnerTickets(owner.getId())
                .flatMap(ticket -> this.updateTicketFromOwner(ticket, owner))
                .flatMap(tickets -> super.updateInternal(access, tickets))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateOwnerTickets"));
    }

    @Override
    protected Mono<Ticket> updatableEntity(Ticket ticket) {
        return super.updatableEntity(ticket)
                .flatMap(existing -> {
                    if (existing.isExpired())
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.TICKET_EXPIRED);

                    ULong oldAssignedUserId = existing.getAssignedUserId();
                    ULong newAssignedUserId = ticket.getAssignedUserId();

                    if (newAssignedUserId != null
                            && !newAssignedUserId.equals(oldAssignedUserId)) {
                        SecurityContextUtil.getUsersContextAuthentication()
                                .flatMap(ca -> this.diagnosticsService.log(
                                        ProcessorAccess.of(ca.getUrlAppCode(), ca.getClientCode(), true,
                                                ca.getUser(), null),
                                        com.fincity.saas.entity.processor.jooq.enums
                                                .EntityProcessorDiagnosticsObjectType.TICKET,
                                        existing.getId(),
                                        "ASSIGNMENT_UPDATE",
                                        oldAssignedUserId,
                                        newAssignedUserId,
                                        "Generic ticket update",
                                        Map.of()))
                                .onErrorResume(e -> Mono.empty())
                                .subscribe();
                    }

                    existing.setEmail(ticket.getEmail());
                    existing.setAssignedUserId(ticket.getAssignedUserId());
                    existing.setStage(ticket.getStage());
                    existing.setStatus(ticket.getStatus());
                    existing.setSubSource(ticket.getSubSource());
                    existing.setTag(ticket.getTag());

                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updatableEntity"));
    }

    @ReactiveTime
    public Mono<Ticket> createRequest(TicketRequest ticketRequest) {

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
                        (access, productIdentity) -> {
                            if (!productIdentity.getT1().isActive())
                                return this.msgService.<Boolean>throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.PRODUCT_NOT_ACTIVE);
                            return this.checkDuplicate(
                                    access,
                                    productIdentity.getT1().getId(),
                                    ticketRequest.getPhoneNumber(),
                                    ticketRequest.getEmail(),
                                    ticketRequest.getSource(),
                                    ticketRequest.getSubSource());
                        },
                        (access, productIdentity, isDuplicate) -> Mono.just(
                                ticket.setProductId(productIdentity.getT1().getId())
                                        .setDnc(productIdentity.getT2())),
                        (access, productIdentity, isDuplicate, pTicket) -> super.create(access, pTicket),
                        (access, productIdentity, isDuplicate, pTicket, created) -> {
                            RuleResult rr = pTicket.getAssignmentRuleResult();
                            this.diagnosticsService
                                    .logAssignment(
                                            access,
                                            created.getId(),
                                            DIAG_ACTION_ASSIGNMENT_INITIAL,
                                            null,
                                            created.getAssignedUserId(),
                                            rr != null ? DIAG_REASON_RULE : DIAG_REASON_LOGGED_IN_USER,
                                            rr)
                                    .onErrorResume(e -> Mono.empty())
                                    .subscribe();
                            return this.createNote(access, ticketRequest, created);
                        },
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
                        () -> this.campaignService
                                .readByCampaignId(
                                        access,
                                        cTicketRequest.getCampaignDetails().getCampaignId())
                                .switchIfEmpty(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        ProcessorMessageResourceService.IDENTITY_WRONG,
                                        this.campaignService.getEntityName(),
                                        cTicketRequest.getCampaignDetails().getCampaignId())),
                        campaign -> this.productService.readById(access, campaign.getProductId()),
                        (campaign, product) -> {
                            if (!product.isActive())
                                return this.msgService.<Ticket>throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.PRODUCT_NOT_ACTIVE);

                            Ticket ticket = Ticket.of(cTicketRequest).setCampaignId(campaign.getId());

                            CampaignTicketRequest.CampaignDetails details = cTicketRequest.getCampaignDetails();
                            if (details == null || details.getAdSetId() == null) return Mono.just(ticket);

                            return this.adsetService
                                    .readOrCreate(access, details.getAdSetId(), details.getAdSetName(), campaign.getId())
                                    .flatMap(adset -> {
                                        ticket.setAdsetId(adset.getId());

                                        return this.adService
                                                .readOrCreate(access, details.getAdId(), details.getAdName(),
                                                        adset.getId(), campaign.getId())
                                                .map(ad -> ticket.setAdId(ad.getId()))
                                                .defaultIfEmpty(ticket);
                                    });
                        },
                        (campaign, product, ticket) -> this.checkDuplicate(
                                access,
                                campaign.getProductId(),
                                cTicketRequest.getLeadDetails().getPhone(),
                                cTicketRequest.getLeadDetails().getEmail(),
                                cTicketRequest.getLeadDetails().getSource(),
                                cTicketRequest.getLeadDetails().getSubSource()),
                        (campaign, product, ticket, isDuplicate) -> Mono.just(ticket.setProductId(product.getId())),
                        (campaign, product, ticket, isDuplicate, pTicket) -> super.create(access, pTicket)
                                .switchIfEmpty(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.TICKET_CREATION_FAILED,
                                        "campaign")),
                        (campaign, product, ticket, isDuplicate, pTicket, created) -> {
                            RuleResult rr = pTicket.getAssignmentRuleResult();
                            this.diagnosticsService
                                    .logAssignment(
                                            access,
                                            created.getId(),
                                            DIAG_ACTION_ASSIGNMENT_INITIAL,
                                            null,
                                            created.getAssignedUserId(),
                                            rr != null ? DIAG_REASON_RULE : DIAG_REASON_LOGGED_IN_USER,
                                            rr)
                                    .onErrorResume(e -> Mono.empty())
                                    .subscribe();
                            return this.createNote(access, cTicketRequest, created);
                        },
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
                        product -> {
                            if (!product.isActive())
                                return this.msgService.<Ticket>throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.PRODUCT_NOT_ACTIVE);
                            return Mono.just(Ticket.of(cTicketRequest));
                        },
                        (product, ticket) -> this.checkDuplicate(
                                access,
                                product.getId(),
                                cTicketRequest.getLeadDetails().getPhone(),
                                cTicketRequest.getLeadDetails().getEmail(),
                                cTicketRequest.getLeadDetails().getSource(),
                                cTicketRequest.getLeadDetails().getSubSource()),
                        (product, ticket, isDuplicate) -> Mono.just(ticket.setProductId(product.getId())),
                        (product, ticket, isDuplicate, pTicket) -> super.create(access, pTicket)
                                .switchIfEmpty(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.TICKET_CREATION_FAILED,
                                        "website")),
                        (product, ticket, isDuplicate, pTicket, created) -> {
                            RuleResult rr = pTicket.getAssignmentRuleResult();
                            this.diagnosticsService
                                    .logAssignment(
                                            access,
                                            created.getId(),
                                            DIAG_ACTION_ASSIGNMENT_INITIAL,
                                            null,
                                            created.getAssignedUserId(),
                                            rr != null ? DIAG_REASON_RULE : DIAG_REASON_LOGGED_IN_USER,
                                            rr)
                                    .onErrorResume(e -> Mono.empty())
                                    .subscribe();
                            return this.createNote(access, cTicketRequest, created);
                        },
                        (product, ticket, isDuplicate, pTicket, created, noteCreated) -> this.activityService
                                .acCreate(access, created, null)
                                .thenReturn(created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.createForWebsite[CampaignTicketRequest]"));
    }

    public Mono<Ticket> createForPartnerImportDCRM(String appCode, String clientCode, TicketPartnerRequest request) {

        ProcessorAccess access = ProcessorAccess.of(appCode, clientCode, true, null, null);

        return FlatMapUtil.flatMapMono(
                () -> this.productService.readByIdentity(access, request.getProductId()),
                product -> {
                    if (!product.isActive())
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.PRODUCT_NOT_ACTIVE);
                    return this.stageService
                            .getParentChild(
                                    access, product.getProductTemplateId(), request.getStageId(), request.getStatusId())
                            .switchIfEmpty(this.msgService.throwMessage(
                                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                    ProcessorMessageResourceService.STAGE_MISSING));
                },
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
                (product, stageStatusEntity, partnerClient, assignedUser, existing, ticket, oTicket, created) -> {
                    this.diagnosticsService
                            .logAssignment(
                                    access,
                                    created.getId(),
                                    "ASSIGNMENT_DCRM_IMPORT",
                                    null,
                                    created.getAssignedUserId(),
                                    "DCRM partner import",
                                    null)
                            .onErrorResume(e -> Mono.empty())
                            .subscribe();

                    return this.activityService
                            .acDcrmImport(access, created, null, request.getActivityJson())
                            .thenReturn(created);
                });
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
                .flatMap(ruleCondition -> this.handleDuplicateCheck(
                        access, productId, ticketPhone, ticketMail, ruleCondition, source, subSource))
                .switchIfEmpty(
                        this.handleDuplicateCheck(access, productId, ticketPhone, ticketMail, null, source, subSource))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.checkDuplicate"));
    }

    private Mono<Boolean> handleDuplicateCheck(
            ProcessorAccess access,
            ULong productId,
            PhoneNumber ticketPhone,
            Email ticketMail,
            AbstractCondition ruleCondition,
            String source,
            String subSource) {

        if (ruleCondition != null && ruleCondition.isNonEmpty())
            return this.checkDuplicateWithRule(
                    access, productId, ticketPhone, ticketMail, ruleCondition, source, subSource);

        return this.fetchDuplicateAndLog(
                        this.getTicket(access, productId, ticketPhone, ticketMail), access, source, subSource)
                .switchIfEmpty(Mono.just(Boolean.FALSE));
    }

    private Mono<Boolean> checkDuplicateWithRule(
            ProcessorAccess access,
            ULong productId,
            PhoneNumber ticketPhone,
            Email ticketMail,
            AbstractCondition ruleCondition,
            String source,
            String subSource) {

        return FlatMapUtil.flatMapMono(
                () -> ruleCondition.removeConditionWithField(Ticket.Fields.stage),
                conditionWithoutStage ->
                        this.getTickets(conditionWithoutStage, access, productId, ticketPhone, ticketMail),
                (conditionWithoutStage, tickets) -> {
                    if (tickets.isEmpty())
                        return this.fetchDuplicateAndLog(
                                this.getTicket(access, productId, ticketPhone, ticketMail), access, source, subSource);

                    return this.fetchDuplicateAndLog(
                            this.getTicket(ruleCondition, access, productId, ticketPhone, ticketMail),
                            access,
                            source,
                            subSource);
                });
    }

    private Mono<Boolean> fetchDuplicateAndLog(
            Mono<Ticket> ticketMono, ProcessorAccess access, String source, String subSource) {

        return ticketMono
                .flatMap(ticket -> {
                    if (ticket == null || ticket.getId() == null) return Mono.just(Boolean.FALSE);

                    return activityService
                            .acReInquiry(access, ticket, null, source, subSource)
                            .then(super.throwDuplicateError(access, ticket));
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
                .createRequest(access, noteRequest)
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

                            if (!statusPresent) {
                                boolean stageUnchanged = ticket.getStage().equals(resolvedStageId);
                                if (stageUnchanged) return Mono.just(ticket);
                            }

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
                .createRequest(access, taskRequest)
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
                                    false,
                                    null);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.reassignTicket"));
    }

    public Mono<Ticket> reassignForStage(ProcessorAccess access, Ticket ticket, ULong userId, boolean isAutomatic) {

        if (userId != null)
            return this.updateTicketForReassignment(
                    access, ticket, userId, AUTOMATIC_REASSIGNMENT, isAutomatic, null);

        return FlatMapUtil.flatMapMono(
                        () -> this.productTicketCRuleService.getUserAssignment(
                                access,
                                ticket.getProductId(),
                                ticket.getStage(),
                                this.getEntityPrefix(access.getAppCode()),
                                access.getUserId(),
                                ticket,
                                false),
                        ruleResult -> ruleResult == null
                                ? Mono.just(ticket)
                                : this.updateTicketForReassignment(
                                        access, ticket, ruleResult.getUserId(), AUTOMATIC_REASSIGNMENT, isAutomatic,
                                        ruleResult))
                .switchIfEmpty(Mono.just(ticket))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.reassignForStage"));
    }

    private Mono<Ticket> updateTicketForReassignment(
            ProcessorAccess access, Ticket ticket, ULong userId, String comment, boolean isAutomatic,
            RuleResult ruleResult) {

        ULong oldUserId = ticket.getAssignedUserId();

        if (oldUserId != null && oldUserId.equals(userId)) return Mono.just(ticket);

        return FlatMapUtil.flatMapMono(
                        () -> this.setTicketAssignment(access, ticket, userId, null),
                        aTicket -> super.updateInternal(access, aTicket),
                        (aTicket, uTicket) -> {
                            String action = isAutomatic ? "ASSIGNMENT_STAGE_CHANGE" : "ASSIGNMENT_REASSIGN";
                            this.diagnosticsService
                                    .logAssignment(
                                            access, uTicket.getId(), action, oldUserId,
                                            uTicket.getAssignedUserId(), comment, ruleResult)
                                    .onErrorResume(e -> Mono.empty())
                                    .subscribe();

                            return this.activityService
                                    .acReassign(
                                            access,
                                            uTicket.getId(),
                                            comment,
                                            oldUserId,
                                            uTicket.getAssignedUserId(),
                                            isAutomatic)
                                    .thenReturn(uTicket);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateTicketForReassignment"));
    }

    private Mono<Ticket> getTicket(
            AbstractCondition condition,
            ProcessorAccess access,
            ULong productId,
            PhoneNumber ticketPhone,
            Email ticketMail) {
        return this.dao.readTicketByNumberAndEmail(condition, access, productId, ticketPhone, ticketMail);
    }

    private Mono<List<Ticket>> getTickets(
            AbstractCondition condition,
            ProcessorAccess access,
            ULong productId,
            PhoneNumber ticketPhone,
            Email ticketMail) {
        return this.dao.readTicketsByNumberAndEmail(condition, access, productId, ticketPhone, ticketMail);
    }

    public Mono<Ticket> getTicket(ProcessorAccess access, ULong productId, PhoneNumber ticketPhone, Email ticketMail) {
        return this.getTicket(null, access, productId, ticketPhone, ticketMail)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.getTicket"));
    }

    public Mono<List<Ticket>> getTickets(
            ProcessorAccess access, ULong productId, PhoneNumber ticketPhone, Email ticketMail) {
        return this.getTickets(null, access, productId, ticketPhone, ticketMail)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.getTickets"));
    }

    public Mono<Map<String, Object>> readEager(
            ProcessorAccess access, Identity identity, List<String> fields, MultiValueMap<String, String> queryParams) {

        return this.dao
                .readByIdentityAndAppCodeAndClientCodeEager(identity, access, fields, queryParams)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.readEager"));
    }

    public Mono<List<User>> readTicketUsers(Query query, String timezone) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.dao.processorAccessCondition(query.getCondition(), access),
                        (access, pCondition) -> this.dao.readDistinctAssignedUserIds(
                                pCondition, timezone, query.getSubQueryConditions()),
                        (access, pCondition, userIds) -> {
                            if (userIds.isEmpty()) return Mono.just(List.<User>of());
                            return this.securityService.getUsersInternal(
                                    userIds.stream().map(ULong::toBigInteger).toList(), null);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.readTicketUsers"));
    }

    public Flux<Ticket> updateTicketDncByClientId(ProcessorAccess access, ULong clientId, Boolean dnc) {
        return this.dao
                .getAllClientTicketsByDnc(clientId, !dnc)
                .map(ticket -> ticket.setDnc(dnc))
                .flatMap(tickets -> super.updateInternal(access, tickets))
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

    public Mono<Ticket> updateTag(Identity ticketId, TicketTagRequest ticketTagRequest) {

        if (ticketTagRequest == null || ticketTagRequest.getTag() == null)
            return this.identityMissingError(Ticket.Fields.tag);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.readByIdentity(access, ticketId),
                        (access, ticket) -> Mono.just(ticketTagRequest.getTag()),
                        (access, ticket, resolvedTag) -> {
                            Tag oldTagEnum = ticket.getTag();
                            ticket.setTag(resolvedTag);

                            return FlatMapUtil.flatMapMono(
                                    () -> this.update(access, ticket),
                                    uTicket -> ticketTagRequest.getTaskRequest() != null
                                            ? this.createTask(access, ticketTagRequest.getTaskRequest(), uTicket)
                                            : Mono.just(Boolean.FALSE),
                                    (uTicket, cTask) -> this.activityService
                                            .acTagChange(access, uTicket, ticketTagRequest.getComment(), oldTagEnum)
                                            .thenReturn(uTicket));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateTag"));
    }

    @Override
    protected Mono<Integer> deleteInternal(ProcessorAccess access, Ticket ticket) {
        return FlatMapUtil.flatMapMono(
                        () -> this.checkDeleteAccess(access, ticket),
                        checked -> Mono.zip(
                                this.taskService.evictCachesForTicket(ticket.getId()).defaultIfEmpty(Boolean.TRUE),
                                this.noteService.evictCachesForTicket(ticket.getId()).defaultIfEmpty(Boolean.TRUE))
                                .thenReturn(Boolean.TRUE),
                        (checked, evicted) -> super.deleteInternal(access, ticket))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.deleteInternal"));
    }

    private Mono<Boolean> checkDeleteAccess(ProcessorAccess access, Ticket ticket) {

        if (access.getUser() == null
                || !SecurityContextUtil.hasAuthority(
                        BusinessPartnerConstant.OWNER_ROLE, access.getUser().getAuthorities()))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    ProcessorMessageResourceService.FORBIDDEN_APP_ACCESS,
                    "delete " + this.getEntityName());

        if (ticket.getClientCode() != null
                && !ticket.getClientCode().equals(access.getClientCode()))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    ProcessorMessageResourceService.FORBIDDEN_APP_ACCESS,
                    "delete " + this.getEntityName());

        return Mono.just(Boolean.TRUE);
    }

    public Mono<Boolean> evictCachesForOwner(ULong ownerId) {
        return this.dao
                .getAllOwnerTickets(ownerId)
                .flatMap(ticket -> Mono.zip(
                        this.taskService.evictCachesForTicket(ticket.getId()).defaultIfEmpty(Boolean.TRUE),
                        this.noteService.evictCachesForTicket(ticket.getId()).defaultIfEmpty(Boolean.TRUE),
                        this.evictCache(ticket).defaultIfEmpty(Boolean.TRUE))
                        .thenReturn(Boolean.TRUE))
                .then(Mono.just(Boolean.TRUE));
    }

    private Mono<Ticket> computeAndSetExpiresOn(ProcessorAccess access, Ticket ticket) {

        if (ticket.getSource() == null || ticket.getProductId() == null) return Mono.just(ticket);

        return this.productTicketExRuleService
                .computeExpiresOn(access, ticket.getProductId(), ticket.getSource())
                .map(ticket::setExpiresOn)
                .defaultIfEmpty(ticket)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.computeAndSetExpiresOn"));
    }

    public Mono<Void> resetExpiresOn(ProcessorAccess access, ULong ticketId) {

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> this.dao.readInternal(ticketId),
                        ticket -> {
                            if (ticket == null) return Mono.empty();

                            if (ticket.isExpired()
                                    && (access.getUser() == null
                                            || !com.fincity.saas.commons.security.util.SecurityContextUtil
                                                    .hasAuthority(
                                                            com.fincity.saas.entity.processor.constant
                                                                    .BusinessPartnerConstant.OWNER_ROLE,
                                                            access.getUser().getAuthorities())))
                                return this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        ProcessorMessageResourceService.TICKET_EXPIRED);

                            if (ticket.getSource() == null || ticket.getProductId() == null)
                                return Mono.just(ticket);

                            return this.productTicketExRuleService
                                    .computeExpiresOn(access, ticket.getProductId(), ticket.getSource())
                                    .map(ticket::setExpiresOn)
                                    .defaultIfEmpty(ticket);
                        },
                        (ticket, updatedTicket) -> {
                            Ticket toSave = updatedTicket != null ? updatedTicket : ticket;
                            if (toSave == null) return Mono.empty();
                            return super.updateInternal(access, toSave);
                        })
                .then()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.resetExpiresOn"));
    }

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {
        return this.defaultSchemaRepositoryFor(Ticket.class, classSchema);
    }
}
