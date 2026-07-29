package com.modlix.saas.adzump.dao;

import static com.modlix.saas.adzump.jooq.tables.AdzumpPerformancePolicy.ADZUMP_PERFORMANCE_POLICY;

import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dto.PerformancePolicy;
import com.modlix.saas.adzump.jooq.enums.AdzumpPerformancePolicyScope;
import com.modlix.saas.adzump.jooq.tables.records.AdzumpPerformancePolicyRecord;

@Service
public class PerformancePolicyDao extends AbstractAdzumpJsonDAO<AdzumpPerformancePolicyRecord, PerformancePolicy> {

    public PerformancePolicyDao() {
        super(PerformancePolicy.class, ADZUMP_PERFORMANCE_POLICY, ADZUMP_PERFORMANCE_POLICY.ID);
    }

    @Override
    protected void readCustomColumns(Record rec, PerformancePolicy pojo) {
        pojo.setBody(fromJson(getJson(rec, ADZUMP_PERFORMANCE_POLICY.BODY), JsonNode.class));
    }

    @Override
    protected void writeCustomColumns(PerformancePolicy pojo, AdzumpPerformancePolicyRecord rec) {
        rec.set(ADZUMP_PERFORMANCE_POLICY.BODY, toJson(pojo.getBody()));
    }

    /**
     * Finds the policy row for the given scope within the (already resolved)
     * effective client. {@code campaignId} must be non-null for
     * {@code CAMPAIGN_OVERRIDE} and null for {@code ACCOUNT_DEFAULT}. Returns
     * {@code null} when no row matches.
     */
    public PerformancePolicy findByScope(String clientCode, AdzumpPerformancePolicyScope scope, ULong campaignId) {

        Condition condition = ADZUMP_PERFORMANCE_POLICY.CLIENT_CODE.eq(clientCode)
                .and(ADZUMP_PERFORMANCE_POLICY.SCOPE.eq(scope))
                .and(campaignId == null
                        ? ADZUMP_PERFORMANCE_POLICY.CAMPAIGN_ID.isNull()
                        : ADZUMP_PERFORMANCE_POLICY.CAMPAIGN_ID.eq(campaignId));

        return this.toPojo(this.dslContext.selectFrom(ADZUMP_PERFORMANCE_POLICY)
                .where(condition)
                .orderBy(ADZUMP_PERFORMANCE_POLICY.ID.desc())
                .limit(1)
                .fetchOne());
    }
}
