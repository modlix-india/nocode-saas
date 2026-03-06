package com.fincity.saas.entity.processor.service.product;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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
import com.fincity.saas.entity.processor.dao.product.ProductMessageConfigDAO;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.dto.product.ProductMessageConfig;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.MessageChannelType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductMessageConfigsRecord;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.product.ProductMessageConfigRequest;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.StageService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.google.gson.Gson;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ProductMessageConfigService
        extends BaseUpdatableService<
                EntityProcessorProductMessageConfigsRecord, ProductMessageConfig, ProductMessageConfigDAO>
        implements IRepositoryProvider {

    private static final String CACHE_NAME = "productMessageConfig";
    private static final String NAMESPACE = "EntityProcessor.ProductMessageConfig";

    private static final ClassSchema classSchema =
            ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());

    private final List<ReactiveFunction> functions = new ArrayList<>();

    private final Gson gson;
    private final ProductService productService;
    private final StageService stageService;

    @Autowired
    @Lazy
    private ProductMessageConfigService self;

    public ProductMessageConfigService(ProductService productService, StageService stageService, Gson gson) {
        this.productService = productService;
        this.stageService = stageService;
        this.gson = gson;
    }

    @PostConstruct
    private void init() {

        this.functions.addAll(super.getCommonFunctions(NAMESPACE, ProductMessageConfig.class, classSchema, gson));

        String dtoSchemaRef = classSchema.getNamespaceForClass(ProductMessageConfig.class)
                + "."
                + ProductMessageConfig.class.getSimpleName();

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "CreateRequest",
                ClassSchema.ArgSpec.ofRef("request", ProductMessageConfigRequest.class, classSchema),
                "result",
                Schema.ofArray("result", Schema.ofRef(dtoSchemaRef)),
                gson,
                self::createRequest));
    }

    @Override
    protected String getCacheName() {
        return CACHE_NAME;
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_MESSAGE_CONFIGS;
    }

    @Override
    protected Mono<Boolean> evictCache(ProductMessageConfig entity) {
        return Mono.zip(super.evictCache(entity), this.evictGroupCache(entity))
                .map(tuple -> tuple.getT1() && tuple.getT2());
    }

    private Mono<Boolean> evictGroupCache(ProductMessageConfig cfg) {
        return this.cacheService.evict(
                this.getCacheName(),
                this.getConfigsCacheKey(
                        cfg.getAppCode(),
                        cfg.getClientCode(),
                        cfg.getProductId(),
                        cfg.getStageId(),
                        cfg.getStatusId(),
                        cfg.getChannel()));
    }

    private String getConfigsCacheKey(
            String appCode,
            String clientCode,
            ULong productId,
            ULong stageId,
            ULong statusId,
            MessageChannelType channel) {
        return super.getCacheKey(
                appCode, clientCode, productId, stageId, statusId, channel != null ? channel.getLiteral() : null);
    }

    @Override
    protected Mono<ProductMessageConfig> checkEntity(ProductMessageConfig entity, ProcessorAccess access) {

        if (entity.getProductId() == null) return this.throwMissingParam(ProductMessageConfig.Fields.productId);

        if (entity.getStageId() == null) return this.throwMissingParam(ProductMessageConfig.Fields.stageId);

        if (entity.getChannel() == null) return this.throwMissingParam(ProductMessageConfig.Fields.channel);

        if (entity.getOrder() == null || entity.getMessageTemplateId() == null) return Mono.just(entity);

        return this.validateNoDuplicateConfigs(
                        access,
                        entity.getProductId(),
                        entity.getStageId(),
                        entity.getStatusId(),
                        entity.getChannel(),
                        List.of(entity.getMessageTemplateId()),
                        entity.getOrder())
                .thenReturn(entity)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductMessageConfigService.checkEntity"));
    }

    @Override
    protected Mono<ProductMessageConfig> create(ProcessorAccess access, ProductMessageConfig entity) {
        return super.create(access, entity)
                .flatMap(created -> this.evictCache(created).thenReturn(created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductMessageConfigService.create"));
    }

    public Mono<List<ProductMessageConfig>> updateOrderForGroup(List<IdAndValue<ULong, Integer>> entries) {

        if (entries == null || entries.isEmpty()) return this.throwMissingParam("configs");

        Set<ULong> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();

        for (IdAndValue<ULong, Integer> entry : entries) {
            if (entry.getId() == null || entry.getValue() == null)
                return this.throwMissingParam("configs.id/configs.value");

            if (!ids.add(entry.getId()) || !orders.add(entry.getValue()))
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.DUPLICATE_PRODUCT_MESSAGE_CONFIG);
        }

        return this.updateOrderForGroupInternal(entries);
    }

    private Mono<List<ProductMessageConfig>> updateOrderForGroupInternal(
            List<IdAndValue<ULong, Integer>> validEntries) {

        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.readById(access, validEntries.getFirst().getId()),
                        (access, firstConfig) -> this.getConfigs(
                                access,
                                firstConfig.getProductId(),
                                firstConfig.getStageId(),
                                firstConfig.getStatusId(),
                                firstConfig.getChannel()),
                        (access, firstConfig, groupConfigs) ->
                                this.applyOrderChanges(access, firstConfig, groupConfigs, validEntries))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductMessageConfigService.updateOrderForGroup"));
    }

    public Mono<List<ProductMessageConfig>> createRequest(ProductMessageConfigRequest request) {

        if (request == null || !request.isValid()) return this.throwMissingParam("ProductMessageConfigRequest");

        Set<ULong> reqTemplates = new HashSet<>(request.getTemplateIds());
        if (reqTemplates.size() != request.getTemplateIds().size())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.DUPLICATE_PRODUCT_MESSAGE_CONFIG);

        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.productService.readByIdentity(access, request.getProductId()),
                        (access, product) -> this.stageService
                                .getParentChild(
                                        access,
                                        product.getProductTemplateId(),
                                        request.getStageId(),
                                        request.getStatusId())
                                .switchIfEmpty(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.STAGE_MISSING)),
                        (access, product, stageStatusEntry) -> {
                            Stage parentStage = stageStatusEntry.getKey();
                            List<Stage> children = stageStatusEntry.getValue();
                            ULong statusId = (children != null && !children.isEmpty())
                                    ? children.getFirst().getId()
                                    : null;

                            int baseOrder = request.getStartingOrder() != null ? request.getStartingOrder() : 0;

                            return this.validateNoDuplicateConfigs(
                                            access,
                                            product.getId(),
                                            parentStage.getId(),
                                            statusId,
                                            request.getChannel(),
                                            request.getTemplateIds(),
                                            baseOrder)
                                    .then(Flux.fromIterable(request.getTemplateIds())
                                            .index()
                                            .flatMap(tuple -> {
                                                long idx = tuple.getT1();
                                                ULong templateId = tuple.getT2();

                                                ProductMessageConfig entity = new ProductMessageConfig()
                                                        .setProductId(product.getId())
                                                        .setStageId(parentStage.getId())
                                                        .setStatusId(statusId)
                                                        .setChannel(request.getChannel())
                                                        .setOrder(baseOrder + (int) idx)
                                                        .setMessageTemplateId(templateId);

                                                return super.create(access, entity);
                                            })
                                            .collectList());
                        },
                        (access, product, stageStatusEntry, productMessageConfigs) -> productMessageConfigs.isEmpty()
                                ? Mono.just(productMessageConfigs)
                                : this.evictGroupCache(productMessageConfigs.getFirst())
                                        .thenReturn(productMessageConfigs))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductMessageConfigService.createRequest"));
    }

    public Mono<Integer> deleteGroup(ULong id) {

        if (id == null) return this.identityMissingError();

        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.readById(access, id),
                        (access, productMessageConfig) -> this.getConfigs(
                                access,
                                productMessageConfig.getProductId(),
                                productMessageConfig.getStageId(),
                                productMessageConfig.getStatusId(),
                                productMessageConfig.getChannel()),
                        (access, productMessageConfig, configGroup) -> {
                            if (configGroup == null || configGroup.isEmpty()) return Mono.just(0);

                            return this.deleteMultiple(configGroup)
                                    .flatMap(deleted -> this.evictGroupCache(productMessageConfig)
                                            .thenReturn(deleted));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductMessageConfigService.deleteGroup"));
    }

    private Mono<Void> validateNoDuplicateConfigs(
            ProcessorAccess access,
            ULong productId,
            ULong stageId,
            ULong statusId,
            MessageChannelType channel,
            List<ULong> templateIds,
            int startingOrder) {

        return this.getConfigs(access, productId, stageId, statusId, channel)
                .flatMap(existing -> {
                    Set<ULong> existingTemplates = existing.stream()
                            .map(ProductMessageConfig::getMessageTemplateId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());

                    boolean templateClash = templateIds.stream().anyMatch(existingTemplates::contains);

                    Set<Integer> existingOrders = existing.stream()
                            .map(ProductMessageConfig::getOrder)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());

                    boolean orderClash = false;
                    for (int i = 0; i < templateIds.size(); i++) {
                        int candidateOrder = startingOrder + i;
                        if (existingOrders.contains(candidateOrder)) {
                            orderClash = true;
                            break;
                        }
                    }

                    if (templateClash || orderClash) {
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.DUPLICATE_PRODUCT_MESSAGE_CONFIG);
                    }

                    return Mono.<Void>empty();
                })
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "ProductMessageConfigService.validateNoDuplicateConfigs"));
    }

    private Mono<List<ProductMessageConfig>> applyOrderChanges(
            ProcessorAccess access,
            ProductMessageConfig firstConfig,
            List<ProductMessageConfig> groupConfigs,
            List<IdAndValue<ULong, Integer>> validEntries) {

        if (groupConfigs.isEmpty()) return Mono.just(List.of());

        Set<ULong> existingIds =
                groupConfigs.stream().map(ProductMessageConfig::getId).collect(Collectors.toSet());

        Set<ULong> requestedIds = validEntries.stream().map(IdAndValue::getId).collect(Collectors.toSet());

        if (!existingIds.containsAll(requestedIds)) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.INVALID_PARAMETERS,
                    "id",
                    "ProductMessageConfigOrderRequest");
        }

        Map<ULong, Integer> orderMap = validEntries.stream()
                .collect(Collectors.toMap(IdAndValue<ULong, Integer>::getId, IdAndValue::getValue));

        List<ProductMessageConfig> toUpdate = new ArrayList<>();
        List<ProductMessageConfig> toDelete = new ArrayList<>();

        for (ProductMessageConfig cfg : groupConfigs) {
            Integer newOrder = orderMap.get(cfg.getId());
            if (newOrder != null) {
                cfg.setOrder(newOrder);
                toUpdate.add(cfg);
            } else {
                toDelete.add(cfg);
            }
        }

        Mono<Integer> deleteMono = toDelete.isEmpty() ? Mono.just(0) : this.deleteMultiple(toDelete);

        Mono<List<ProductMessageConfig>> updateMono = toUpdate.isEmpty()
                ? Mono.just(List.of())
                : Flux.fromIterable(toUpdate)
                        .flatMap(cfg -> this.updateInternal(access, cfg))
                        .collectList();

        return deleteMono.then(updateMono).flatMap(updated -> {
            List<ProductMessageConfig> finalList = groupConfigs.stream()
                    .filter(cfg -> orderMap.containsKey(cfg.getId()))
                    .toList();

            if (finalList.isEmpty()) return Mono.just(finalList);

            return this.evictGroupCache(firstConfig).thenReturn(finalList);
        });
    }

    public Mono<List<ProductMessageConfig>> getConfigs(
            ProcessorAccess access, ULong productId, ULong stageId, ULong statusId, MessageChannelType channel) {

        return this.cacheService
                .cacheValueOrGet(
                        this.getCacheName(),
                        () -> this.getConfigsInternal(access, productId, stageId, statusId, channel)
                                .contextWrite(
                                        Context.of(LogUtil.METHOD_NAME, "ProductMessageConfigService.getConfigs")),
                        this.getConfigsCacheKey(
                                access.getAppCode(), access.getClientCode(), productId, stageId, statusId, channel))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductMessageConfigService.getConfigs"));
    }

    private Mono<List<ProductMessageConfig>> getConfigsInternal(
            ProcessorAccess access, ULong productId, ULong stageId, ULong statusId, MessageChannelType channel) {

        return this.dao
                .getConfigs(access, productId, stageId, statusId, channel)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductMessageConfigService.getConfigsInternal"));
    }

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {
        return this.defaultSchemaRepositoryFor(ProductMessageConfig.class, classSchema);
    }
}
