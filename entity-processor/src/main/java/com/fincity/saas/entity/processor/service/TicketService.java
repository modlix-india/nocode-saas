package com.fincity.saas.entity.processor.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

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
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.aspect.ReactiveTime;
import com.fincity.saas.entity.processor.constant.BusinessPartnerConstant;
import com.fincity.saas.entity.processor.dao.TicketDAO;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.dto.DiagnosticsLog;
import com.fincity.saas.entity.processor.dto.Owner;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.product.Product;
import com.fincity.saas.entity.processor.dto.product.ProductComm;
import com.fincity.saas.entity.processor.enums.EntitySeries;

import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.response.WhatsappConversationResponse;
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
import com.fincity.saas.entity.processor.model.request.ticket.TicketWhatsappNumberRequest;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import com.fincity.saas.entity.processor.service.content.NoteService;
import com.fincity.saas.entity.processor.service.content.TaskService;
import com.fincity.saas.entity.processor.service.message.TicketMessageService;
import com.fincity.saas.entity.processor.service.product.ProductCommService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import com.fincity.saas.entity.processor.service.product.ProductTicketCRuleService;
import com.fincity.saas.entity.processor.service.product.ProductTicketExRuleService;
import com.fincity.saas.entity.processor.service.rule.TicketDuplicationRuleService;
import com.fincity.saas.entity.processor.util.EntityProcessorArgSpec;
import com.google.gson.Gson;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TicketService extends BaseProcessorService<EntityProcessorTicketsRecord, Ticket, TicketDAO>
        implements IRepositoryProvider {

    private static final String DIAG_ACTION_ASSIGNMENT_INITIAL = "ASSIGNMENT_INITIAL";
    private static final String DIAG_REASON_RULE = "Initial assignment via rule";
    private static final String DIAG_REASON_LOGGED_IN_USER = "Initial assignment to logged-in user";
    private static final String DIAG_LOG_FAILURE = "Failed to log diagnostics for ticket {}: {}";
    private static final String AUTOMATIC_REASSIGNMENT = "Automatic Reassignment for Stage update.";
    private static final String NAMESPACE = "EntityProcessor.Ticket";
    private static final String SOURCE_WHATSAPP = "WhatsApp";

    /**
     * How much of a customer's WhatsApp profile name may become a deal name.
     *
     * <p>Well under {@code NAME}'s 512 characters, and that is the point: the column's width is not
     * the right bound for a value somebody else chooses. WhatsApp's own limit is 25, so anything past
     * this is not a name and a 512-character one would wreck every list it appears in. Truncation is
     * safe here because nothing matches on it.
     */
    private static final int MAX_INBOUND_NAME_LENGTH = 128;
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
    private final ConversionActionMappingService conversionActionMappingService;
    private final ConversionEventService conversionEventService;
    private final TicketMessageService ticketMessageService;

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
            @Lazy ConversionActionMappingService conversionActionMappingService,
            @Lazy ConversionEventService conversionEventService,
            TicketMessageService ticketMessageService,
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
        this.conversionActionMappingService = conversionActionMappingService;
        this.conversionEventService = conversionEventService;
        this.ticketMessageService = ticketMessageService;
        this.gson = gson;
    }

    /**
     * Cross-tenant id lookup without {@code hasAccess()}. Worker-driven flows only
     * (e.g. {@code ConversionsDrainService}); never expose via a public controller.
     */
    public Mono<Ticket> findById(ULong id) {
        return this.dao.readById(id)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.findById"));
    }

    /**
     * Records that a lead asked not to be contacted on WhatsApp.
     *
     * <p>No {@code hasAccess()}, like {@link #findById} above it and for the same reason: this is
     * driven by an inbound message arriving from the bridge, where there is no user and never will
     * be. Nothing here is caller-supplied except the message text we just stored ourselves.
     *
     * <p>Idempotent. A lead who writes "stop" three times should not have the original timestamp
     * overwritten, because when they first asked is the fact that matters if this is ever questioned.
     */
    public Mono<Ticket> markWhatsappOptedOut(ULong ticketId, String triggeringText) {

        return this.findById(ticketId)
                .filter(ticket -> !Boolean.TRUE.equals(ticket.getWhatsappOptedOut()))
                .flatMap(ticket -> this.dao.update(ticket.setWhatsappOptedOut(Boolean.TRUE)
                        .setWhatsappOptedOutAt(LocalDateTime.now(java.time.ZoneOffset.UTC))
                        .setWhatsappOptedOutText(
                                triggeringText == null
                                        ? null
                                        : triggeringText.substring(0, Math.min(triggeringText.length(), 512)))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.markWhatsappOptedOut"));
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

        // Skip duplicate check and assignment/stage setup for updates (ticket already has an ID)
        if (ticket.getId() != null) return Mono.just(ticket);

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> this.checkDuplicateAndCarryAssignment(access, ticket),
                        preferredUserId -> this.setAssignmentAndStage(ticket, access, preferredUserId),
                        (preferredUserId, aTicket) -> this.ownerService.getOrCreateTicketOwner(access, aTicket),
                        (preferredUserId, aTicket, owner) -> this.updateTicketFromOwner(aTicket, owner),
                        (preferredUserId, aTicket, owner, oTicket) -> this.computeAndSetExpiresOn(access, oTicket))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.checkEntity"));
    }

    /**
     * Checks for duplicate tickets and, when a duplicate rule allows creation,
     * returns the existing ticket's assignedUserId as a preferred user hint.
     * The returned userId is validated against the current distribution pool
     * in setAssignmentAndStage rather than being applied directly.
     */
    private Mono<ULong> checkDuplicateAndCarryAssignment(ProcessorAccess access, Ticket ticket) {

        PhoneNumber phone = ticket.getPhoneNumber() != null
                ? PhoneNumber.of(ticket.getDialCode(), ticket.getPhoneNumber())
                : null;

        Email email = ticket.getEmail() != null ? Email.of(ticket.getEmail()) : null;

        if (phone == null && email == null) return Mono.empty();

        return this.checkDuplicate(
                access, ticket.getProductId(), phone, email, ticket.getSource(), ticket.getSubSource())
                .flatMap(isDuplicate -> this.getTicket(access, ticket.getProductId(), phone, email)
                        .mapNotNull(Ticket::getAssignedUserId));
    }

    private Mono<Ticket> setAssignmentAndStage(Ticket ticket, ProcessorAccess access, ULong preferredUserId) {

        Map<String, Object> trace = new HashMap<>();
        ticket.setEvaluationTrace(trace);

        if (ticket.getAssignedUserId() != null && ticket.getStage() != null) {
            trace.put("skippedReason", "ALREADY_ASSIGNED_AND_STAGED");
            trace.put("assignedUserId", ticket.getAssignedUserId().toString());
            trace.put("stage", ticket.getStage().toString());
            return Mono.just(ticket);
        }

        if (ticket.getAssignedUserId() != null) {
            trace.put("skippedReason", "ALREADY_ASSIGNED_EXTERNALLY");
            trace.put("assignedUserId", ticket.getAssignedUserId().toString());
            return this.setDefaultStage(access, ticket)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.checkTicket"));
        }

        ULong loggedInAssignedUser = access.isOutsideUser() ? null : access.getUserId();
        ULong userIdHint = preferredUserId != null ? preferredUserId : loggedInAssignedUser;

        trace.put("preferredUserId", preferredUserId != null ? preferredUserId.toString() : null);
        trace.put("loggedInFallbackUserId", loggedInAssignedUser != null ? loggedInAssignedUser.toString() : null);
        trace.put("userIdHint", userIdHint != null ? userIdHint.toString() : null);
        trace.put("isOutsideUser", access.isOutsideUser());

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> this.setDefaultStage(access, ticket)
                                .doOnNext(t -> trace.put("defaultStage",
                                        t.getStage() != null ? t.getStage().toString() : null)),
                        sTicket -> this.productTicketCRuleService.getUserAssignment(
                                access,
                                sTicket.getProductId(),
                                sTicket.getStage(),
                                this.getEntityPrefix(access.getAppCode()),
                                userIdHint,
                                sTicket),
                        (sTicket, ruleResult) -> {
                            ULong assignedUserId;
                            String assignedVia;

                            if (ruleResult != null) {
                                assignedUserId = ruleResult.getUserId();
                                assignedVia = "RULE";
                            } else if (preferredUserId != null) {
                                assignedUserId = preferredUserId;
                                assignedVia = "PREFERRED_USER_NO_RULES";
                            } else {
                                assignedUserId = loggedInAssignedUser;
                                assignedVia = "LOGGED_IN_USER_FALLBACK";
                            }

                            trace.put("finalAssignedUserId",
                                    assignedUserId != null ? assignedUserId.toString() : null);
                            trace.put("assignedVia", assignedVia);
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

        return FlatMapUtil.flatMapMono(
            () -> super.updatableEntity(ticket),

            existing -> SecurityContextUtil.getUsersContextAuthentication(),

            (existing, ca) -> {

                if (!existing.isExpired()) return Mono.just(true);

                if (!existing.getClientCode().equals(ca.getClientCode()) ||
                    !SecurityContextUtil.hasAuthority(BusinessPartnerConstant.OWNER_ROLE, ca.getUser().getAuthorities()))
                    return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.TICKET_EXPIRED);

                return Mono.just(true);
            },

            (existing, ca, canUpdate) -> {
                ULong oldAssignedUserId = existing.getAssignedUserId();
                ULong newAssignedUserId = ticket.getAssignedUserId();

                existing.setEmail(ticket.getEmail());
                existing.setAssignedUserId(ticket.getAssignedUserId());
                existing.setSubSource(ticket.getSubSource());
                existing.setTag(ticket.getTag());

                ProcessorAccess access = ProcessorAccess.of(ca.getUrlAppCode(), ca.getClientCode(), true,
                        ca.getUser(), null);

                Mono<Ticket> result = this.applyStageStatus(access, existing, ticket)
                        .flatMap(sTicket -> this.computeAndSetExpiresOn(access, sTicket));

                if (newAssignedUserId != null
                        && !newAssignedUserId.equals(oldAssignedUserId)) {
                    return result.flatMap(eTicket ->
                        this.diagnosticsService.log(
                            access,
                            com.fincity.saas.entity.processor.jooq.enums
                                    .EntityProcessorDiagnosticsObjectType.TICKET,
                            eTicket.getId(),
                            "ASSIGNMENT_UPDATE",
                            oldAssignedUserId,
                            newAssignedUserId,
                            "Generic ticket update",
                            Map.of())
                        .onErrorResume(e -> {
                            logger.error(DIAG_LOG_FAILURE, eTicket.getId(), e.getMessage());
                            return Mono.empty();
                        })
                        .thenReturn(eTicket));
                }

                return result;
            }
        )
        .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updatableEntity"));
    }

    /**
     * Moves a deal's stage and status on the generic update path, refusing anything that does not
     * belong to the deal's own product template.
     *
     * <p>These two were a plain assignment until now, so a payload could put any stage id at all on
     * a deal - one from another product template, or from another tenant entirely - and it was
     * written and answered 200. The dedicated {@code /stage} route has always checked through
     * {@link #updateStageStatus}, but the kanban drag-drop and the deal profile form both come
     * through here instead, so the check has to live here too.
     *
     * <p>A stage the payload does not carry leaves the deal where it is rather than clearing it.
     * Nulling it is never a real operation - a deal with no stage falls out of every board - and a
     * client that simply does not manage the field would otherwise erase it on an unrelated save,
     * which is what a PUT omitting {@code stage} used to do.
     *
     * <p>Status follows its stage, because it has no meaning apart from one. A status that is not a
     * child of the resolved stage is dropped rather than refused, which is exactly what
     * {@link #updateStageStatus} does with one: the kanban sends the deal's existing status
     * alongside the new stage on every drop, and refusing that would break dragging a card.
     *
     * <p>Validated against the deal's stored product, never the payload's. Product is not updatable
     * here, so the payload's is either the same or an attempt to change it, and neither is the right
     * thing to check a stage against.
     */
    private Mono<Ticket> applyStageStatus(ProcessorAccess access, Ticket existing, Ticket incoming) {

        ULong newStage = incoming.getStage();

        if (newStage == null) return Mono.just(existing);

        if (newStage.equals(existing.getStage()) && Objects.equals(incoming.getStatus(), existing.getStatus()))
            return Mono.just(existing);

        return FlatMapUtil.flatMapMono(
                        () -> this.productService.readById(access, existing.getProductId()),
                        product -> {
                            if (product.getProductTemplateId() == null)
                                return this.msgService.<Ticket>throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.PRODUCT_TEMPLATE_TYPE_MISSING,
                                        product.getId());

                            return this.stageService
                                    .getParentChild(
                                            access,
                                            product.getProductTemplateId(),
                                            Identity.of(newStage.toBigInteger()),
                                            incoming.getStatus() == null
                                                    ? null
                                                    : Identity.of(incoming.getStatus().toBigInteger()))
                                    .map(stageStatus -> {
                                        existing.setStage(
                                                stageStatus.getKey().getId());
                                        existing.setStatus(
                                                stageStatus.getValue().isEmpty()
                                                        ? null
                                                        : stageStatus
                                                                .getValue()
                                                                .getFirst()
                                                                .getId());
                                        return existing;
                                    })
                                    .switchIfEmpty(this.msgService.throwMessage(
                                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                            ProcessorMessageResourceService.STAGE_MISSING));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.applyStageStatus"));
    }

    @Override
    public Mono<Ticket> create(ProcessorAccess access, Ticket entity) {
        return super.create(access, entity)
                .flatMap(created -> this.logInitialAssignment(access, entity, created).thenReturn(created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.create[ProcessorAccess, Ticket]"));
    }

    private Mono<DiagnosticsLog> logInitialAssignment(ProcessorAccess access, Ticket pTicket, Ticket created) {

        if (created.getAssignedUserId() == null) return Mono.empty();

        RuleResult rr = pTicket.getAssignmentRuleResult();

        return this.diagnosticsService
                .logAssignment(
                        access,
                        created.getId(),
                        DIAG_ACTION_ASSIGNMENT_INITIAL,
                        null,
                        created.getAssignedUserId(),
                        rr != null ? DIAG_REASON_RULE : DIAG_REASON_LOGGED_IN_USER,
                        rr,
                        pTicket.getEvaluationTrace())
                .onErrorResume(e -> {
                    logger.error(DIAG_LOG_FAILURE, created.getId(), e.getMessage());
                    return Mono.empty();
                });
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
                                return this.msgService.<Ticket>throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.PRODUCT_NOT_ACTIVE);
                            return Mono.just(
                                    ticket.setProductId(productIdentity.getT1().getId())
                                            .setDnc(productIdentity.getT2()));
                        },
                        (access, productIdentity, pTicket) -> this.create(access, pTicket),
                        (access, productIdentity, pTicket, created) ->
                                this.createNote(access, ticketRequest, created),
                        (access, productIdentity, pTicket, created, noteCreated) -> this.activityService
                                .acCreate(created)
                                .then(this.ticketMessageService.enqueueForStage(access, created))
                                .thenReturn(created))
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
                        campaign -> this.resolveCampaignProduct(access, campaign),
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
                                                        null, null, adset.getId(), campaign.getId())
                                                .map(ad -> ticket.setAdId(ad.getId()))
                                                .defaultIfEmpty(ticket);
                                    });
                        },
                        (campaign, product, ticket) -> Mono.just(ticket.setProductId(product.getId())),
                        (campaign, product, ticket, pTicket) -> this.create(access, pTicket)
                                .switchIfEmpty(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.TICKET_CREATION_FAILED,
                                        "campaign")),
                        (campaign, product, ticket, pTicket, created) ->
                                this.createNote(access, cTicketRequest, created),
                        (campaign, product, ticket, pTicket, created, noteCreated) -> this.activityService
                                .acCreate(access, created, null)
                                .thenReturn(created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.createForCampaign[cTicketRequest]"));
    }

    public Mono<Ticket> createForWebsite(CampaignTicketRequest cTicketRequest, String productCode) {

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
                        (product, ticket) -> Mono.just(ticket.setProductId(product.getId())),
                        (product, ticket, pTicket) -> this.attachCampaignAttribution(access, pTicket, cTicketRequest),
                        (product, ticket, pTicket, attributedTicket) -> this.create(access, attributedTicket)
                                .switchIfEmpty(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.TICKET_CREATION_FAILED,
                                        "website")),
                        (product, ticket, pTicket, attributedTicket, created) -> this.activityService
                                .acCreate(access, created, null)
                                .thenReturn(created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.createForWebsite[CampaignTicketRequest]"));
    }

    private Mono<Ticket> attachCampaignAttribution(
            ProcessorAccess access, Ticket ticket, CampaignTicketRequest cTicketRequest) {

        CampaignTicketRequest.CampaignDetails cd = cTicketRequest.getCampaignDetails();
        if (cd == null || StringUtil.safeIsBlank(cd.getCampaignId())) return Mono.just(ticket);

        return this.campaignService
                .readByCampaignId(access, cd.getCampaignId())
                .flatMap(campaign -> {
                    ticket.setCampaignId(campaign.getId());
                    if (StringUtil.safeIsBlank(cd.getAdSetId())) return Mono.just(ticket);

                    return this.adsetService
                            .readOrCreate(access, cd.getAdSetId(), cd.getAdSetName(), campaign.getId())
                            .flatMap(adset -> {
                                ticket.setAdsetId(adset.getId());
                                if (StringUtil.safeIsBlank(cd.getAdId())) return Mono.just(ticket);

                                return this.adService
                                        .readOrCreate(
                                                access, cd.getAdId(), cd.getAdName(), null, null, adset.getId(), campaign.getId())
                                        .map(ad -> ticket.setAdId(ad.getId()))
                                        .defaultIfEmpty(ticket);
                            })
                            .defaultIfEmpty(ticket);
                })
                .defaultIfEmpty(ticket);
    }

    /**
     * Resolves the product a campaign lead should be attributed to, under
     * many-to-many. Uses the deprecated "primary" {@code PRODUCT_ID} when set
     * (kept in sync with the first linked product); otherwise falls back to the
     * single linked product from the join. If a campaign has zero or multiple
     * products and no primary, attribution cannot be disambiguated and a clear
     * error is raised rather than silently picking one.
     */
    private Mono<Product> resolveCampaignProduct(ProcessorAccess access, Campaign campaign) {
        if (campaign.getProductId() != null)
            return this.productService.readById(access, campaign.getProductId());

        return this.campaignService.findLinkedProductIds(campaign.getId()).flatMap(productIds -> productIds.size() == 1
                ? this.productService.readById(access, productIds.get(0))
                : this.msgService.<Product>throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.MISSING_PARAMETERS,
                        "product for campaign " + campaign.getCampaignId()));
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

        if (ruleCondition == null || !ruleCondition.isNonEmpty()) {
            // No duplication rule for this source — block all duplicates.
            return this.checkWithinClientDuplicate(access, productId, ticketPhone, ticketMail, source, subSource);
        }

        // A duplication rule exists for this source. Use the rule-based check which respects
        // maxStageId and rule conditions. If the rule-based check chain fails (empty), fall back
        // to within-client check.
        return this.checkDuplicateWithRule(
                access, productId, ticketPhone, ticketMail, ruleCondition, source, subSource)
                .switchIfEmpty(this.checkWithinClientDuplicate(
                        access, productId, ticketPhone, ticketMail, source, subSource));
    }

    private Mono<Boolean> checkWithinClientDuplicate(
            ProcessorAccess access,
            ULong productId,
            PhoneNumber ticketPhone,
            Email ticketMail,
            String source,
            String subSource) {

        if (!access.isOutsideUser() || access.getUser() == null)
            return this.fetchDuplicateAndLog(
                            this.getTicket(access, productId, ticketPhone, ticketMail), access, source, subSource)
                    .switchIfEmpty(Mono.just(Boolean.FALSE));

        AbstractCondition clientIdCondition = FilterCondition.make(
                BaseProcessorDto.Fields.clientId, ULongUtil.valueOf(access.getUser().getClientId()));

        return this.fetchDuplicateAndLog(
                        this.getTicket(clientIdCondition, access, productId, ticketPhone, ticketMail),
                        access, source, subSource)
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

        // Strip entity prefix (e.g., "Deal.source" -> "source") from condition fields
        // since the DAO queries use raw column names, not prefixed evaluator names.
        AbstractCondition dbCondition = stripFieldPrefix(ruleCondition);

        return FlatMapUtil.flatMapMono(
                () -> dbCondition.removeConditionWithField(Ticket.Fields.stage),
                conditionWithoutStage ->
                        this.getTickets(conditionWithoutStage, access, productId, ticketPhone, ticketMail),
                (conditionWithoutStage, tickets) -> {
                    if (tickets.isEmpty())
                        return this.fetchDuplicateAndLog(
                                this.getTicket(access, productId, ticketPhone, ticketMail), access, source, subSource);

                    return this.fetchDuplicateAndLog(
                            this.getTicket(dbCondition, access, productId, ticketPhone, ticketMail),
                            access,
                            source,
                            subSource);
                });
    }

    private static AbstractCondition stripFieldPrefix(AbstractCondition condition) {
        if (condition == null) return null;

        if (condition instanceof FilterCondition fc) {
            String field = fc.getField();
            if (field != null && field.contains(".")) {
                return FilterCondition.of(
                        field.substring(field.lastIndexOf('.') + 1),
                        fc.getValue(),
                        fc.getOperator())
                        .setToValue(fc.getToValue())
                        .setMultiValue(fc.getMultiValue())
                        .setNegate(fc.isNegate());
            }
            return fc;
        }

        if (condition instanceof ComplexCondition cc && cc.getConditions() != null) {
            List<AbstractCondition> stripped = cc.getConditions().stream()
                    .map(TicketService::stripFieldPrefix)
                    .toList();
            return new ComplexCondition()
                    .setOperator(cc.getOperator())
                    .setConditions(stripped)
                    .setNegate(cc.isNegate());
        }

        return condition;
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

        ULong oldStage = ticket.getStage();
        ULong oldStatus = ticket.getStatus();

        boolean doReassignment = !oldStage.equals(stageId);

        logger.info("updateTicketStage: ticketId={}, oldStage={}, newStage={}, statusId={}, doReassignment={}, reassignUserId={}",
                ticket.getId(), oldStage, stageId, statusId, doReassignment, reassignUserId);

        ticket.setStage(stageId);
        ticket.setStatus(statusId);

        return this.computeAndSetExpiresOn(access, ticket)
                .flatMap(eTicket -> FlatMapUtil.flatMapMono(
                        () -> super.updateInternal(access, eTicket),
                        uTicket -> taskRequest != null
                                ? this.createTask(access, taskRequest, uTicket)
                                : Mono.just(Boolean.FALSE),
                        (uTicket, cTask) -> this.activityService
                                .acStageStatus(access, uTicket, comment, oldStage)
                                .thenReturn(uTicket),
                        (uTicket, cTask, fTicket) -> doReassignment
                                ? this.reassignForStage(access, fTicket, reassignUserId, true)
                                : Mono.just(fTicket),
                        (uTicket, cTask, fTicket, rTicket) -> this.enqueueConversionEventsForStageTransition(
                                        access, rTicket, oldStage, oldStatus)
                                .then(this.enqueueStageMessages(access, rTicket, oldStage, oldStatus))
                                .thenReturn(rTicket)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateTicketStage"));
    }

    /**
     * Queues whatever the new stage's rules say this deal is owed.
     *
     * <p>The stage-change half of the messaging trigger. Creation was already wired; a move was not,
     * which meant every rule beyond the first stage was configurable and dead. A deal reaching "Site
     * visit booked" is exactly when its confirmation should go out.
     *
     * <p>Skipped when neither stage nor status actually changed, so re-saving a deal does not re-run
     * the rules. Even if it did, the per-rule check in the channel service would refuse the duplicate;
     * this is the cheaper of the two guards, not the load-bearing one.
     *
     * <p>Best-effort, like the conversion hook above it. Failing a person's stage update because a
     * message could not be queued would be the wrong trade, and the queue row is not the message: the
     * sweeper decides whether anything is sent at all.
     */
    private Mono<Void> enqueueStageMessages(
            ProcessorAccess access, Ticket ticket, ULong oldStage, ULong oldStatus) {

        if (java.util.Objects.equals(oldStage, ticket.getStage())
                && java.util.Objects.equals(oldStatus, ticket.getStatus())) return Mono.empty();

        return this.ticketMessageService.enqueueForStage(access, ticket).onErrorResume(e -> {
            logger.error(
                    "Could not queue stage messages for ticket {} moving to stage {}.",
                    ticket.getId(),
                    ticket.getStage(),
                    e);
            return Mono.empty();
        });
    }

    /**
     * Best-effort hook: looks up active conversion-action mappings for the new
     * (stage, status, product_template) and enqueues an outbox event per match.
     * Errors are swallowed — a downstream Meta/Google enqueue failure must not
     * abort the user's stage update.
     *
     * <p>Two attribution gates prevent enqueuing non-ad-platform tickets:
     * <ol>
     *   <li>{@code ticket.campaignId == null} — partner-referred / walk-in / direct
     *       tickets have no ad-platform attribution and cannot become conversion events.</li>
     *   <li>{@code campaign.platform != mapping.platform} — a Meta mapping must not
     *       fire for a Google-attributed ticket (and vice versa); doing so would
     *       feed wrong-platform conversions back to the ad platform and inflate its
     *       reported ROAS.</li>
     * </ol>
     *
     * <p>Per Meta CAPI doc Part 6.2, {@code action_source} must be derived from
     * ticket origin: {@code system_generated} when {@code adData.lead_id} is set
     * (lead-form webhook origin), otherwise {@code website}.
     */
    private Mono<Void> enqueueConversionEventsForStageTransition(
            ProcessorAccess access, Ticket ticket, ULong oldStage, ULong oldStatus) {

        if (java.util.Objects.equals(oldStage, ticket.getStage())
                && java.util.Objects.equals(oldStatus, ticket.getStatus())) {
            return Mono.empty();
        }

        // Gate 1: no campaign attribution → no conversion event.
        if (ticket.getCampaignId() == null) {
            return Mono.empty();
        }

        com.fincity.saas.entity.processor.enums.ConversionActionSource source = deriveActionSource(ticket);

        return this.campaignService
                .findById(ticket.getCampaignId())
                .flatMap(campaign -> this.conversionActionMappingService
                        .findActiveByTrigger(
                                access,
                                ticket.getStage(),
                                ticket.getStatus(),
                                ticket.getProductTemplateId(),
                                // Meta uses a single Pixel/dataset and routes by user identifiers; Google
                                // under MCC + cross-account does the same via Enhanced Conversions for
                                // Leads. Either way, the campaign's sub-account is not the right filter
                                // key -- platformAccountId on the mapping identifies the action's owner,
                                // not which campaign it answers to. See [[ECL conversion model]].
                                null)
                        // Gate 2: only fire mappings whose platform matches the ticket's source platform.
                        .filter(mapping -> java.util.Objects.equals(
                                mapping.getCampaignPlatform(), campaign.getCampaignPlatform()))
                        .concatMap(mapping -> this.conversionEventService
                                .enqueue(access, ticket, mapping, source)
                                .onErrorResume(e -> {
                                    logger.warn(
                                            "Failed to enqueue conversion event for ticket {} mapping {}: {}",
                                            ticket.getId(),
                                            mapping.getId(),
                                            e.getMessage());
                                    return Mono.empty();
                                }))
                        .then())
                // Campaign row vanished between ticket attribution and stage transition — treat
                // as no-attribution so we don't enqueue.
                .switchIfEmpty(Mono.empty());
    }

    private static com.fincity.saas.entity.processor.enums.ConversionActionSource deriveActionSource(Ticket ticket) {
        java.util.Map<String, Object> adData = ticket.getAdData();
        if (adData != null && (adData.containsKey("lead_id") || adData.containsKey("leadgen_id"))) {
            return com.fincity.saas.entity.processor.enums.ConversionActionSource.SYSTEM_GENERATED;
        }
        // Defense in depth: a ticket sourced from a Meta/Instagram lead-form ad
        // (LeadSource.SOCIAL_MEDIA) is never a website event, regardless of whether
        // adData propagated the lead_id. Without this fallback, any webhook payload
        // that missed lead_id stamping would silently fall through to WEBSITE and
        // get dispatched without fbc — un-attributable on Meta's side.
        if (com.fincity.saas.entity.processor.enums.LeadSource.SOCIAL_MEDIA
                .getName()
                .equalsIgnoreCase(ticket.getSource())) {
            return com.fincity.saas.entity.processor.enums.ConversionActionSource.SYSTEM_GENERATED;
        }
        return com.fincity.saas.entity.processor.enums.ConversionActionSource.WEBSITE;
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

    public Mono<Integer> bulkReassignTickets(
            Query query, List<ULong> userIds, String comment, String timezone) {

        if (userIds == null || userIds.isEmpty())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    "reassign user");

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> {
                            List<ULong> subOrg = access.getUserInherit().getSubOrg();
                            for (ULong uid : userIds) {
                                if (!subOrg.contains(uid))
                                    return this.msgService.<Boolean>throwMessage(
                                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                            ProcessorMessageResourceService.INVALID_USER_ACCESS);
                            }
                            return Mono.just(Boolean.TRUE);
                        },
                        (access, valid) -> this.dao.processorAccessCondition(query.getCondition(), access),
                        (access, valid, pCondition) -> {
                            AtomicInteger counter = new AtomicInteger(0);
                            return this.dao.readAllForBulkOp(pCondition, timezone, query.getSubQueryConditions())
                                    .flatMap(ticket -> {
                                        ULong userId = userIds.get(counter.getAndIncrement() % userIds.size());
                                        return this.updateTicketForReassignment(
                                                access, ticket, userId, comment, false, null)
                                                .onErrorResume(e -> {
                                                    logger.error("Bulk reassign failed for ticket {}: {}",
                                                            ticket.getId(), e.getMessage());
                                                    return Mono.empty();
                                                });
                                    })
                                    .count()
                                    .map(Long::intValue);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.bulkReassignTickets"));
    }

    public Mono<Ticket> reassignForWalkIn(ProcessorAccess access, Ticket ticket, ULong userId) {

        if (userId == null) return Mono.just(ticket);

        ULong oldUserId = ticket.getAssignedUserId();

        if (oldUserId != null && oldUserId.equals(userId)) return Mono.just(ticket);

        return FlatMapUtil.flatMapMono(
                        () -> this.setTicketAssignment(access, ticket, userId, null),
                        aTicket -> super.updateInternal(access, aTicket),
                        (aTicket, uTicket) -> Mono.when(
                                    this.diagnosticsService
                                            .logAssignment(
                                                    access, uTicket.getId(), "ASSIGNMENT_WALK_IN_FORM", oldUserId,
                                                    uTicket.getAssignedUserId(), "User assigned from walk-in form", null)
                                            .onErrorResume(e -> {
                                                logger.error(DIAG_LOG_FAILURE, uTicket.getId(), e.getMessage());
                                                return Mono.empty();
                                            }),
                                    this.activityService
                                            .acReassign(
                                                    access,
                                                    uTicket.getId(),
                                                    "User assigned from walk-in form",
                                                    oldUserId,
                                                    uTicket.getAssignedUserId(),
                                                    false))
                                    .thenReturn(uTicket))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.reassignForWalkIn"));
    }

    public Mono<Ticket> validateAssignedUser(ProcessorAccess access, Ticket ticket) {

        if (ticket.getAssignedUserId() == null) return Mono.just(ticket);

        return this.productTicketCRuleService
                .getUserAssignment(
                        access,
                        ticket.getProductId(),
                        ticket.getStage(),
                        this.getEntityPrefix(access.getAppCode()),
                        ticket.getAssignedUserId(),
                        ticket,
                        true)
                .flatMap(ruleResult -> {
                    if (ruleResult.getUserId().equals(ticket.getAssignedUserId()))
                        return Mono.just(ticket);

                    logger.info(
                            "validateAssignedUser: ticketId={}, oldUser={}, newUser={} (old user no longer in distribution)",
                            ticket.getId(),
                            ticket.getAssignedUserId(),
                            ruleResult.getUserId());

                    return this.updateTicketForReassignment(
                            access,
                            ticket,
                            ruleResult.getUserId(),
                            "Reassigned: user no longer in distribution",
                            true,
                            ruleResult);
                })
                .switchIfEmpty(Mono.just(ticket))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.validateAssignedUser"));
    }

    public Mono<Ticket> reassignForStage(ProcessorAccess access, Ticket ticket, ULong userId, boolean isAutomatic) {

        logger.info("reassignForStage: ticketId={}, productId={}, stage={}, userId={}, assignedUserId={}",
                ticket.getId(), ticket.getProductId(), ticket.getStage(), userId, ticket.getAssignedUserId());

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

                            return Mono.when(
                                    this.diagnosticsService
                                            .logAssignment(
                                                    access, uTicket.getId(), action, oldUserId,
                                                    uTicket.getAssignedUserId(), comment, ruleResult)
                                            .onErrorResume(e -> {
                                                logger.error(DIAG_LOG_FAILURE, uTicket.getId(), e.getMessage());
                                                return Mono.empty();
                                            }),
                                    this.activityService
                                            .acReassign(
                                                    access,
                                                    uTicket.getId(),
                                                    comment,
                                                    oldUserId,
                                                    uTicket.getAssignedUserId(),
                                                    isAutomatic))
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

    /**
     * The deals on a customer's number this caller may see. See {@link
     * com.fincity.saas.entity.processor.dao.TicketDAO#readAccessibleTicketIdsByPhone}.
     */
    public Mono<List<ULong>> readAccessibleTicketIdsByPhone(
            ProcessorAccess access, String phoneNumber, ULong productId) {
        return this.dao.readAccessibleTicketIdsByPhone(access, phoneNumber, productId);
    }

    /**
     * The same, for a WhatsApp thread, which matches either number a deal can be messaged on. See
     * {@link com.fincity.saas.entity.processor.dao.TicketDAO#readAccessibleTicketIdsByWhatsappNumber}.
     */
    public Mono<List<ULong>> readAccessibleTicketIdsByWhatsappNumber(
            ProcessorAccess access, String number, ULong productId) {
        return this.dao.readAccessibleTicketIdsByWhatsappNumber(access, number, productId);
    }

    /**
     * The WhatsApp inbox. See {@link
     * com.fincity.saas.entity.processor.dao.TicketDAO#readConversations}.
     */
    public Mono<Page<WhatsappConversationResponse>> readConversations(
            ProcessorAccess access, ULong productId, String search, Pageable pageable) {
        return this.dao
                .readConversations(access, productId, search, pageable)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.readConversations"));
    }

    /**
     * Records that a WhatsApp message was exchanged with a customer, and answers which deal it
     * belongs to.
     *
     * <p>Called by the message service on both directions. It knows the business number and the
     * customer's number but nothing about deals, so everything below the phone number happens here.
     *
     * <p>Three things happen, in order:
     *
     * <ol>
     *   <li><b>Match.</b> Every active deal on the customer's number, within the product scope. An
     *       empty {@code productIds} means the business number is the tenant default and serves every
     *       product, so the match is on the number alone.
     *   <li><b>Create, when nothing matched and {@code createIfMissing}.</b> A stranger messaging
     *       the advertised number is a lead, and with the inbox gated on deal access a message with
     *       no deal would be visible to nobody. See {@link #createFromInboundWhatsapp}.
     *   <li><b>Touch.</b> Every matched deal gets {@code LAST_MESSAGE_AT}, not just the newest, so a
     *       customer holding several deals sees them all rise together. The thread is shared across
     *       them, so anything else would leave stale rows below a live one.
     * </ol>
     *
     * <p>Matching and creation take separate scopes because they are separate questions. A number can
     * serve several products, so matching has to look across all of them or a customer's existing deal
     * on a sibling product is missed and duplicated; creating a deal has to pick exactly one, and only
     * the caller knows which of the mapped products that should be.
     *
     * <p>Returns the most recently updated match, which the caller stamps onto the message as its
     * {@code TICKET_ID}. Empty only when nothing matched and creation was not asked for; the message
     * is still stored, it simply has no deal.
     */
    public Mono<Ticket> registerWhatsappMessage(
            String appCode,
            String clientCode,
            WhatsappOrigin origin,
            PhoneNumber customerPhone,
            LocalDateTime occurredAt,
            boolean createIfMissing) {

        ProcessorAccess access = ProcessorAccess.of(appCode, clientCode, Boolean.TRUE, null, null);
        LocalDateTime messageAt = occurredAt != null ? occurredAt : LocalDateTime.now(ZoneOffset.UTC);
        WhatsappOrigin from = origin == null ? WhatsappOrigin.unknown() : origin;

        return this.dao
                .readActiveByProductAndPhone(access, from.productIds(), customerPhone)
                .flatMap(matched -> {
                    if (!matched.isEmpty()) return Mono.just(matched);
                    if (!createIfMissing) return Mono.just(List.<Ticket>of());
                    return this.createFromInboundWhatsapp(
                                    access, from.createUnderProductId(), customerPhone, from.customerName())
                            .map(List::of)
                            .defaultIfEmpty(List.of());
                })
                .flatMap(tickets -> {
                    if (tickets.isEmpty()) return Mono.empty();
                    return this.dao
                            .touchLastMessageAt(
                                    tickets.stream().map(Ticket::getId).toList(), messageAt)
                            .thenReturn(tickets.getFirst());
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.registerWhatsappMessage"));
    }

    /**
     * Moves one deal up the conversation list, for a message whose deal was never in doubt.
     *
     * <p>The outbound counterpart to the fan-out in {@link #registerWhatsappMessage}. One deal rather
     * than every deal on the number, because a message somebody sent went to a particular deal, and
     * bumping its siblings would make them all look like live conversations.
     */
    public Mono<Integer> touchWhatsappConversation(ULong ticketId, LocalDateTime occurredAt) {

        if (ticketId == null) return Mono.just(0);

        return this.dao
                .touchLastMessageAt(
                        List.of(ticketId), occurredAt != null ? occurredAt : LocalDateTime.now(ZoneOffset.UTC))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.touchWhatsappConversation"));
    }

    /**
     * Everything about where an inbound WhatsApp message came from that deal resolution cares about.
     *
     * <p>One record rather than three parameters because the three are read together and are easy to
     * transpose at a call site: two of them are product ids that mean different things.
     *
     * @param productIds which products a match may be found in. Empty means the number serves
     *     everything, so the match is on the customer's number alone.
     * @param createUnderProductId the single product a brand-new deal is created in. Null falls back
     *     to the tenant's oldest active product.
     * @param customerName the name from the customer's own WhatsApp profile, or null. Names a new
     *     deal; never matched on.
     */
    public record WhatsappOrigin(List<ULong> productIds, ULong createUnderProductId, String customerName) {

        public WhatsappOrigin {
            productIds = productIds == null ? List.of() : List.copyOf(productIds);
        }

        /** Nothing resolved: match on the number alone and create wherever the fallback lands. */
        public static WhatsappOrigin unknown() {
            return new WhatsappOrigin(List.of(), null, null);
        }

        /** One product for both scopes, which is what a caller holding a single product id means. */
        public static WhatsappOrigin ofProduct(ULong productId) {
            return new WhatsappOrigin(productId == null ? List.of() : List.of(productId), productId, null);
        }
    }

    /**
     * One product for both scopes and no name.
     *
     * <p>Kept for the {@code /internal/whatsapp/register} route, whose contract is one optional
     * {@code productId} query parameter and is called from outside this service.
     */
    public Mono<Ticket> registerWhatsappMessage(
            String appCode,
            String clientCode,
            ULong productId,
            PhoneNumber customerPhone,
            LocalDateTime occurredAt,
            boolean createIfMissing) {

        return this.registerWhatsappMessage(
                appCode,
                clientCode,
                WhatsappOrigin.ofProduct(productId),
                customerPhone,
                occurredAt,
                createIfMissing);
    }

    /**
     * Creates a deal for a customer who messaged in without one.
     *
     * <p>The business number carries the routing information. Mapped to a product, the deal is
     * created there. Unmapped (the tenant default number), there is nothing to disambiguate on, so
     * it lands on the oldest active product and a sales agent moves it.
     *
     * <p>Named from the customer's own WhatsApp profile name when they have one, and from their phone
     * number otherwise. The profile name is the only thing an inbound message carries that names the
     * person, and it used to be discarded two services upstream, so every deal created this way was
     * called after a phone number and a salesperson opening their pipeline saw a column of digits.
     * Assignment, stage and owner are left to {@code checkEntity}, which runs the same distribution
     * rules as any other intake. Source is {@code WhatsApp} so these are separable from form leads.
     *
     * <p>Note this is reachable by anyone who knows the business number. Rate limiting is not built
     * yet, and the deliberate trade is that dropping the message instead would lose the highest
     * intent lead the system can receive. The name is part of that exposure and is treated as such:
     * see {@link #dealNameFrom}.
     */
    private Mono<Ticket> createFromInboundWhatsapp(
            ProcessorAccess access, ULong productId, PhoneNumber customerPhone, String customerName) {

        if (customerPhone == null || StringUtil.safeIsBlank(customerPhone.getNumber())) return Mono.empty();

        Mono<Product> product = productId != null
                ? this.productService.readById(access, productId)
                : this.productService.readFirstActive(access);

        return product.flatMap(p -> {
                    if (!p.isActive()) {
                        logger.warn(
                                "Inbound WhatsApp from {} resolved to inactive product {}, no deal created.",
                                customerPhone.getNumber(),
                                p.getId());
                        return Mono.<Ticket>empty();
                    }

                    Ticket ticket = new Ticket()
                            .setDialCode(customerPhone.getCountryCode())
                            .setPhoneNumber(customerPhone.getNumber())
                            .setSource(SOURCE_WHATSAPP)
                            .setProductId(p.getId());
                    ticket.setName(dealNameFrom(customerName, customerPhone));

                    return this.create(access, ticket)
                            .flatMap(created -> this.logInboundWhatsappCreation(access, created));
                })
                .switchIfEmpty(Mono.fromRunnable(() -> logger.warn(
                        "Inbound WhatsApp from {} has no deal and no product to create one on.",
                        customerPhone.getNumber())))
                .onErrorResume(e -> {
                    logger.error(
                            "Could not create a deal for inbound WhatsApp from {}: {}",
                            customerPhone.getNumber(),
                            e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Records the creation in the deal's activity log.
     *
     * <p>Every other intake does this - {@code create[TicketRequest]}, {@code createForCampaign} and
     * {@code createForWebsite} all call {@code acCreate} straight after {@code create} - and this path
     * was the one that did not, so a deal that arrived over WhatsApp had an activity log that began
     * mid-conversation with no record of where the lead came from. That is the first thing anybody
     * looks at when asking how a deal got here.
     *
     * <p>A failure is swallowed. The deal exists and the customer's message is about to be filed
     * against it; losing the audit line is worth a log entry, not a lost lead.
     */
    private Mono<Ticket> logInboundWhatsappCreation(ProcessorAccess access, Ticket created) {

        return this.activityService
                // The explicit-access overload, like createForWebsite and createForCampaign use. The
                // single-argument one resolves the *caller's* security context, and there is no caller
                // here: this runs on a machine-to-machine handoff from the message service, so it
                // would find nothing and the activity would never be written - silently, because the
                // failure is swallowed below.
                .acCreate(access, created, null)
                .thenReturn(created)
                .onErrorResume(e -> {
                    logger.error(
                            "Created deal {} from an inbound WhatsApp message but could not write its"
                                    + " creation activity.",
                            created.getId(),
                            e);
                    return Mono.just(created);
                });
    }

    /**
     * What to call a deal created for a stranger who messaged in.
     *
     * <p>Their WhatsApp profile name when there is one, their number otherwise. The number is not a
     * bad name so much as no name at all, and it is what every one of these deals was called.
     *
     * <p><b>The name is attacker-controlled and is handled accordingly.</b> Anyone who knows the
     * business number can reach this by sending one message, and the value is whatever they typed on
     * their own handset. So it is trimmed, bounded to the column's length, and stripped of the
     * control characters that would let a name break the line it is rendered on or a CSV export it
     * lands in. It is only ever a label: resolution stays on the phone number throughout, and nothing
     * matches on this.
     *
     * <p>Package-private so {@code InboundWhatsappDealNameTest} can exercise the sanitising directly.
     * It is the one part of this class that handles a value chosen by somebody outside the tenant, and
     * testing it through the reactive create path would need the whole service mocked to assert on a
     * string.
     */
    static String dealNameFrom(String customerName, PhoneNumber customerPhone) {

        if (StringUtil.safeIsBlank(customerName)) return customerPhone.getNumber();

        // Two passes, and the second is not tidiness. Stripping turns each control character into a
        // space, so "Vishwas\r\nKumar" would otherwise keep a double space, and a name padded with
        // twenty of them would read as two columns in a list.
        String cleaned = customerName
                .replaceAll("[\\p{Cntrl}\\p{Cf}]", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();

        // A name that was nothing but control characters. Falling back is right: an empty deal name
        // is unusable in a list and the number at least identifies the lead.
        if (cleaned.isEmpty()) return customerPhone.getNumber();

        return cleaned.length() > MAX_INBOUND_NAME_LENGTH
                ? cleaned.substring(0, MAX_INBOUND_NAME_LENGTH)
                : cleaned;
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

    public Mono<Integer> updateTicketDncByClientId(ULong clientId, Boolean dnc) {
        return this.dao
                .updateDncByClientId(clientId, dnc)
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

    /**
     * Records the number this deal is messaged on.
     *
     * <p>Its own operation rather than part of the general ticket update, which replaces only the
     * handful of fields it names and would either ignore this one or clear it on every save from a
     * client that has not been taught about it.
     *
     * <p>A null or blank number clears the override and puts the deal back on its phone number.
     * Logged as a field update either way, because this value comes from a phone call rather than
     * from the lead's own submission, and when messages later go nowhere the first useful question is
     * who typed it and when.
     */
    public Mono<Ticket> updateWhatsappNumber(Identity ticketId, TicketWhatsappNumberRequest request) {

        if (request == null) return this.identityMissingError(Ticket.Fields.whatsappNumber);

        PhoneNumber number = request.getWhatsappNumber();
        boolean clearing = number == null || StringUtil.safeIsBlank(number.getNumber());

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.readByIdentity(access, ticketId),
                        (access, ticket) -> {
                            String old = ticket.getWhatsappNumber();

                            ticket.setWhatsappNumber(clearing ? null : number.getNumber());
                            ticket.setWhatsappDialCode(clearing ? null : number.getCountryCode());

                            // Straight to the two columns, not through update(): updatableEntity
                            // re-reads the row and copies only the fields it names onto it, and these
                            // two are deliberately not among them for the reason above. Sent through
                            // the general path they are silently dropped and the call answers 200
                            // with the old number.
                            ULong contextUserId = access.getUserId();

                            return this.dao.updateWhatsappNumber(
                                            ticket.getId(),
                                            ticket.getWhatsappDialCode(),
                                            ticket.getWhatsappNumber(),
                                            contextUserId != null && contextUserId.longValue() != 0L
                                                    ? contextUserId
                                                    : null)
                                    .then(this.activityService.acFieldUpdate(
                                            ticket.getId(),
                                            request.getComment(),
                                            Ticket.Fields.whatsappNumber + ": "
                                                    + (old == null ? "-" : old) + " -> "
                                                    + (ticket.getWhatsappNumber() == null
                                                            ? "-"
                                                            : ticket.getWhatsappNumber())))
                                    .thenReturn(ticket);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateWhatsappNumber"));
    }

    public Mono<Ticket> updateTag(Identity ticketId, TicketTagRequest ticketTagRequest) {

        if (ticketTagRequest == null || ticketTagRequest.getTag() == null)
            return this.identityMissingError(Ticket.Fields.tag);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.readByIdentity(access, ticketId),
                        (access, ticket) -> Mono.just(ticketTagRequest.getTag()),
                        (access, ticket, resolvedTag) -> {
                            String oldTagEnum = ticket.getTag();
                            ticket.setTag(resolvedTag);

                            return FlatMapUtil.flatMapMono(
                                    () -> this.computeAndSetExpiresOn(access, ticket),
                                    eTicket -> this.update(access, eTicket),
                                    (eTicket, uTicket) -> ticketTagRequest.getTaskRequest() != null
                                            ? this.createTask(access, ticketTagRequest.getTaskRequest(), uTicket)
                                            : Mono.just(Boolean.FALSE),
                                    (eTicket, uTicket, cTask) -> this.activityService
                                            .acTagChange(access, uTicket, ticketTagRequest.getComment(), oldTagEnum)
                                            .thenReturn(uTicket));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateTag"));
    }

    @Override
    protected Mono<Integer> deleteInternal(ProcessorAccess access, Ticket ticket) {
        return FlatMapUtil.flatMapMono(
                        () -> this.checkDeleteAccess(access, ticket),
                        checked -> super.deleteInternal(access, ticket))
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

    private Mono<Ticket> computeAndSetExpiresOn(ProcessorAccess access, Ticket ticket) {

        if (ticket.getSource() == null || ticket.getProductId() == null) return Mono.just(ticket);

        return this.productTicketExRuleService
                .computeExpiresOn(access, ticket.getProductId(), ticket.getSource())
                .map(ticket::setExpiresOn)
                .defaultIfEmpty(ticket)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.computeAndSetExpiresOn"));
    }

    public Mono<Void> resetExpiresOn(ProcessorAccess access, Ticket ticket) {

        return this.computeAndSetExpiresOn(access, ticket)
                .flatMap(eTicket -> super.updateInternal(access, eTicket))
                .then()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.resetExpiresOn(ticket)"));
    }

    public Mono<Void> resetExpiresOn(ProcessorAccess access, ULong ticketId) {

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> super.readById(access, ticketId),
                        ticket -> {
                            if (ticket == null) return Mono.empty();

                            if (ticket.isExpired()
                                    && (access.getUser() == null
                                            || !SecurityContextUtil
                                                    .hasAuthority(
                                                            BusinessPartnerConstant.OWNER_ROLE,
                                                            access.getUser().getAuthorities())))
                                return this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        ProcessorMessageResourceService.TICKET_EXPIRED);

                            return this.computeAndSetExpiresOn(access, ticket);
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

    /**
     * Records a customer's WhatsApp avatar on every deal that shares their number.
     *
     * <p>No access check, and that is correct rather than an omission: the caller is the inbound
     * handoff from the message service, which runs with no user at all. Nothing here is readable by
     * a caller who could not already read the deal, and the write is confined to two columns that
     * hold a picture.
     */
    public Mono<Integer> updateWhatsappProfilePicture(
            String appCode, String clientCode, String phoneNumber, FileDetail detail, String pictureId) {
        return this.dao
                .updateWhatsappProfilePicture(appCode, clientCode, phoneNumber, detail, pictureId)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketService.updateWhatsappProfilePicture"));
    }
}
