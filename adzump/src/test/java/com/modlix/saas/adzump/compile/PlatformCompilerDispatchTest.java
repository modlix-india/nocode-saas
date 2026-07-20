package com.modlix.saas.adzump.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.platform.CompiledCampaign;
import com.modlix.saas.adzump.platform.PlatformCompiler;

/**
 * Dispatch tests for the platform {@link PlatformCompiler}s: they resolve the right
 * {@link TypeCompiler} by the plan's per-platform campaign type, and a not-yet-implemented type fails
 * loudly (the {@link UnsupportedTypeCompiler} scaffold) rather than silently. Also covers the P1
 * {@link PlatformCompilerRegistry} lookup J8 uses.
 */
class PlatformCompilerDispatchTest {

    private final MetaCompiler metaCompiler = new MetaCompiler(new MetaLeadsCompiler());
    private final GoogleCompiler googleCompiler = new GoogleCompiler(new GoogleSearchCompiler());

    @Test
    void metaCompilerDispatchesLeadsOnTheMetaCampaignType() {
        CompiledCampaign compiled = this.metaCompiler.compile(
                CompileFixtures.reSearchAndMetaLeads(), CompileFixtures.metaConfig());
        assertEquals(Platform.META, compiled.platform());
        assertEquals(CampaignType.LEADS, compiled.type());
    }

    @Test
    void googleCompilerDispatchesSearchOnTheGoogleCampaignType() {
        CompiledCampaign compiled = this.googleCompiler.compile(
                CompileFixtures.reSearchAndMetaLeads(), CompileFixtures.googleConfig());
        assertEquals(Platform.GOOGLE, compiled.platform());
        assertEquals(CampaignType.SEARCH, compiled.type());
    }

    @Test
    void unimplementedMetaTypeFailsLoudly() {
        CampaignPlan plan = CompileFixtures.reSearchAndMetaLeads();
        plan.setCampaignTypes(Map.of(Platform.META, CampaignType.SALES)); // scaffolded, not built

        assertThrows(UnsupportedOperationException.class,
                () -> this.metaCompiler.compile(plan, CompileFixtures.metaConfig()));
    }

    @Test
    void unimplementedGoogleTypeFailsLoudly() {
        CampaignPlan plan = CompileFixtures.reSearchAndMetaLeads();
        plan.setCampaignTypes(Map.of(Platform.GOOGLE, CampaignType.PMAX)); // scaffolded, not built

        assertThrows(UnsupportedOperationException.class,
                () -> this.googleCompiler.compile(plan, CompileFixtures.googleConfig()));
    }

    @Test
    void planNotTargetingThePlatformThrows() {
        CampaignPlan googleOnly = CompileFixtures.reSearchAndMetaLeads();
        googleOnly.setCampaignTypes(Map.of(Platform.GOOGLE, CampaignType.SEARCH));

        assertThrows(IllegalStateException.class,
                () -> this.metaCompiler.compile(googleOnly, CompileFixtures.metaConfig()));
    }

    @Test
    void registryIndexesCompilersByPlatform() {
        PlatformCompilerRegistry registry = new PlatformCompilerRegistry(
                List.of(this.metaCompiler, this.googleCompiler));

        assertSame(this.metaCompiler, registry.get(Platform.META));
        assertSame(this.googleCompiler, registry.get(Platform.GOOGLE));
        assertTrue(registry.has(Platform.GOOGLE));
    }
}
