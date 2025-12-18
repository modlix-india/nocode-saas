package com.fincity.saas.entity.processor.service.product;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.functions.AbstractProcessorFunction;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.commons.functions.repository.ListFunctionRepository;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.product.ProductDAO;
import com.fincity.saas.entity.processor.dto.form.ProductWalkInForm;
import com.fincity.saas.entity.processor.dto.product.Product;
import com.fincity.saas.entity.processor.dto.product.ProductTemplate;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductsRecord;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.product.ProductPartnerUpdateRequest;
import com.fincity.saas.entity.processor.model.request.product.ProductRequest;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import com.fincity.saas.entity.processor.service.product.template.ProductTemplateService;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ProductService extends BaseProcessorService<EntityProcessorProductsRecord, Product, ProductDAO>
        implements IRepositoryProvider {

    private static final String PRODUCT_CACHE = "product";

    private final List<ReactiveFunction> functions = new ArrayList<>();
    private final Gson gson;

    private final ClassSchema classSchema = ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());

    private ProductTemplateService productTemplateService;

    @Lazy
    @Autowired
    private void setValueTemplateService(ProductTemplateService productTemplateService) {
        this.productTemplateService = productTemplateService;
    }

    @Autowired
    @Lazy
    private ProductService self;

    public ProductService(Gson gson) {
        this.gson = gson;
    }

    @PostConstruct
    private void init() {

        this.functions.addAll(super.getCommonFunctions("Product", Product.class, gson));

        String productSchemaRef = classSchema.getNamespaceForClass(Product.class) + "." + Product.class.getSimpleName();

        // ProductController: createRequest(ProductRequest)
        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "Product",
                "CreateRequest",
                ClassSchema.ArgSpec.ofRef("productRequest", ProductRequest.class),
                "created",
                Schema.ofRef(productSchemaRef),
                gson,
                self::createRequest));

        // ProductController: updateForPartner(ProductPartnerUpdateRequest)
        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "Product",
                "UpdateForPartner",
                ClassSchema.ArgSpec.ofRef("request", ProductPartnerUpdateRequest.class),
                "updated",
                Schema.ofInteger("updated"),
                gson,
                self::updateForPartner));

        // ProductController: getProductInternal(appCode, clientCode, identity)
        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "Product",
                "GetProductInternal",
                ClassSchema.ArgSpec.string("appCode"),
                ClassSchema.ArgSpec.string("clientCode"),
                EntityProcessorArgSpec.identity("identity"),
                "result",
                Schema.ofRef(productSchemaRef),
                gson,
                (appCode, clientCode, identity) -> self.readByIdentity(
                        ProcessorAccess.of(appCode, clientCode, Boolean.TRUE, null, null), identity)));

        // ProductController: getProductsInternal(appCode, clientCode, productIds)
        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "Product",
                "GetProductsInternal",
                ClassSchema.ArgSpec.string("appCode"),
                ClassSchema.ArgSpec.string("clientCode"),
                EntityProcessorArgSpec.uLongList("productIds"),
                "result",
                Schema.ofArray("result", Schema.ofRef(productSchemaRef)),
                gson,
                (appCode, clientCode, productIds) -> self.getAllProducts(
                        ProcessorAccess.of(appCode, clientCode, Boolean.TRUE, null, null), productIds)));
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_CACHE;
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT;
    }

    @Override
    protected Mono<Product> checkEntity(Product product, ProcessorAccess access) {

        if (product.getId() == null && product.getName().isEmpty())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.NAME_MISSING);

        return super.checkExistsByName(access, product)
                .flatMap(exists -> product.getProductTemplateId() != null
                        ? productTemplateService
                                .readById(access, product.getProductTemplateId())
                                .thenReturn(product)
                        : Mono.just(product))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductService.checkEntity"));
    }

    @Override
    protected Mono<Product> updatableEntity(Product entity) {
        return super.updatableEntity(entity)
                .flatMap(existing -> {
                    existing.setProductTemplateId(entity.getProductTemplateId());
                    existing.setForPartner(entity.isForPartner());
                    existing.setOverrideCTemplate(entity.isOverrideCTemplate());
                    existing.setOverrideRuTemplate(entity.isOverrideRuTemplate());
                    existing.setProductWalkInFormId(entity.getProductWalkInFormId());
                    existing.setLogoFileDetail(entity.getLogoFileDetail());
                    existing.setBannerFileDetail(entity.getBannerFileDetail());

                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductService.updatableEntity"));
    }

    public Mono<Product> createRequest(ProductRequest productRequest) {

        if (productRequest.getProductTemplateId() == null
                || productRequest.getProductTemplateId().isNull())
            return super.create(Product.of(productRequest))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductService.create[ProductRequest]"));

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.create(access, Product.of(productRequest)),
                        (access, created) ->
                                productTemplateService.readByIdentity(productRequest.getProductTemplateId()),
                        (access, created, productTemplate) -> {
                            created.setProductTemplateId(productTemplate.getId());
                            return super.updateInternal(access, created);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductService.create[ProductRequest]"));
    }

    @IgnoreGeneration
    public Mono<ProductTemplate> setProductTemplate(
            ProcessorAccess access, Identity productId, ProductTemplate productTemplate) {
        return FlatMapUtil.flatMapMono(() -> super.readByIdentity(access, productId), product -> {
                    product.setProductTemplateId(productTemplate.getId());
                    return super.updateInternal(access, product).map(updated -> productTemplate);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductService.setProductTemplate"));
    }

    @IgnoreGeneration
    public Mono<ProductWalkInForm> setProductWalkInForm(
            ProcessorAccess access, ULong productId, ProductWalkInForm productWalkInForm) {
        return FlatMapUtil.flatMapMono(() -> super.readById(access, productId), product -> {
                    product.setProductWalkInFormId(productWalkInForm.getId());
                    return super.updateInternal(access, product).map(updated -> productWalkInForm);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductService.setProductWalkInForm"));
    }

    public Mono<Long> updateForPartner(ProductPartnerUpdateRequest request) {
        if (request.getProductPartnerActive() == null
                || request.getProductPartnerActive().isEmpty())
            return Mono.just(0L).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductService.updateForPartner"));

        return this.hasAccess()
                .flatMap(access -> {
                    List<IdAndValue<Identity, Boolean>> entries = request.getProductPartnerActive();

                    return Flux.fromIterable(entries)
                            .flatMap(entry -> this.readByIdentity(access, entry.getId())
                                    .map(product -> product.setForPartner(entry.getValue())))
                            .collectList()
                            .flatMapMany(validatedProducts -> Flux.fromIterable(validatedProducts)
                                    .flatMap(vProduct -> super.updateInternal(access, vProduct)))
                            .count();
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductService.updateForPartner"));
    }

    @IgnoreGeneration
    public Mono<List<Product>> getAllProducts(ProcessorAccess access, List<ULong> productIds) {
        return this.dao.getAllProducts(access, productIds);
    }

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {
        return this.defaultSchemaRepositoryFor(Product.class, classSchema);
    }
}
