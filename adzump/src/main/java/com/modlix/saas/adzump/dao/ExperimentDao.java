package com.modlix.saas.adzump.dao;

import static com.modlix.saas.adzump.jooq.tables.AdzumpExperiment.ADZUMP_EXPERIMENT;

import java.util.List;

import org.jooq.Record;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.modlix.saas.adzump.dto.Experiment;
import com.modlix.saas.adzump.dto.ExperimentReadout;
import com.modlix.saas.adzump.dto.ExperimentVariant;
import com.modlix.saas.adzump.jooq.tables.records.AdzumpExperimentRecord;

/**
 * DAO for {@code adzump_experiment} (J21 §5.1). Extends {@link AbstractAdzumpJsonDAO} so the two JSON
 * columns the plain JOOQ record&lt;-&gt;POJO mapper cannot translate ({@code variants} — a list of
 * {@link ExperimentVariant} arms, and {@code readout} — the {@link ExperimentReadout} stats) map through the
 * shared JSON hook, while every other column ({@code id}, {@code client_code}, {@code campaign_plan_id},
 * {@code hypothesis}, {@code metric}, the volume/duration caps, the {@code status} enum, {@code started_at}/
 * {@code ended_at}, audit) is the plain JOOQ mapping.
 *
 * <p>Every read is scoped to the resolved effective client, so an experiment (and the account attribute
 * wins it feeds into J20) stays tenant-private.
 */
@Service
public class ExperimentDao extends AbstractAdzumpJsonDAO<AdzumpExperimentRecord, Experiment> {

    private static final TypeReference<List<ExperimentVariant>> VARIANTS_TYPE = new TypeReference<>() {
    };

    public ExperimentDao() {
        super(Experiment.class, ADZUMP_EXPERIMENT, ADZUMP_EXPERIMENT.ID);
    }

    @Override
    protected void readCustomColumns(Record rec, Experiment pojo) {
        pojo.setVariants(fromJson(getJson(rec, ADZUMP_EXPERIMENT.VARIANTS), VARIANTS_TYPE));
        pojo.setReadout(fromJson(getJson(rec, ADZUMP_EXPERIMENT.READOUT), ExperimentReadout.class));
    }

    @Override
    protected void writeCustomColumns(Experiment pojo, AdzumpExperimentRecord rec) {
        // variants is NOT NULL; guarantee a JSON array even if a caller left it unset.
        rec.set(ADZUMP_EXPERIMENT.VARIANTS, toJson(pojo.getVariants() == null ? List.of() : pojo.getVariants()));
        rec.set(ADZUMP_EXPERIMENT.READOUT, toJson(pojo.getReadout()));
    }

    /**
     * Every experiment recorded for a campaign within the effective client, newest first — the read the
     * studio and the loop's progression list against. Scoped to {@code clientCode}, so it stays
     * tenant-private.
     */
    public List<Experiment> findByCampaign(String clientCode, ULong campaignPlanId) {

        return this.dslContext.selectFrom(ADZUMP_EXPERIMENT)
                .where(ADZUMP_EXPERIMENT.CLIENT_CODE.eq(clientCode))
                .and(ADZUMP_EXPERIMENT.CAMPAIGN_PLAN_ID.eq(campaignPlanId))
                .orderBy(ADZUMP_EXPERIMENT.ID.desc())
                .fetch()
                .map(this::toPojo);
    }
}
