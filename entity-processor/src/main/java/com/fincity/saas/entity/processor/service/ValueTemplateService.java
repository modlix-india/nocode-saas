package com.fincity.saas.entity.processor.service;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.ValueTemplateDAO;
import com.fincity.saas.entity.processor.dto.ValueTemplate;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.enums.ValueTemplateType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorValueTemplatesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.ValueTemplateRequest;
import com.fincity.saas.entity.processor.service.base.BaseService;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

@Service
public class ValueTemplateService
        extends BaseService<EntityProcessorValueTemplatesRecord, ValueTemplate, ValueTemplateDAO>
        implements IEntitySeries {

    private static final String VALUE_TEMPLATE = "valueTemplate";

    private final ProductService productService;

    private final Map<ValueTemplateType, IValueTemplateService> services = new EnumMap<>(ValueTemplateType.class);

    public ValueTemplateService(ProductService productService) {
        this.productService = productService;
    }

    @PostConstruct
    public void init() {
        this.services.put(ValueTemplateType.PRODUCT, productService);
        this.services.put(ValueTemplateType.ENTITY, null);
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

        ValueTemplate valueTemplate = ValueTemplate.of(valueTemplateRequest);

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> {
                    valueTemplate.setAppCode(hasAccess.getT1().getT1());
                    valueTemplate.setClientCode(hasAccess.getT1().getT2());

                    if (valueTemplate.getAddedByUserId() == null)
                        valueTemplate.setAddedByUserId(hasAccess.getT1().getT3());

                    return super.create(valueTemplate);
                },
                (hasAccess, created) -> updateDependentServices(created, valueTemplateRequest.getValueEntityId()));
    }

    private Mono<ValueTemplate> updateDependentServices(ValueTemplate valueTemplate, Identity identity) {
        IValueTemplateService service = services.get(valueTemplate.getValueTemplateType());

        if (service == null) return Mono.just(valueTemplate);

        return service.updateValueTemplate(identity, valueTemplate);
    }
}
