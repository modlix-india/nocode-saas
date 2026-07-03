package com.modlix.saas.adzump.dao;

import static com.modlix.saas.adzump.jooq.tables.AdzumpMilestoneMapping.ADZUMP_MILESTONE_MAPPING;

import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dto.MilestoneMapping;
import com.modlix.saas.adzump.jooq.enums.AdzumpMilestoneMappingScope;
import com.modlix.saas.adzump.jooq.tables.records.AdzumpMilestoneMappingRecord;

@Service
public class MilestoneMappingDao extends AbstractAdzumpJsonDAO<AdzumpMilestoneMappingRecord, MilestoneMapping> {

    public MilestoneMappingDao() {
        super(MilestoneMapping.class, ADZUMP_MILESTONE_MAPPING, ADZUMP_MILESTONE_MAPPING.ID);
    }

    @Override
    protected void readCustomColumns(Record rec, MilestoneMapping pojo) {
        pojo.setBody(fromJson(getJson(rec, ADZUMP_MILESTONE_MAPPING.BODY), JsonNode.class));
    }

    @Override
    protected void writeCustomColumns(MilestoneMapping pojo, AdzumpMilestoneMappingRecord rec) {
        rec.set(ADZUMP_MILESTONE_MAPPING.BODY, toJson(pojo.getBody()));
    }

    /**
     * Finds the milestone-mapping row for the given product template and scope
     * within the (already resolved) effective client. {@code campaignId} must be
     * non-null for {@code CAMPAIGN_OVERRIDE} and null for
     * {@code ACCOUNT_DEFAULT}. Returns {@code null} when no row matches.
     */
    public MilestoneMapping findByTemplate(String clientCode, String productTemplateId,
            AdzumpMilestoneMappingScope scope, ULong campaignId) {

        Condition condition = ADZUMP_MILESTONE_MAPPING.CLIENT_CODE.eq(clientCode)
                .and(ADZUMP_MILESTONE_MAPPING.PRODUCT_TEMPLATE_ID.eq(productTemplateId))
                .and(ADZUMP_MILESTONE_MAPPING.SCOPE.eq(scope))
                .and(campaignId == null
                        ? ADZUMP_MILESTONE_MAPPING.CAMPAIGN_ID.isNull()
                        : ADZUMP_MILESTONE_MAPPING.CAMPAIGN_ID.eq(campaignId));

        return this.toPojo(this.dslContext.selectFrom(ADZUMP_MILESTONE_MAPPING)
                .where(condition)
                .orderBy(ADZUMP_MILESTONE_MAPPING.ID.desc())
                .limit(1)
                .fetchOne());
    }
}
