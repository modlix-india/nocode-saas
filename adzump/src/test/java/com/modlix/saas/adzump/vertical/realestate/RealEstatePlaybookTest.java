package com.modlix.saas.adzump.vertical.realestate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.enums.SpecialAdCategory;
import com.modlix.saas.adzump.vertical.ComplianceRule;
import com.modlix.saas.adzump.vertical.PolicyDefaults;
import com.modlix.saas.adzump.vertical.Slot;

/**
 * Pure (no Spring) tests for the real-estate playbook: required-slots differ by {@link CampaignType},
 * defaults differ by type, HOUSING compliance is present, the RE attribute taxonomy + milestone keys
 * + seeds carry the ported legacy content.
 */
class RealEstatePlaybookTest {

    private final RealEstatePlaybook re = new RealEstatePlaybook();

    @Test
    void codeIsRealEstate() {
        assertEquals("real_estate", re.code());
    }

    @Test
    void searchRequiresKeywordsAndGeoNotAssetGroups() {
        var slots = re.requiredSlots(CampaignType.SEARCH);
        assertTrue(slots.containsAll(List.of(Slot.GEO, Slot.KEYWORDS, Slot.AD_GROUPS, Slot.CREATIVES)));
        assertFalse(slots.contains(Slot.ASSET_GROUPS));
    }

    @Test
    void leadsRequiresLeadFormNotKeywords() {
        var slots = re.requiredSlots(CampaignType.LEADS);
        assertTrue(slots.containsAll(List.of(Slot.GEO, Slot.LEAD_FORM, Slot.CREATIVES)));
        assertFalse(slots.contains(Slot.KEYWORDS));
    }

    @Test
    void pmaxRequiresAssetGroupsAndAudienceNotKeywords() {
        var slots = re.requiredSlots(CampaignType.PMAX);
        assertTrue(slots.containsAll(List.of(Slot.ASSET_GROUPS, Slot.AUDIENCE, Slot.CREATIVES)));
        assertFalse(slots.contains(Slot.KEYWORDS));
        assertFalse(slots.contains(Slot.AD_GROUPS));
    }

    @Test
    void requiredSlotsDifferBySearchVsPmax() {
        assertNotEquals(re.requiredSlots(CampaignType.SEARCH), re.requiredSlots(CampaignType.PMAX));
    }

    @Test
    void defaultsDifferByType() {
        PolicyDefaults search = re.defaults(CampaignType.SEARCH);
        PolicyDefaults leads = re.defaults(CampaignType.LEADS);
        PolicyDefaults pmax = re.defaults(CampaignType.PMAX);

        assertEquals("30d_click", search.attributionWindow());
        assertEquals("7d_click_1d_view", leads.attributionWindow());
        assertNotEquals(search.attributionWindow(), leads.attributionWindow());

        assertEquals(PolicyDefaults.BudgetMode.AD_SET, leads.budgetMode());
        assertEquals(PolicyDefaults.BudgetMode.CAMPAIGN, pmax.budgetMode());
    }

    @Test
    void housingComplianceIsPresentForLeadGen() {
        List<ComplianceRule> rules = re.complianceRules(Platform.META, CampaignType.LEADS);
        assertEquals(1, rules.size());
        ComplianceRule housing = rules.getFirst();
        assertEquals(SpecialAdCategory.HOUSING, housing.category());
        assertTrue(housing.disclaimerRequired());
        assertFalse(housing.restrictedTargeting().isEmpty());
    }

    @Test
    void taxonomyCarriesReAxesAndValues() {
        var tax = re.attributeTaxonomy();
        assertTrue(tax.axisNames().containsAll(List.of("angle", "scene", "offer", "cta")));
        assertTrue(tax.knows("angle", "investment_roi"));
        assertTrue(tax.knows("offer", "pre_launch_price"));
        assertFalse(tax.knows("angle", "not_a_real_angle"));
    }

    @Test
    void milestoneKeysAreTheReFunnel() {
        assertEquals(List.of("lead", "qualified", "site_visit", "booking"), re.milestoneKeys());
    }

    @Test
    void seedsPortLegacyKeywordsAndInterests() {
        var seeds = re.seeds();
        assertTrue(seeds.keywordSeeds().contains("villa"));   // legacy _REAL_ESTATE_KEYWORDS
        assertTrue(seeds.keywordSeeds().contains("realty"));
        assertFalse(seeds.interestSeeds().isEmpty());
    }
}
