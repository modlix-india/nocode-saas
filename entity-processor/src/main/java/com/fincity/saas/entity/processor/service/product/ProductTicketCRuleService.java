package com.fincity.saas.entity.processor.service.product;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.product.ProductTicketCRuleDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketCRuleDto;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketCRulesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.product.template.ProductTemplateTicketCRuleService;
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
public class ProductTicketCRuleService
        extends BaseRuleService<
                EntityProcessorProductTicketCRulesRecord, ProductTicketCRuleDto, ProductTicketCRuleDAO> {

    private static final String PRODUCT_TICKET_C_RULE = "productTicketCRule";

    private ProductService productService;
    private ProductTemplateTicketCRuleService productTemplateTicketCRuleService;

    @Lazy
    @Autowired
    private void setProductService(ProductService productService) {
        this.productService = productService;
    }

    @Lazy
    @Autowired
    private void setProductTemplateTicketCRuleService(
            ProductTemplateTicketCRuleService productTemplateTicketCRuleService) {
        this.productTemplateTicketCRuleService = productTemplateTicketCRuleService;
    }

    @Override
    protected Mono<Identity> getEntityId(ProcessorAccess access, Identity entityId) {
        return productService.checkAndUpdateIdentityWithAccess(access, entityId);
    }

    @Override
    protected Mono<Set<ULong>> getStageIds(ProcessorAccess access, Identity entityId, List<ULong> stageIds) {
        return FlatMapUtil.flatMapMono(
                        () -> productService.readIdentityInternal(entityId),
                        product -> super.stageService.getAllStages(
                                access,
                                product.getProductTemplateId(),
                                stageIds != null ? stageIds.toArray(new ULong[0]) : null))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductStageRuleService.getStageIds"));
    }

    @Override
    public Mono<ULong> getUserAssignment(
            ProcessorAccess access, ULong entityId, ULong stageId, String tokenPrefix, ULong userId, JsonElement data) {
        return FlatMapUtil.flatMapMono(
                        () -> this.getRulesWithOrder(access, entityId, stageId),
                        productRule -> super.ruleExecutionService.executeRules(productRule, tokenPrefix, userId, data),
                        (productRule, eRule) -> super.updateInternalForOutsideUser(eRule),
                        (productRule, eRule, uRule) -> {
                            ULong assignedUserId = uRule.getLastAssignedUserId();
                            if (assignedUserId == null || assignedUserId.equals(ULong.valueOf(0))) return Mono.empty();
                            return Mono.just(assignedUserId);
                        })
                .switchIfEmpty(this.getUserAssignmentFromTemplate(access, entityId, stageId, tokenPrefix, userId, data))
                .onErrorResume(e -> Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductStageRuleService.getUserAssignment"));
    }

    private Mono<ULong> getUserAssignmentFromTemplate(
            ProcessorAccess access, ULong entityId, ULong stageId, String tokenPrefix, ULong userId, JsonElement data) {
        return FlatMapUtil.flatMapMono(
                        () -> productService.readById(entityId),
                        product -> this.productTemplateTicketCRuleService.getUserAssignment(
                                access, product.getProductTemplateId(), stageId, tokenPrefix, userId, data))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductStageRuleService.getUserAssignmentFromTemplate"));
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TICKET_C_RULE;
    }
}
