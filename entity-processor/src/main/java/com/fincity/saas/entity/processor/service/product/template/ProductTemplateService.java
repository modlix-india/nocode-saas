package com.fincity.saas.entity.processor.service.product.template;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.functions.AbstractProcessorFunction;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.repository.ListFunctionRepository;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.product.template.ProductTemplateDAO;
import com.fincity.saas.entity.processor.dto.form.ProductTemplateWalkInForm;
import com.fincity.saas.entity.processor.dto.product.ProductComm;
import com.fincity.saas.entity.processor.dto.product.ProductTemplate;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.ProductTemplateType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplatesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.product.template.ProductTemplateRequest;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import com.fincity.saas.entity.processor.util.EntityProcessorArgSpec;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ProductTemplateService
        extends BaseUpdatableService<EntityProcessorProductTemplatesRecord, ProductTemplate, ProductTemplateDAO>
        implements IRepositoryProvider {

    private static final String PRODUCT_TEMPLATE = "productTemplate";
    private final List<ReactiveFunction> functions = new ArrayList<>();
    private final Gson gson;

    private final ClassSchema classSchema = ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());

    private ProductService productService;

    @Autowired
    @Lazy
    private ProductTemplateService self;

    public ProductTemplateService(Gson gson) {
        this.gson = gson;
    }

    @Lazy
    @Autowired
    private void setProductService(ProductService productService) {
        this.productService = productService;
    }

    @PostConstruct
    private void init() {

        this.functions.addAll(super.getCommonFunctions("ProductTemplate", ProductTemplate.class, gson));

        String dtoSchemaRef =
                classSchema.getNamespaceForClass(ProductTemplate.class) + "." + ProductTemplate.class.getSimpleName();

        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "ProductTemplate",
                "CreateRequest",
                ClassSchema.ArgSpec.ofRef("request", ProductTemplateRequest.class),
                "created",
                Schema.ofRef(dtoSchemaRef),
                gson,
                self::createRequest));

        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "ProductTemplate",
                "AttachEntity",
                EntityProcessorArgSpec.identity("identity"),
                ClassSchema.ArgSpec.ofRef("request", ProductTemplateRequest.class),
                "result",
                Schema.ofRef(dtoSchemaRef),
                gson,
                self::attachEntity));
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TEMPLATE;
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    @Override
    protected Mono<ProductTemplate> checkEntity(ProductTemplate entity, ProcessorAccess access) {
        return super.checkExistsByName(access, entity);
    }

    @Override
    protected Mono<ProductTemplate> updatableEntity(ProductTemplate entity) {
        return super.updatableEntity(entity)
                .flatMap(existing -> {
                    existing.setProductTemplateWalkInFormId(entity.getProductTemplateWalkInFormId());
                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductService.updatableEntity"));
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_TEMPLATE;
    }

    public Mono<ProductTemplate> createRequest(ProductTemplateRequest productTemplateRequest) {

        ProductTemplateType productTemplateType = productTemplateRequest.getProductTemplateType();

        if (productTemplateType == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.PRODUCT_TEMPLATE_TYPE_MISSING);

        ProductTemplate productTemplate = ProductTemplate.of(productTemplateRequest);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.create(access, productTemplate),
                        (access, created) -> (productTemplateRequest.getProductId() == null
                                        || productTemplateRequest.getProductId().isNull())
                                ? Mono.just(created)
                                : this.updateDependentServices(access, productTemplateRequest.getProductId(), created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTemplateService.create"));
    }

    public Mono<ProductTemplate> attachEntity(Identity identity, ProductTemplateRequest productTemplateRequest) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.readByIdentity(access, identity),
                        (access, productTemplate) -> this.updateDependentServices(
                                access, productTemplateRequest.getProductId(), productTemplate))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTemplateService.attachEntity"));
    }

    private Mono<ProductTemplate> updateDependentServices(
            ProcessorAccess access, Identity productId, ProductTemplate productTemplate) {
        return productService.setProductTemplate(access, productId, productTemplate);
    }

    public Mono<ProductTemplateWalkInForm> setProductTemplateWalkInForm(
            ProcessorAccess access, ULong productTemplateId, ProductTemplateWalkInForm productTemplateWalkInForm) {
        return FlatMapUtil.flatMapMono(() -> super.readById(access, productTemplateId), productTemplate -> {
                    productTemplate.setProductTemplateWalkInFormId(productTemplateWalkInForm.getId());
                    return super.updateInternal(access, productTemplate).map(updated -> productTemplateWalkInForm);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductService.setProductTemplateWalkInForm"));
    }

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {
        return this.defaultSchemaRepositoryFor(ProductComm.class, classSchema);
    }
}
