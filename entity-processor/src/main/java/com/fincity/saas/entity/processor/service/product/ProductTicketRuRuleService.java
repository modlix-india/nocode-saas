package com.fincity.saas.entity.processor.service.product;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.entity.processor.dao.product.ProductTicketRuRuleDAO;
import com.fincity.saas.entity.processor.dao.rule.TicketRuUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketRuRule;
import com.fincity.saas.entity.processor.dto.rule.TicketRuUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketRuRulesRecord;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketRuUserDistributionsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import com.fincity.saas.entity.processor.service.rule.TicketRuUserDistributionService;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProductTicketRuRuleService
        extends BaseRuleService<
                EntityProcessorProductTicketRuRulesRecord,
                ProductTicketRuRule,
                ProductTicketRuRuleDAO,
                EntityProcessorTicketRuUserDistributionsRecord,
                TicketRuUserDistribution,
                TicketRuUserDistributionDAO> {

    private static final String PRODUCT_TICKET_RU_RULE = "productTicketRURule";
    private static final String CONDITION_CACHE = "ruleConditionCache";

    protected ProductTicketRuRuleService(TicketRuUserDistributionService ticketRUUserDistributionService) {
        super(ticketRUUserDistributionService);
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TICKET_RU_RULE;
    }

    private String getConditionCacheName(String appCode, String clientCode) {
        return super.getCacheName(CONDITION_CACHE, appCode, clientCode);
    }

    private Mono<Boolean> evictConditionCache(String appCode, String clientCode) {
        return super.cacheService.evictAll(this.getConditionCacheName(appCode, clientCode));
    }

    @Override
    protected Mono<Boolean> evictCache(ProductTicketRuRule entity) {
        return Mono.zip(
                super.evictCache(entity),
                this.evictConditionCache(entity.getAppCode(), entity.getClientCode()),
                (baseEvicted, conditionEvicted) -> baseEvicted && conditionEvicted);
    }

    @Override
    public Mono<ProductTicketRuRule> create(ProductTicketRuRule entity) {
        return super.create(entity)
                .flatMap(created -> this.evictConditionCache(entity.getAppCode(), entity.getClientCode())
                        .map(evicted -> created));
    }

    public Mono<AbstractCondition> getUserReadConditions(ProcessorAccess access) {
        return Mono.empty();
    }

    public Mono<List<ProductTicketRuRule>> getConditionsForUserInternal(ProcessorAccess access, boolean isEdit) {
        return FlatMapUtil.flatMapMono(
                () -> this.userDistributionService.getUserForClient(access),
                userInfo -> this.dao.getUserConditions(isEdit, userInfo).collectList());
    }
}
