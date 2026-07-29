package com.modlix.saas.adzump.vertical;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.vertical.realestate.RealEstatePlaybook;

/**
 * Registry resolution + the generic fallback (safe minimums, no industry knowledge). Built by hand
 * from the playbook beans — no Spring context needed.
 */
class VerticalRegistryTest {

    private final GenericPlaybook generic = new GenericPlaybook();
    private final RealEstatePlaybook re = new RealEstatePlaybook();
    private final VerticalRegistry registry = new VerticalRegistry(List.of(generic, re));

    @Test
    void resolvesRealEstateByCode() {
        assertSame(re, registry.get("real_estate"));
    }

    @Test
    void unknownVerticalFallsBackToGeneric() {
        assertSame(generic, registry.get("aerospace_widgets"));
    }

    @Test
    void nullOrBlankCodeFallsBackToGeneric() {
        assertSame(generic, registry.get(null));
        assertSame(generic, registry.get("  "));
        assertSame(generic, registry.getOrDefault(null));
        assertSame(generic, registry.getOrDefault("aerospace_widgets"));
    }

    @Test
    void genericHasSafeMinimalSlots() {
        var slots = generic.requiredSlots(CampaignType.SEARCH);
        assertTrue(slots.containsAll(List.of(Slot.NAME, Slot.PRODUCT, Slot.OBJECTIVE, Slot.BUDGET,
                Slot.SCHEDULE, Slot.CREATIVES, Slot.AD_GROUPS)));
        // Generic stays neutral: no RE-specific geo/keyword requirement.
        assertFalse(slots.contains(Slot.GEO));
        assertFalse(slots.contains(Slot.KEYWORDS));
    }

    @Test
    void genericSwapsAssetGroupsForPmax() {
        var slots = generic.requiredSlots(CampaignType.PMAX);
        assertTrue(slots.contains(Slot.ASSET_GROUPS));
        assertFalse(slots.contains(Slot.AD_GROUPS));
    }

    @Test
    void genericHasNoHousingComplianceOrTaxonomyOrSeeds() {
        assertTrue(generic.complianceRules(Platform.META, CampaignType.LEADS).isEmpty());
        assertTrue(generic.attributeTaxonomy().axisNames().isEmpty());
        assertTrue(generic.seeds().interestSeeds().isEmpty());
        assertTrue(generic.seeds().keywordSeeds().isEmpty());
    }

    @Test
    void genericDefaultsAreNonNull() {
        var d = generic.defaults(CampaignType.LEADS);
        assertFalse(d.attributionWindow() == null || d.attributionWindow().isBlank());
        assertTrue(d.budgetMode() != null);
        assertTrue(d.objective() != null);
    }
}
