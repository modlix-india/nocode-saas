package com.fincity.saas.entity.processor.service.product;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.repository.ListFunctionRepository;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.product.ProductMessageConfigDAO;
import com.fincity.saas.entity.processor.dto.product.ProductMessageConfig;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.MessageChannelType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductMessageConfigsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
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

    @Autowired
    @Lazy
    private ProductMessageConfigService self;

    public ProductMessageConfigService(Gson gson) {
        this.gson = gson;
    }

    @PostConstruct
    private void init() {

        this.functions.addAll(super.getCommonFunctions(NAMESPACE, ProductMessageConfig.class, classSchema, gson));
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
    protected Mono<ProductMessageConfig> create(ProcessorAccess access, ProductMessageConfig entity) {
        return super.create(access, entity)
                .flatMap(created -> this.evictCache(created).thenReturn(created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductMessageConfigService.create"));
    }

    @Override
    protected Mono<Boolean> evictCache(ProductMessageConfig entity) {
        return Mono.zip(
                        super.evictCache(entity),
                        this.cacheService.evict(
                                this.getCacheName(),
                                this.getConfigsCacheKey(
                                        entity.getAppCode(),
                                        entity.getClientCode(),
                                        entity.getProductId(),
                                        entity.getStageId(),
                                        entity.getStatusId(),
                                        entity.getChannel())))
                .map(tuple -> tuple.getT1() && tuple.getT2());
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

    public Mono<List<ProductMessageConfig>> getConfigs(
            ProcessorAccess access, ULong productId, ULong stageId, ULong statusId, MessageChannelType channel) {

        return this.cacheService
                .cacheValueOrGet(
                        this.getCacheName(),
                        () -> this.dao
                                .getConfigs(access, productId, stageId, statusId, channel)
                                .contextWrite(
                                        Context.of(LogUtil.METHOD_NAME, "ProductMessageConfigService.getConfigs")),
                        this.getConfigsCacheKey(
                                access.getAppCode(), access.getClientCode(), productId, stageId, statusId, channel))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductMessageConfigService.getConfigs"));
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
