package com.fincity.saas.entity.processor.service.product.template;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.product.template.ProductTemplateTicketCRuleDAO;
import com.fincity.saas.entity.processor.dto.product.template.ProductTemplateTicketCRuleDto;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplateTicketCRulesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import com.google.gson.JsonElement;
import java.util.List;
import java.util.Set;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ProductTemplateTicketCRuleService
        extends BaseRuleService<
                EntityProcessorProductTemplateTicketCRulesRecord,
                ProductTemplateTicketCRuleDto,
                ProductTemplateTicketCRuleDAO> {

    private static final String PRODUCT_TEMPLATE_TICKET_C_RULE = "productTemplateTicketCRule";

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
    public Mono<ULong> getUserAssignment(
            ProcessorAccess access, ULong entityId, ULong stageId, String tokenPrefix, ULong userId, JsonElement data) {
        return FlatMapUtil.flatMapMono(
                        () -> this.getRulesWithOrder(access, entityId, stageId),
                        productTemplateRules -> super.ruleExecutionService.executeRules(
                                productTemplateRules, tokenPrefix, userId, data),
                        (productTemplateRules, eRule) -> super.updateInternalForOutsideUser(eRule),
                        (productTemplateRules, eRule, uRule) -> {
                            ULong assignedUserId = uRule.getLastAssignedUserId();
                            if (assignedUserId == null || assignedUserId.equals(ULong.valueOf(0))) return Mono.empty();

                            return Mono.just(assignedUserId);
                        })
                .onErrorResume(e -> Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTemplateRuleService.getUserAssignment"));
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TEMPLATE_TICKET_C_RULE;
    }
}
