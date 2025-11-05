package com.fincity.saas.entity.processor.service.product.template;

import java.util.List;
import java.util.Set;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.product.template.ProductTemplateTicketRURuleDAO;
import com.fincity.saas.entity.processor.dto.product.template.ProductTemplateTicketRURuleDto;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplateTicketRuRulesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import com.google.gson.JsonElement;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ProductTemplateTicketRURuleService
        extends BaseRuleService<
                EntityProcessorProductTemplateTicketRuRulesRecord,
                ProductTemplateTicketRURuleDto,
                ProductTemplateTicketRURuleDAO> {

	private static final String PRODUCT_TEMPLATE_TICKET_C_RULE = "productTemplateTicketRURule";

	private ProductTemplateService productTemplateService;

	@Lazy
	@Autowired
	private void setValueTemplateService(ProductTemplateService productTemplateService) {
		this.productTemplateService = productTemplateService;
	}


	@Override
    protected Mono<Identity> getEntityId(ProcessorAccess access, Identity entityId) {
		return productTemplateService.checkAndUpdateIdentityWithAccess(access, entityId);
    }

    @Override
    protected Mono<Set<ULong>> getStageIds(ProcessorAccess access, Identity entityId, List<ULong> stageIds) {
	    return FlatMapUtil.flatMapMono(
					    () -> productTemplateService.readIdentityInternal(entityId),
					    productTemplate -> super.stageService.getAllStages(
							    access,
							    productTemplate.getId(),
							    stageIds != null ? stageIds.toArray(new ULong[0]) : null))
			    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTemplateRuleService.getStageIds"));
    }

    @Override
    protected String getCacheName() {
	    return PRODUCT_TEMPLATE_TICKET_C_RULE;
    }
}
