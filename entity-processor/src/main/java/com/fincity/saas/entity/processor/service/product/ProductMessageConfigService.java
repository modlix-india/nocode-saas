package com.fincity.saas.entity.processor.service.product;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.functions.AbstractServiceFunction;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.repository.ListFunctionRepository;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.product.ProductMessageConfigDAO;
import com.fincity.saas.entity.processor.dto.product.ProductMessageConfig;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.MessageChannelType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductMessageConfigsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.util.EntityProcessorArgSpec;
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

        String dtoSchemaRef = classSchema.getNamespaceForClass(ProductMessageConfig.class)
                + "."
                + ProductMessageConfig.class.getSimpleName();

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "GetConfigs",
                EntityProcessorArgSpec.identity("productId"),
                EntityProcessorArgSpec.identity("stageId"),
                EntityProcessorArgSpec.identity("statusId"),
                ClassSchema.ArgSpec.ofRef("channel", MessageChannelType.class, classSchema),
                "result",
                Schema.ofArray("result", Schema.ofRef(dtoSchemaRef)),
                gson,
                (productId, stageId, statusId, channel) -> self.getConfigs(productId, stageId, statusId, channel)));
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

    public Mono<List<ProductMessageConfig>> getConfigs(
            ProcessorAccess access, ULong productId, ULong stageId, ULong statusId, MessageChannelType channel) {

        return this.dao
                .getConfigs(access, productId, stageId, statusId, channel)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductMessageConfigService.getConfigs"));
    }

    public Mono<List<ProductMessageConfig>> getConfigs(
            Identity productId, Identity stageId, Identity statusId, MessageChannelType channel) {

        return this.hasAccess()
                .flatMap(access -> this.getConfigs(
                        access,
                        productId.getULongId(),
                        stageId.getULongId(),
                        statusId != null && !statusId.isNull() ? statusId.getULongId() : null,
                        channel))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductMessageConfigService.getConfigs[Identity]"));
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
