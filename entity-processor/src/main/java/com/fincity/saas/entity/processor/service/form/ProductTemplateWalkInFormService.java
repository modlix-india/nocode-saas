package com.fincity.saas.entity.processor.service.form;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.functions.AbstractProcessorFunction;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.repository.ListFunctionRepository;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.form.ProductTemplateWalkInFormDAO;
import com.fincity.saas.entity.processor.dto.form.ProductTemplateWalkInForm;
import com.fincity.saas.entity.processor.enums.AssignmentType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplateWalkInFormsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.form.WalkInFormRequest;
import com.fincity.saas.entity.processor.service.product.template.ProductTemplateService;
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
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class ProductTemplateWalkInFormService
        extends BaseWalkInFormService<
                EntityProcessorProductTemplateWalkInFormsRecord,
                ProductTemplateWalkInForm,
                ProductTemplateWalkInFormDAO>
        implements IRepositoryProvider {

    private static final String PRODUCT_TEMPLATE_WALK_IN_FORM_CACHE = "productTemplateWalkInForm";

    private final List<ReactiveFunction> functions = new ArrayList<>();
    private final Gson gson;
    private final ProductTemplateService productTemplateService;

    private final ClassSchema classSchema = ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());

    @Autowired
    @Lazy
    private ProductTemplateWalkInFormService self;

    public ProductTemplateWalkInFormService(ProductTemplateService productTemplateService, Gson gson) {
        this.productTemplateService = productTemplateService;
        this.gson = gson;
    }

    @PostConstruct
    private void init() {

        this.functions.addAll(
                super.getCommonFunctions("ProductTemplateWalkInForm", ProductTemplateWalkInForm.class, gson));

        String dtoSchemaRef = classSchema.getNamespaceForClass(ProductTemplateWalkInForm.class) + "."
                + ProductTemplateWalkInForm.class.getSimpleName();

        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "ProductTemplateWalkInForm",
                "CreateRequest",
                ClassSchema.ArgSpec.ofRef("walkInFormRequest", WalkInFormRequest.class),
                "created",
                Schema.ofRef(dtoSchemaRef),
                gson,
                self::createRequest));

        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "ProductTemplateWalkInForm",
                "GetWalkInForm",
                EntityProcessorArgSpec.identity("productId"),
                "result",
                Schema.ofRef(dtoSchemaRef),
                gson,
                self::getWalkInForm));
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TEMPLATE_WALK_IN_FORM_CACHE;
    }

    @Override
    protected String getProductEntityName() {
        return productTemplateService.getEntityName();
    }

    @Override
    protected Mono<Tuple2<ULong, ULong>> resolveProduct(ProcessorAccess access, Identity productId) {
        return productTemplateService
                .readByIdentity(access, productId)
                .map(productTemplate -> Tuples.of(productTemplate.getId(), productTemplate.getId()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTemplateWalkInFormService.resolveProduct"));
    }

    @Override
    protected ProductTemplateWalkInForm create(
            String name, ULong entityId, ULong stageId, ULong statusId, AssignmentType assignmentType) {
        return (ProductTemplateWalkInForm) new ProductTemplateWalkInForm()
                .setName(name)
                .setProductTemplateId(entityId)
                .setStageId(stageId)
                .setStatusId(statusId)
                .setAssignmentType(assignmentType);
    }

    @Override
    protected Mono<ProductTemplateWalkInForm> attachEntity(
            ProcessorAccess access, ULong productId, ProductTemplateWalkInForm entity) {
        return this.productTemplateService.setProductTemplateWalkInForm(access, productId, entity);
    }

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {
        return this.defaultSchemaRepositoryFor(ProductTemplateWalkInForm.class, classSchema);
    }
}
