package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.rule.TicketDuplicationRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.NoOpUserDistribution;
import com.fincity.saas.entity.processor.dto.rule.TicketDuplicationRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketDuplicationRulesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.StageService;
import lombok.Getter;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TicketDuplicationRuleService
        extends BaseRuleService<
                EntityProcessorTicketDuplicationRulesRecord,
                TicketDuplicationRule,
                TicketDuplicationRuleDAO,
                NoOpUserDistribution> {

    private static final String TICKET_DUPLICATION_RULE = "ticketDuplicationRule";

    @Getter
    private TicketCUserDistributionService userDistributionService;

    private StageService stageService;

    @Autowired
    public void setUserDistributionService(TicketCUserDistributionService userDistributionService) {
        this.userDistributionService = userDistributionService;
    }

    @Autowired
    private void setStageService(StageService stageService) {
        this.stageService = stageService;
    }

    @Override
    protected String getCacheName() {
        return TICKET_DUPLICATION_RULE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET_DUPLICATION_RULES;
    }

    @Override
    protected Mono<TicketDuplicationRule> checkEntity(TicketDuplicationRule entity, ProcessorAccess access) {

        if (entity.getSource() == null || entity.getSource().isEmpty())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    "Source");

        if (entity.getMaxStageId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.STAGE_MISSING);

        return FlatMapUtil.flatMapMonoWithNull(
                () -> super.checkEntity(entity, access),
                cEntity -> this.dao.getRule(
                        access,
                        cEntity.getProductId(),
                        cEntity.getProductTemplateId(),
                        cEntity.getSource(),
                        cEntity.getSubSource()),
                (cEntity, existingRule) -> {
                    if (existingRule != null
                            && (cEntity.getId() == null || !existingRule.getId().equals(cEntity.getId())))
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.DUPLICATE_SOURCE_SUBSOURCE_RULE,
                                cEntity.getSource(),
                                cEntity.getSubSource() != null ? cEntity.getSubSource() : "",
                                existingRule.getId());

                    return Mono.just(cEntity);
                },
                (cEntity, existingRule, validatedEntity) -> this.stageService
                        .getStage(access, validatedEntity.getProductTemplateId(), validatedEntity.getMaxStageId())
                        .switchIfEmpty(this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.TEMPLATE_STAGE_INVALID,
                                validatedEntity.getMaxStageId(),
                                validatedEntity.getProductTemplateId()))
                        .thenReturn(validatedEntity));
    }

    public Mono<TicketDuplicationRule> getDuplicationRule(
            ProcessorAccess access, ULong productId, String source, String subSource) {
        return this.dao.getRule(access, productId, null, source, subSource);
    }
}
