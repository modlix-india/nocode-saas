package com.modlix.saas.adzump.dao;

import static com.modlix.saas.adzump.jooq.tables.AdzumpAutonomyConfig.ADZUMP_AUTONOMY_CONFIG;

import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dto.AutonomyConfig;
import com.modlix.saas.adzump.jooq.enums.AdzumpAutonomyConfigScope;
import com.modlix.saas.adzump.jooq.tables.records.AdzumpAutonomyConfigRecord;

@Service
public class AutonomyConfigDao extends AbstractAdzumpJsonDAO<AdzumpAutonomyConfigRecord, AutonomyConfig> {

    public AutonomyConfigDao() {
        super(AutonomyConfig.class, ADZUMP_AUTONOMY_CONFIG, ADZUMP_AUTONOMY_CONFIG.ID);
    }

    @Override
    protected void readCustomColumns(Record rec, AutonomyConfig pojo) {
        pojo.setBody(fromJson(getJson(rec, ADZUMP_AUTONOMY_CONFIG.BODY), JsonNode.class));
    }

    @Override
    protected void writeCustomColumns(AutonomyConfig pojo, AdzumpAutonomyConfigRecord rec) {
        rec.set(ADZUMP_AUTONOMY_CONFIG.BODY, toJson(pojo.getBody()));
    }

    /**
     * Finds the autonomy-config row for the given scope within the (already
     * resolved) effective client. {@code campaignId} must be non-null for
     * {@code CAMPAIGN_OVERRIDE} and null for {@code ACCOUNT_DEFAULT}. Returns
     * {@code null} when no row matches.
     */
    public AutonomyConfig findByScope(String clientCode, AdzumpAutonomyConfigScope scope, ULong campaignId) {

        Condition condition = ADZUMP_AUTONOMY_CONFIG.CLIENT_CODE.eq(clientCode)
                .and(ADZUMP_AUTONOMY_CONFIG.SCOPE.eq(scope))
                .and(campaignId == null
                        ? ADZUMP_AUTONOMY_CONFIG.CAMPAIGN_ID.isNull()
                        : ADZUMP_AUTONOMY_CONFIG.CAMPAIGN_ID.eq(campaignId));

        return this.toPojo(this.dslContext.selectFrom(ADZUMP_AUTONOMY_CONFIG)
                .where(condition)
                .orderBy(ADZUMP_AUTONOMY_CONFIG.ID.desc())
                .limit(1)
                .fetchOne());
    }
}
