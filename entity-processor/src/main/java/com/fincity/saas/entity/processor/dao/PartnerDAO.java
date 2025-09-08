package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PARTNERS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.Partner;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorPartnersRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class PartnerDAO extends BaseUpdatableDAO<EntityProcessorPartnersRecord, Partner> {

    protected PartnerDAO() {
        super(Partner.class, ENTITY_PROCESSOR_PARTNERS, ENTITY_PROCESSOR_PARTNERS.ID);
    }

    public Mono<Partner> getPartnerByClientId(ProcessorAccess access, ULong clientId) {
        return FlatMapUtil.flatMapMono(
                () -> this.processorAccessCondition(
                        FilterCondition.make(Partner.Fields.clientId, clientId)
                                .setOperator(FilterConditionOperator.EQUALS),
                        access),
                this::filter,
                (pCondition, jCondition) -> Mono.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(rec -> rec.into(this.pojoClass)));
    }
}
