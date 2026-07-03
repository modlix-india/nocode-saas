package com.modlix.saas.adzump.vertical;

import java.util.List;
import java.util.Set;

import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;

/**
 * The vertical-agnostic seam: the single home for everything that varies by industry, behind one
 * interface, so the rest of the system stays vertical-neutral. Adding a vertical = add a
 * {@code VerticalPlaybook} bean; nothing else changes.
 *
 * <p>The chosen vertical is <b>deduced by A2</b> (product study) and stored on the plan/product — it is
 * not a UI dropdown (the UI is vertical-neutral). Every consumer resolves
 * {@code registry.get(plan.vertical)}.
 *
 * <p>Consumers: A1 (completeness rail, via J6), J6 (validate), J7 (effective defaults), A3 (rubric +
 * seeds), J20 (attribute taxonomy), J1 / MilestoneMapping (milestone keys).
 */
public interface VerticalPlaybook {

    /** Stable vertical code, e.g. {@code "real_estate"}, {@code "generic"}. */
    String code();

    /**
     * The slots a plan of this {@link CampaignType} must fill to be launch-ready. Type-aware: a SEARCH
     * plan needs geo + keywords; a PMAX plan needs asset groups + audience signals, not keywords — so
     * neither the rail nor J6 hardcodes a structure.
     */
    Set<Slot> requiredSlots(CampaignType type);

    /**
     * The vertical default policy for this campaign type (attribution window, budget mode, bidding,
     * objective mapping). J7 resolves campaign-override &rarr; account-default &rarr; this.
     */
    PolicyDefaults defaults(CampaignType type);

    /**
     * Compliance rules that apply for this platform + campaign type, e.g. Meta HOUSING for real-estate.
     * Consumed by J6 (enforce) and J3/J4 (stamp the special-ad-category on the payload).
     */
    List<ComplianceRule> complianceRules(Platform p, CampaignType type);

    /** The creative-attribute taxonomy (J20) for this vertical. Empty for generic. */
    AttributeTaxonomy attributeTaxonomy();

    /** The critic rubric (A3) this vertical scores plan quality against. */
    CriticRubric criticRubric();

    /**
     * The vertical's funnel vocabulary (e.g. RE: {@code lead, qualified, site_visit, booking}). Not a
     * fixed enum — another vertical names its funnel differently; MilestoneMapping maps live leadzump
     * stages onto these keys.
     */
    List<String> milestoneKeys();

    /** Interest/keyword seed sets A3/J3/J4 expand from. */
    TargetingSeeds seeds();
}
