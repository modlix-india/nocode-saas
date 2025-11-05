package com.fincity.saas.entity.processor.service.product.template;

import com.fincity.saas.entity.processor.dao.product.template.ProductTemplateTicketRURuleDAO;
import com.fincity.saas.entity.processor.dto.product.template.ProductTemplateTicketRURuleDto;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplateTicketRuRulesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import com.google.gson.JsonElement;
import java.util.List;
import java.util.Set;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProductTemplateTicketRURuleService
        extends BaseRuleService<
                EntityProcessorProductTemplateTicketRuRulesRecord,
                ProductTemplateTicketRURuleDto,
                ProductTemplateTicketRURuleDAO> {

    @Override
    protected Mono<Identity> getEntityId(ProcessorAccess access, Identity entityId) {
        return null;
    }

    @Override
    protected Mono<Set<ULong>> getStageIds(ProcessorAccess access, Identity entityId, List<ULong> stageIds) {
        return null;
    }

    @Override
    protected Mono<ULong> getUserAssignment(
            ProcessorAccess access, ULong entityId, ULong stageId, String tokenPrefix, ULong userId, JsonElement data) {
        return null;
    }

    @Override
    protected String getCacheName() {
        return "";
    }
}
