package com.fincity.security.dao.billing;

import static com.fincity.security.jooq.tables.SecurityCreditPricing.SECURITY_CREDIT_PRICING;

import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.billing.CreditPricing;
import com.fincity.security.jooq.enums.SecurityCreditPricingCostBasisType;
import com.fincity.security.jooq.enums.SecurityCreditPricingStatus;
import com.fincity.security.jooq.tables.records.SecurityCreditPricingRecord;

import reactor.core.publisher.Mono;

@Component
public class CreditPricingDAO extends AbstractUpdatableDAO<SecurityCreditPricingRecord, ULong, CreditPricing> {

    public CreditPricingDAO() {
        super(CreditPricing.class, SECURITY_CREDIT_PRICING, SECURITY_CREDIT_PRICING.ID);
    }

    /**
     * Find the active pricing row matching the given cost basis, vendor, model/SKU
     * and unit. Null vendor/model/unit are matched as IS NULL.
     */
    public Mono<CreditPricing> findActive(SecurityCreditPricingCostBasisType costBasisType,
            String vendor, String modelOrSku, String unit) {

        Condition cond = SECURITY_CREDIT_PRICING.COST_BASIS_TYPE.eq(costBasisType)
                .and(SECURITY_CREDIT_PRICING.STATUS.eq(SecurityCreditPricingStatus.ACTIVE))
                .and(vendor == null ? SECURITY_CREDIT_PRICING.VENDOR.isNull()
                        : SECURITY_CREDIT_PRICING.VENDOR.eq(vendor))
                .and(modelOrSku == null ? SECURITY_CREDIT_PRICING.MODEL_OR_SKU.isNull()
                        : SECURITY_CREDIT_PRICING.MODEL_OR_SKU.eq(modelOrSku))
                .and(unit == null ? DSL.noCondition() : SECURITY_CREDIT_PRICING.UNIT.eq(unit));

        return Mono.from(this.dslContext.selectFrom(SECURITY_CREDIT_PRICING)
                .where(cond)
                .orderBy(SECURITY_CREDIT_PRICING.EFFECTIVE_FROM.desc())
                .limit(1))
                .map(r -> r.into(CreditPricing.class));
    }
}
