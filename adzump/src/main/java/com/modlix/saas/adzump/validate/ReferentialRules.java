package com.modlix.saas.adzump.validate;

import static com.modlix.saas.adzump.validate.ValidationSupport.isBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.modlix.saas.adzump.dto.ValidationIssue;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.Ad;
import com.modlix.saas.adzump.model.AdGroup;
import com.modlix.saas.adzump.model.AssetGroup;
import com.modlix.saas.adzump.model.Audiences;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.LeadForm;
import com.modlix.saas.adzump.model.Links;

/**
 * Layer 2 — <b>referential</b>. Two independent concerns, both server-side so id-honesty holds even
 * outside the agent path:
 * <ol>
 *   <li><b>Fetched-id honesty</b> — every platform id the plan references (account, page, pixel,
 *       audience, lead-form) must be in the session's fetched-id set (mirrors A1 §5.4: the agent may not
 *       invent ids). <b>P0 stub:</b> when {@code ctx} carries no fetched-id set the membership check is
 *       skipped ({@link ValidationContext#referentialPermissive()}) — TODO(A1 gate) wires the real
 *       session registry.</li>
 *   <li><b>Internal consistency</b> — every ad group / asset group / lead form targets a platform the
 *       plan actually campaigns on ({@code campaignTypes}); an ad's {@code leadFormId} matches the plan's
 *       lead form. (The account-level "ad set's account matches the campaign" check activates once the IR
 *       carries per-group account ids; the IR holds account only at {@code links.*}.)</li>
 * </ol>
 * All findings are {@code ERROR}.
 */
public final class ReferentialRules {

    public static final String REF_UNKNOWN_ID = "REF_UNKNOWN_ID";
    public static final String REF_PLATFORM_MISMATCH = "REF_PLATFORM_MISMATCH";
    public static final String REF_LEADFORM_UNKNOWN = "REF_LEADFORM_UNKNOWN";

    private ReferentialRules() {
    }

    private record Ref(String path, String field, String id) {
    }

    public static List<ValidationIssue> check(CampaignPlan plan, ValidationContext ctx) {

        List<ValidationIssue> issues = new ArrayList<>();

        CampaignPlanBody body = plan == null ? null : plan.getBody();
        if (body == null)
            return issues; // structural layer already flags an absent body

        // ---- (1) fetched-id honesty (skipped when permissive; TODO A1 gate) ----
        if (!ctx.referentialPermissive()) {
            Set<String> fetched = ctx.fetchedIds();
            for (Ref ref : gatherIds(body))
                if (!isBlank(ref.id()) && !fetched.contains(ref.id()))
                    issues.add(ValidationIssue.error(REF_UNKNOWN_ID, ref.path(), ref.field(),
                            "id '" + ref.id() + "' was not fetched from the connected account this session"));
        }

        // ---- (2) internal consistency ----
        Set<Platform> targeted = (plan.getCampaignTypes() == null)
                ? Set.of()
                : plan.getCampaignTypes().keySet();

        List<AdGroup> adGroups = body.getAdGroups();
        if (adGroups != null)
            for (int i = 0; i < adGroups.size(); i++) {
                AdGroup ag = adGroups.get(i);
                if (ag != null && ag.getPlatform() != null && !targeted.contains(ag.getPlatform()))
                    issues.add(ValidationIssue.error(REF_PLATFORM_MISMATCH,
                            "/body/adGroups/" + i + "/platform", "platform",
                            "ad group targets platform " + ag.getPlatform() + " which the plan does not campaign on"));
            }

        List<AssetGroup> assetGroups = body.getAssetGroups();
        if (assetGroups != null)
            for (int i = 0; i < assetGroups.size(); i++) {
                AssetGroup g = assetGroups.get(i);
                if (g != null && g.getPlatform() != null && !targeted.contains(g.getPlatform()))
                    issues.add(ValidationIssue.error(REF_PLATFORM_MISMATCH,
                            "/body/assetGroups/" + i + "/platform", "platform",
                            "asset group targets platform " + g.getPlatform()
                                    + " which the plan does not campaign on"));
            }

        LeadForm leadForm = body.getLeadForm();
        if (leadForm != null && leadForm.getPlatform() != null && !targeted.contains(leadForm.getPlatform()))
            issues.add(ValidationIssue.error(REF_PLATFORM_MISMATCH, "/body/leadForm/platform", "platform",
                    "lead form targets platform " + leadForm.getPlatform()
                            + " which the plan does not campaign on"));

        // ad.leadFormId must match the plan's lead form id
        String planFormId = leadForm == null ? null : leadForm.getId();
        if (adGroups != null)
            for (int i = 0; i < adGroups.size(); i++) {
                AdGroup ag = adGroups.get(i);
                if (ag == null || ag.getAds() == null)
                    continue;
                for (int j = 0; j < ag.getAds().size(); j++) {
                    Ad ad = ag.getAds().get(j);
                    if (ad != null && !isBlank(ad.getLeadFormId())
                            && !ad.getLeadFormId().equals(planFormId))
                        issues.add(ValidationIssue.error(REF_LEADFORM_UNKNOWN,
                                "/body/adGroups/" + i + "/ads/" + j + "/leadFormId", "leadFormId",
                                "ad references lead form id '" + ad.getLeadFormId()
                                        + "' that is not the plan's lead form"));
                }
            }

        return issues;
    }

    /** Every platform-id-bearing field in the plan, paired with its JSON pointer. */
    private static List<Ref> gatherIds(CampaignPlanBody body) {

        List<Ref> refs = new ArrayList<>();

        // Only ids that MUST denote a pre-existing platform resource the agent could know solely by
        // fetching are membership-checked: ad account, page, pixel, and audience ids. NOT campaignId
        // (an OUTPUT written back to links post-launch) and NOT leadForm.id (a form the plan may create);
        // Ad.leadFormId is an internal reference checked separately in check().
        Links links = body.getLinks();
        if (links != null) {
            if (links.getGoogle() != null)
                add(refs, "/body/links/google/adAccountId", "adAccountId", links.getGoogle().getAdAccountId());
            if (links.getMeta() != null) {
                add(refs, "/body/links/meta/adAccountId", "adAccountId", links.getMeta().getAdAccountId());
                add(refs, "/body/links/meta/pageId", "pageId", links.getMeta().getPageId());
                add(refs, "/body/links/meta/pixelId", "pixelId", links.getMeta().getPixelId());
            }
        }

        List<AdGroup> adGroups = body.getAdGroups();
        if (adGroups != null)
            for (int i = 0; i < adGroups.size(); i++) {
                AdGroup ag = adGroups.get(i);
                if (ag == null || ag.getTargeting() == null)
                    continue;
                addAudiences(refs, "/body/adGroups/" + i + "/targeting/audiences",
                        ag.getTargeting().getAudiences());
            }

        List<AssetGroup> assetGroups = body.getAssetGroups();
        if (assetGroups != null)
            for (int i = 0; i < assetGroups.size(); i++) {
                AssetGroup g = assetGroups.get(i);
                if (g == null || g.getAudienceSignals() == null)
                    continue;
                addAudiences(refs, "/body/assetGroups/" + i + "/audienceSignals", g.getAudienceSignals());
            }

        return refs;
    }

    private static void addAudiences(List<Ref> refs, String base, Audiences a) {
        if (a == null)
            return;
        addEach(refs, base + "/customAudienceIds", "customAudienceIds", a.getCustomAudienceIds());
        addEach(refs, base + "/lookalikeIds", "lookalikeIds", a.getLookalikeIds());
        addEach(refs, base + "/excludedIds", "excludedIds", a.getExcludedIds());
    }

    private static void addEach(List<Ref> refs, String base, String field, List<String> ids) {
        if (ids == null)
            return;
        for (int i = 0; i < ids.size(); i++)
            add(refs, base + "/" + i, field, ids.get(i));
    }

    private static void add(List<Ref> refs, String path, String field, String id) {
        if (!isBlank(id))
            refs.add(new Ref(path, field, id));
    }
}
