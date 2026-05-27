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
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.ConversionActionMappingDAO;
import com.fincity.saas.entity.processor.dao.StageDAO;
import com.fincity.saas.entity.processor.dto.ConversionActionMapping;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.enums.FunnelStage;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorConversionActionMappingRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredConversionAction;
import com.fincity.saas.entity.processor.model.request.ApplyFunnelMappingRequest;
import com.fincity.saas.entity.processor.model.request.ConversionActionMappingRequest;
import com.fincity.saas.entity.processor.model.request.CreateGoogleConversionActionRequest;
import com.fincity.saas.entity.processor.model.request.SeedConversionDefaultsRequest;
import com.fincity.saas.entity.processor.platform.GooglePlatformService;
import com.fincity.saas.entity.processor.service.commons.AbstractConnectionService;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ConversionActionMappingService
        extends BaseUpdatableService<
                EntityProcessorConversionActionMappingRecord,
                ConversionActionMapping,
                ConversionActionMappingDAO>
        implements IRepositoryProvider {

    private static final String NAMESPACE = "EntityProcessor.ConversionActionMapping";

    private final List<ReactiveFunction> functions = new ArrayList<>();

    private final StageDAO stageDAO;
    private final Gson gson;
    private final CampaignService campaignService;
    private final AbstractConnectionService connectionService;
    private final GooglePlatformService googlePlatformService;

    private static final ClassSchema classSchema =
            ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());

    @Autowired
    @Lazy
    private ConversionActionMappingService self;

    public ConversionActionMappingService(
            StageDAO stageDAO,
            Gson gson,
            CampaignService campaignService,
            AbstractConnectionService connectionService,
            GooglePlatformService googlePlatformService) {
        this.stageDAO = stageDAO;
        this.gson = gson;
        this.campaignService = campaignService;
        this.connectionService = connectionService;
        this.googlePlatformService = googlePlatformService;
    }

    @PostConstruct
    private void init() {

        this.functions.addAll(
                super.getCommonFunctions(NAMESPACE, ConversionActionMapping.class, classSchema, gson));

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "CreateRequest",
                ClassSchema.ArgSpec.ofRef("request", ConversionActionMappingRequest.class, classSchema),
                "created",
                Schema.ofRef("EntityProcessor.DTO.ConversionActionMapping"),
                gson,
                self::createRequest));
    }

    @Override
    protected boolean canOutsideCreate() {
        return false;
    }

    public Mono<ConversionActionMapping> createRequest(ConversionActionMappingRequest request) {

        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.validateAndBuild(access, request),
                        (access, mapping) -> super.createInternal(access, mapping))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "ConversionActionMappingService.createRequest[Request]"));
    }

    private Mono<ConversionActionMapping> validateAndBuild(
            ProcessorAccess access, ConversionActionMappingRequest request) {

        if (request.getTriggerStageId() == null || request.getTriggerStageId().getULongId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.MISSING_PARAMETERS,
                    "triggerStageId");

        if (request.getCampaignPlatform() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.MISSING_PARAMETERS,
                    "campaignPlatform");

        ConversionActionMapping mapping = ConversionActionMapping.of(request)
                .setTriggerStageId(request.getTriggerStageId().getULongId());

        if (request.getTriggerStatusId() != null) {
            mapping.setTriggerStatusId(request.getTriggerStatusId().getULongId());
        }

        if (request.getProductTemplateId() != null) {
            mapping.setProductTemplateId(request.getProductTemplateId().getULongId());
        }

        return Mono.just(mapping);
    }

    @Override
    protected Mono<ConversionActionMapping> updatableEntity(ConversionActionMapping incoming) {
        return super.updatableEntity(incoming)
                .flatMap(existing -> {
                    existing.setEventName(incoming.getEventName());
                    existing.setPlatformActionId(incoming.getPlatformActionId());
                    existing.setDefaultValue(incoming.getDefaultValue());
                    existing.setCurrency(incoming.getCurrency());
                    existing.setValueFieldPath(incoming.getValueFieldPath());
                    existing.setTestEventCode(incoming.getTestEventCode());
                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ConversionActionMappingService.updatableEntity"));
    }

    public reactor.core.publisher.Flux<ConversionActionMapping> findActiveByTrigger(
            ProcessorAccess access, ULong stageId, ULong statusId, ULong productTemplateId) {
        return this.dao.findActiveByTrigger(access, stageId, statusId, productTemplateId)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ConversionActionMappingService.findActiveByTrigger"));
    }

    /**
     * Walks every active stage under a product template that has a non-null
     * funnelStage tag and creates a mapping row from the supplied defaults. Skips
     * stages whose tag has no entry in {@code request.defaults} and skips
     * (stage, platform) pairs that already have an active mapping.
     *
     * @return the list of newly-created mappings (existing ones are not returned).
     */
    public Mono<List<ConversionActionMapping>> seedDefaults(SeedConversionDefaultsRequest request) {

        if (request.getProductTemplateId() == null
                || request.getProductTemplateId().getULongId() == null
                || request.getCampaignPlatform() == null
                || request.getDefaults() == null
                || request.getDefaults().isEmpty()) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.MISSING_PARAMETERS,
                    "productTemplateId, campaignPlatform, defaults");
        }

        ULong productTemplateId = request.getProductTemplateId().getULongId();
        Map<FunnelStage, SeedConversionDefaultsRequest.FunnelStageDefault> defaults = request.getDefaults();

        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.stageDAO
                                .findTaggedForProductTemplate(access, productTemplateId)
                                .filter(stage -> defaults.containsKey(stage.getFunnelStage()))
                                .concatMap(stage -> this.seedOne(access, stage, request, defaults.get(stage.getFunnelStage())))
                                .collectList())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ConversionActionMappingService.seedDefaults"));
    }

    private Mono<ConversionActionMapping> seedOne(
            ProcessorAccess access,
            Stage stage,
            SeedConversionDefaultsRequest request,
            SeedConversionDefaultsRequest.FunnelStageDefault def) {

        ULong productTemplateId = request.getProductTemplateId().getULongId();
        ULong triggerStageId = Boolean.TRUE.equals(stage.getIsParent()) ? stage.getId() : stage.getParentLevel0();
        ULong triggerStatusId = Boolean.TRUE.equals(stage.getIsParent()) ? null : stage.getId();

        return this.dao.findExisting(
                        access, productTemplateId, request.getCampaignPlatform(), triggerStageId, triggerStatusId)
                .flatMap(existing -> Mono.<ConversionActionMapping>empty())
                .switchIfEmpty(Mono.defer(() -> {
                    ConversionActionMapping mapping = new ConversionActionMapping()
                            .setProductTemplateId(productTemplateId)
                            .setCampaignPlatform(request.getCampaignPlatform())
                            .setTriggerStageId(triggerStageId)
                            .setTriggerStatusId(triggerStatusId)
                            .setEventName(def.getEventName())
                            .setPlatformActionId(def.getPlatformActionId())
                            .setDefaultValue(def.getDefaultValue())
                            .setCurrency(def.getCurrency())
                            .setValueFieldPath(def.getValueFieldPath())
                            .setTestEventCode(def.getTestEventCode());
                    return super.createInternal(access, mapping);
                }));
    }

    /**
     * Self-contained funnel apply: for each funnel stage in the request, tags every
     * chosen deal-stage with that funnel stage ({@code FUNNEL_STAGE}) and upserts a
     * stage-level conversion mapping (triggerStatusId null = any status in stage) for
     * the platform. Additive: stages/mappings not in the request are left untouched
     * (remove via the per-stage editor). Returns {@code {mappings: N}} upsert count.
     */
    public Mono<Map<String, Object>> applyFunnel(ApplyFunnelMappingRequest request) {

        if (request.getProductTemplateId() == null
                || request.getProductTemplateId().getULongId() == null
                || request.getCampaignPlatform() == null
                || request.getFunnels() == null
                || request.getFunnels().isEmpty()) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.MISSING_PARAMETERS,
                    "productTemplateId, campaignPlatform, funnels");
        }

        ULong productTemplateId = request.getProductTemplateId().getULongId();
        CampaignPlatform platform = request.getCampaignPlatform();
        AtomicInteger count = new AtomicInteger();

        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> Flux.fromIterable(request.getFunnels().entrySet())
                                .concatMap(entry -> {
                                    ApplyFunnelMappingRequest.FunnelMapping fm = entry.getValue();
                                    if (fm == null || fm.getStageIds() == null || fm.getStageIds().isEmpty())
                                        return Flux.empty();
                                    return Flux.fromIterable(fm.getStageIds())
                                            .filter(id -> id != null && id.getULongId() != null)
                                            .concatMap(id -> this.applyOneFunnelStage(
                                                    access, productTemplateId, platform, entry.getKey(),
                                                    id.getULongId(), fm))
                                            .doOnNext(m -> count.incrementAndGet());
                                })
                                .then(Mono.fromSupplier(() -> Map.<String, Object>of("mappings", count.get()))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ConversionActionMappingService.applyFunnel"));
    }

    private Mono<ConversionActionMapping> applyOneFunnelStage(
            ProcessorAccess access,
            ULong productTemplateId,
            CampaignPlatform platform,
            FunnelStage funnel,
            ULong stageId,
            ApplyFunnelMappingRequest.FunnelMapping fm) {

        return this.stageDAO
                .setFunnelStage(access, stageId, funnel)
                .then(this.dao
                        .findExisting(access, productTemplateId, platform, stageId, null)
                        .flatMap(existing -> super.update(access, existing
                                .setEventName(fm.getEventName())
                                .setPlatformActionId(fm.getPlatformActionId())
                                .setDefaultValue(fm.getDefaultValue())
                                .setCurrency(fm.getCurrency())
                                .setValueFieldPath(fm.getValueFieldPath())
                                .setTestEventCode(fm.getTestEventCode())))
                        .switchIfEmpty(Mono.defer(() -> super.createInternal(access, new ConversionActionMapping()
                                .setProductTemplateId(productTemplateId)
                                .setCampaignPlatform(platform)
                                .setTriggerStageId(stageId)
                                .setEventName(fm.getEventName())
                                .setPlatformActionId(fm.getPlatformActionId())
                                .setDefaultValue(fm.getDefaultValue())
                                .setCurrency(fm.getCurrency())
                                .setValueFieldPath(fm.getValueFieldPath())
                                .setTestEventCode(fm.getTestEventCode())))));
    }

    /**
     * Lists the Google Ads conversion actions in the client's connected account
     * for the mapping picker. Account is resolved from any of the client's Google
     * campaigns unless {@code customerId} is supplied. Returns an empty list when
     * no Google account can be resolved (fresh client with no synced campaign).
     */
    public Mono<List<DiscoveredConversionAction>> listGoogleConversionActions(
            String customerId, String loginCustomerId) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> resolveGoogleAccount(customerId, loginCustomerId),
                        (access, acct) -> this.connectionService.getMarketingPlatformOAuth2Token(
                                access.getClientCode(), this.googlePlatformService.getConnectionName()),
                        (access, acct, token) -> this.googlePlatformService
                                .fetchConversionActions(
                                        access.getAppCode(),
                                        access.getClientCode(),
                                        acct.customerId(),
                                        acct.loginCustomerId(),
                                        token)
                                .collectList())
                .switchIfEmpty(Mono.just(List.of()))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME, "ConversionActionMappingService.listGoogleConversionActions"));
    }

    /**
     * Creates a Google Ads conversion action in the client's connected account so
     * a fresh client can provision one without leaving Modlix. The returned
     * {@code resourceName} is ready to drop into a mapping's {@code platformActionId}.
     */
    public Mono<DiscoveredConversionAction> createGoogleConversionAction(CreateGoogleConversionActionRequest request) {

        if (request.getName() == null || request.getName().isBlank())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.MISSING_PARAMETERS,
                    "name");

        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> resolveGoogleAccount(request.getCustomerId(), request.getLoginCustomerId())
                                .switchIfEmpty(Mono.error(new GenericException(
                                        HttpStatus.BAD_REQUEST,
                                        "No connected Google Ads account found for this client. Connect Google and"
                                                + " sync a campaign first, or pass customerId explicitly."))),
                        (access, acct) -> this.connectionService.getMarketingPlatformOAuth2Token(
                                access.getClientCode(), this.googlePlatformService.getConnectionName()),
                        (access, acct, token) -> this.googlePlatformService.createConversionAction(
                                access.getAppCode(),
                                access.getClientCode(),
                                acct.customerId(),
                                acct.loginCustomerId(),
                                request.getName(),
                                request.getCategory(),
                                token))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME, "ConversionActionMappingService.createGoogleConversionAction"));
    }

    /** Explicit ids when supplied; else the client's first Google campaign with a resolved account. */
    private Mono<GoogleAccount> resolveGoogleAccount(String customerId, String loginCustomerId) {
        if (customerId != null && !customerId.isBlank())
            return Mono.just(new GoogleAccount(customerId, loginCustomerId));
        return this.campaignService
                .findPlatformAccount(CampaignPlatform.GOOGLE)
                .map(c -> new GoogleAccount(c.getPlatformAccountId(), c.getPlatformLoginId()));
    }

    private record GoogleAccount(String customerId, String loginCustomerId) {}

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {
        return this.defaultSchemaRepositoryFor(ConversionActionMapping.class, classSchema);
    }
}
