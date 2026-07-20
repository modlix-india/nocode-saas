package com.modlix.saas.adzump.model.snapshot;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The JSON {@code body} of an {@code adzump_performance_snapshot} row (J1). The flat columns
 * (client_code, campaign_plan_id, window_from/to, timezone, taken_at) carry the addressing + time
 * series key; this body carries the metrics payload — the joined grain rows plus the rolled-up
 * blended score and the product template the CRM outcomes were aggregated against.
 *
 * <p>Held separate from the rich {@link PerformanceSnapshot} domain aggregate so the persistence
 * layer serializes exactly the grain-row payload (mirrors the {@code CampaignPlan} /
 * {@code CampaignPlanBody} split), while the addressing fields stay first-class columns.
 */
@Data
@Accessors(chain = true)
public class PerformanceSnapshotBody implements Serializable {

    @Serial
    private static final long serialVersionUID = 7301928476510293845L;

    /** the leadzump product template whose MilestoneMapping the CRM outcomes were folded against. */
    private String productTemplateId;

    /** the rolled-up 0..100 blended score across the coarsest-grain rows (J10 §5.4). */
    private double rollupScore;

    private List<SnapshotRow> grainRows;
}
