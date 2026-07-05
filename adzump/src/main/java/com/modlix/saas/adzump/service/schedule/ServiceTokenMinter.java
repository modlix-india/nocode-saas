package com.modlix.saas.adzump.service.schedule;

import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.model.CampaignPlan;

/**
 * Principal-C token minter (J14 §5.2) — the injectable seam that produces the campaign-scoped
 * {@link ScopedContext} a headless run is pinned to, so downstream reads (J10/J11) and writes (J13
 * apply) run <b>only within the campaign's own client</b> and cannot enumerate another.
 *
 * <p><b>In-service impl (P4).</b> {@link #mint(CampaignPlan)} derives the scope basis (clientCode +
 * campaignId + scope) straight from the campaign row. It does <b>not</b> fabricate a JWT and does not
 * install a Spring {@code SecurityContext} — the scoping guarantee here is that the campaign's client is
 * carried as the effective client throughout the run, never a user-supplied one.
 *
 * <p><b>P4.5 TODO (J11 §9 open question).</b> The REAL issuer — reuse the platform system-token issuer
 * vs an adzump-minted short-lived scoped JWT — must additionally establish an authenticated
 * {@code ContextAuthentication} for the scheduled run so the EDIT-gated downstream services
 * ({@code FeedbackService} / {@code OptimizationEngine} / {@code ActionApplier}) authorize under the
 * campaign's client with no user present. Until that lands, live scheduled execution is the P4.5
 * integration gate; the loop is exercised offline with mocked collaborators.
 */
@Service
public class ServiceTokenMinter {

    /**
     * Mints the campaign-scoped context. The campaign's own {@code clientCode} is the only client the
     * resulting run may touch; {@code campaignId} pins it to exactly this campaign.
     */
    public ScopedContext mint(CampaignPlan campaign) {
        return new ScopedContext(campaign.getClientCode(), campaign.getId(), ScopedContext.CAMPAIGN_OPTIMIZE);
    }
}
