package com.modlix.saas.adzump.dao;

import static com.modlix.saas.adzump.jooq.tables.AdzumpCompetitionResearch.ADZUMP_COMPETITION_RESEARCH;

import org.jooq.Condition;
import org.jooq.Record;
import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.dto.CompetitionResearchEntity;
import com.modlix.saas.adzump.jooq.tables.records.AdzumpCompetitionResearchRecord;
import com.modlix.saas.adzump.model.competition.CompetitionResearchBody;

/**
 * DAO over {@code adzump_competition_research} (J19). The ranked findings live in the JSON {@code body}
 * column; the flat columns are the addressing + the {@code generated_at} time key.
 *
 * <p><b>Append-only.</b> Each research run {@code create}s a new row with a fresh {@code generated_at}
 * (inherited from {@link AbstractAdzumpJsonDAO}), so a product accrues a history of findings and
 * {@link #findLatestByProduct} returns the most recent. A finding is never mutated.
 *
 * <p>All finders are scoped to {@code client_code} — findings are <b>tenant-private</b> (J19 §5.5), so
 * no query can resolve another client's research.
 */
@Service
public class CompetitionResearchDao
        extends AbstractAdzumpJsonDAO<AdzumpCompetitionResearchRecord, CompetitionResearchEntity> {

    public CompetitionResearchDao() {
        super(CompetitionResearchEntity.class, ADZUMP_COMPETITION_RESEARCH, ADZUMP_COMPETITION_RESEARCH.ID);
    }

    @Override
    protected void readCustomColumns(Record rec, CompetitionResearchEntity pojo) {
        pojo.setBody(fromJson(getJson(rec, ADZUMP_COMPETITION_RESEARCH.BODY), CompetitionResearchBody.class));
    }

    @Override
    protected void writeCustomColumns(CompetitionResearchEntity pojo, AdzumpCompetitionResearchRecord rec) {
        rec.set(ADZUMP_COMPETITION_RESEARCH.BODY, toJson(pojo.getBody()));
    }

    /**
     * The most recent research finding for a product within the (already resolved) effective client, or
     * {@code null} when none exists. Ordered by {@code generated_at} then {@code id} so ties within the
     * same second resolve to the latest inserted row.
     */
    public CompetitionResearchEntity findLatestByProduct(String clientCode, String productId) {

        Condition condition = ADZUMP_COMPETITION_RESEARCH.CLIENT_CODE.eq(clientCode)
                .and(ADZUMP_COMPETITION_RESEARCH.PRODUCT_ID.eq(productId));

        return this.toPojo(this.dslContext.selectFrom(ADZUMP_COMPETITION_RESEARCH)
                .where(condition)
                .orderBy(ADZUMP_COMPETITION_RESEARCH.GENERATED_AT.desc(), ADZUMP_COMPETITION_RESEARCH.ID.desc())
                .limit(1)
                .fetchOne());
    }
}
