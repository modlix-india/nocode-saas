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
import com.fincity.saas.entity.processor.enums.FunnelStage;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorConversionActionMappingRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.ConversionActionMappingRequest;
import com.fincity.saas.entity.processor.model.request.SeedConversionDefaultsRequest;
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

    private static final ClassSchema classSchema =
            ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());

    @Autowired
    @Lazy
    private ConversionActionMappingService self;

    public ConversionActionMappingService(StageDAO stageDAO, Gson gson) {
        this.stageDAO = stageDAO;
        this.gson = gson;
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
