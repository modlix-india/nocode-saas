package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.ProductDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.dto.ValueTemplate;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.ProductRequest;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

@Service
public class ProductService extends BaseProcessorService<EntityProcessorProductsRecord, Product, ProductDAO>
        implements IValueTemplateService {

    private static final String PRODUCT_CACHE = "product";

    private StageService stageService;

    @Lazy
    @Autowired
    private void setStageService(StageService stageService) {
        this.stageService = stageService;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_CACHE;
    }

    @Override
    protected Mono<Product> checkEntity(Product product, Tuple3<String, String, ULong> accessInfo) {

        if (product.getName().isEmpty())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.NAME_MISSING);

        return FlatMapUtil.flatMapMono(
                () -> product.getDefaultStageId() != null && product.getDefaultStatusId() != null
                        ? stageService.isValidParentChild(
                                accessInfo.getT1(),
                                accessInfo.getT2(),
                                null,
                                product.getValueTemplateId(),
                                product.getDefaultStageId(),
                                product.getDefaultStatusId())
                        : Mono.just(Boolean.TRUE),
                isValid -> {
                    if (Boolean.FALSE.equals(isValid))
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.INVALID_CHILD_FOR_PARENT, product.getDefaultStatusId(), product.getDefaultStageId());

                    return Mono.just(product);
                });
    }

    public Mono<Product> create(ProductRequest productRequest) {
        return super.create(Product.of(productRequest));
    }

    public Mono<Product> readWithAccess(Identity identity) {
        return super.hasAccess().flatMap(hasAccess -> {
            if (Boolean.FALSE.equals(hasAccess.getT2()))
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        ProcessorMessageResourceService.PRODUCT_FORBIDDEN_ACCESS, identity.toString());

            return this.readIdentityInternal(identity);
        });
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT;
    }

    @Override
    public Mono<ValueTemplate> updateValueTemplate(Identity identity, ValueTemplate valueTemplate) {
        return FlatMapUtil.flatMapMono(() -> super.readIdentityInternal(identity), product -> {
            product.setValueTemplateId(valueTemplate.getId());
            return super.updateInternal(product).map(updated -> valueTemplate);
        });
    }
}
