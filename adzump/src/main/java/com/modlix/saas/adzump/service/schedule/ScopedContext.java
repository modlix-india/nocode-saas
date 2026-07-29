package com.modlix.saas.adzump.service.schedule;

import org.jooq.types.ULong;

import com.modlix.saas.commons2.util.StringUtil;

/**
 * Principal-C scope basis (J14 §5.2 / RETRIEVAL §4): on a scheduled/headless run the <b>campaign row is
 * the principal</b>, so the authorization scope is derived from it — never from a user. Carries the
 * campaign's {@code clientCode}, the {@code campaignId} it was minted for, and a coarse {@code scope}
 * label.
 *
 * <p><b>Not a JWT, not a SecurityContext.</b> This is deliberately just the scope <i>basis</i> a run is
 * pinned to (the design's {@code ServiceToken}); it does not fabricate a token. Its
 * {@link #authorizes(ULong)} guard makes it impossible for a context minted for one campaign to drive a
 * run against another, and {@link ScheduleService} additionally asserts the loaded campaign's client
 * equals {@link #clientCode()} before running — so a run can only ever touch the one campaign's client.
 * The real platform system-token / adzump-minted JWT that installs an actual authenticated context for
 * the EDIT-gated downstream services is the P4.5 issuer ({@link ServiceTokenMinter}); this record is the
 * seam it populates.
 */
public record ScopedContext(String clientCode, ULong campaignId, String scope) {

    /** The coarse scope label for a per-campaign optimization run. */
    public static final String CAMPAIGN_OPTIMIZE = "adzump:campaign:optimize";

    /** True iff this context was minted for {@code targetCampaignId} (identity match on the campaign). */
    public boolean authorizes(ULong targetCampaignId) {
        return this.campaignId != null && this.campaignId.equals(targetCampaignId);
    }

    /** True iff this context's client equals the campaign's own client (the only client a run may touch). */
    public boolean authorizesClient(String campaignClientCode) {
        return StringUtil.safeEquals(this.clientCode, campaignClientCode);
    }
}
