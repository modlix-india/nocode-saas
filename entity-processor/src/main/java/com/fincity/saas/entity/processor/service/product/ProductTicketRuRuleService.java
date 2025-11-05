package com.fincity.saas.entity.processor.service.product;

import com.fincity.saas.entity.processor.dao.product.ProductTicketRURuleDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketRURuleDto;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketRuRulesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import java.util.List;
import java.util.Set;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProductTicketRuRuleService
        extends BaseRuleService<
                EntityProcessorProductTicketRuRulesRecord, ProductTicketRURuleDto, ProductTicketRURuleDAO> {

    private static final String PRODUCT_TICKET_RU_RULE = "productTicketRURule";

    @Override
    protected Mono<Identity> getEntityId(ProcessorAccess access, Identity entityId) {
        return null;
    }

    @Override
    protected Mono<Set<ULong>> getStageIds(ProcessorAccess access, Identity entityId, List<ULong> stageIds) {
        return null;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TICKET_RU_RULE;
    }
}
