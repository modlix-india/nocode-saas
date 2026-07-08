package com.modlix.saas.adzump.dao;

import static com.modlix.saas.adzump.jooq.tables.AdzumpPerformanceSnapshot.ADZUMP_PERFORMANCE_SNAPSHOT;

import java.util.List;

import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.dto.PerformanceSnapshotEntity;
import com.modlix.saas.adzump.jooq.tables.records.AdzumpPerformanceSnapshotRecord;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshotBody;

/**
 * Append-only DAO over the existing {@code adzump_performance_snapshot} table (J1). The grain-row
 * metrics live in the JSON {@code body} column; the flat columns are the addressing + the
 * time-series key.
 *
 * <p><b>Append-only.</b> J10 only ever {@code create}s (inherited from
 * {@link AbstractAdzumpJsonDAO}): each build inserts a new row with a fresh {@code taken_at}, so the
 * series shows slow-signal maturation. A snapshot is never mutated, so {@code update} is intentionally
 * unused here.
 */
@Service
public class PerformanceSnapshotDao
        extends AbstractAdzumpJsonDAO<AdzumpPerformanceSnapshotRecord, PerformanceSnapshotEntity> {

    public PerformanceSnapshotDao() {
        super(PerformanceSnapshotEntity.class, ADZUMP_PERFORMANCE_SNAPSHOT, ADZUMP_PERFORMANCE_SNAPSHOT.ID);
    }

    @Override
    protected void readCustomColumns(Record rec, PerformanceSnapshotEntity pojo) {
        pojo.setBody(fromJson(getJson(rec, ADZUMP_PERFORMANCE_SNAPSHOT.BODY), PerformanceSnapshotBody.class));
    }

    @Override
    protected void writeCustomColumns(PerformanceSnapshotEntity pojo, AdzumpPerformanceSnapshotRecord rec) {
        rec.set(ADZUMP_PERFORMANCE_SNAPSHOT.BODY, toJson(pojo.getBody()));
    }

    /**
     * The most recent snapshot for a plan within the (already resolved) effective client, or
     * {@code null} when none exists. Ordered by {@code taken_at} then {@code id} so ties within the
     * same second resolve to the latest inserted row.
     */
    public PerformanceSnapshotEntity findLatest(String clientCode, ULong campaignPlanId) {

        Condition condition = ADZUMP_PERFORMANCE_SNAPSHOT.CLIENT_CODE.eq(clientCode)
                .and(ADZUMP_PERFORMANCE_SNAPSHOT.CAMPAIGN_PLAN_ID.eq(campaignPlanId));

        return this.toPojo(this.dslContext.selectFrom(ADZUMP_PERFORMANCE_SNAPSHOT)
                .where(condition)
                .orderBy(ADZUMP_PERFORMANCE_SNAPSHOT.TAKEN_AT.desc(), ADZUMP_PERFORMANCE_SNAPSHOT.ID.desc())
                .limit(1)
                .fetchOne());
    }

    /**
     * The full time series of snapshots for a plan within the (already resolved) effective client,
     * oldest first, so the caller sees slow-signal maturation over successive builds.
     */
    public List<PerformanceSnapshotEntity> findSeries(String clientCode, ULong campaignPlanId) {

        Condition condition = ADZUMP_PERFORMANCE_SNAPSHOT.CLIENT_CODE.eq(clientCode)
                .and(ADZUMP_PERFORMANCE_SNAPSHOT.CAMPAIGN_PLAN_ID.eq(campaignPlanId));

        return this.dslContext.selectFrom(ADZUMP_PERFORMANCE_SNAPSHOT)
                .where(condition)
                .orderBy(ADZUMP_PERFORMANCE_SNAPSHOT.TAKEN_AT.asc(), ADZUMP_PERFORMANCE_SNAPSHOT.ID.asc())
                .fetch()
                .map(this::toPojo);
    }

    /**
     * Every snapshot for a client across all its campaigns, <b>newest first</b>, so a consumer can keep
     * the first row seen per {@code campaign_plan_id} as that campaign's latest snapshot. Scoped to
     * {@code clientCode} (tenant-private). Used by J20's account-wide attribute attribution, which reads
     * the account's realized outcomes without addressing a single campaign.
     */
    public List<PerformanceSnapshotEntity> findByClient(String clientCode) {

        return this.dslContext.selectFrom(ADZUMP_PERFORMANCE_SNAPSHOT)
                .where(ADZUMP_PERFORMANCE_SNAPSHOT.CLIENT_CODE.eq(clientCode))
                .orderBy(ADZUMP_PERFORMANCE_SNAPSHOT.TAKEN_AT.desc(), ADZUMP_PERFORMANCE_SNAPSHOT.ID.desc())
                .fetch()
                .map(this::toPojo);
    }
}
