package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.ValueTemplateDAO;
import com.fincity.saas.entity.processor.dto.ValueTemplate;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorValueTemplatesRecord;
import com.fincity.saas.entity.processor.model.request.ValueTemplateRequest;
import com.fincity.saas.entity.processor.service.base.BaseService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ValueTemplateService
        extends BaseService<EntityProcessorValueTemplatesRecord, ValueTemplate, ValueTemplateDAO>
        implements IEntitySeries {

    private static final String VALUE_TEMPLATE = "valueTemplate";

    private final ProductService productService;

    public ValueTemplateService(ProductService productService) {
        this.productService = productService;
    }

    @Override
    protected String getCacheName() {
        return VALUE_TEMPLATE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.VALUE_TEMPLATE;
    }

    public Mono<ValueTemplate> create(ValueTemplateRequest valueTemplateRequest) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> super.create(ValueTemplate.of(valueTemplateRequest)),
                (hasAccess, valueTemplate) -> {
                    if (!valueTemplate.getValueTemplateType().isAppLevel())
                        return productService
                                .updateProductTemplate(valueTemplateRequest.getValueEntityId(), valueTemplate.getId())
                                .map(product -> valueTemplate);

                    return Mono.just(valueTemplate);
                });
    }
}
