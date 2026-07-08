package com.modlix.saas.adzump.service.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jooq.types.ULong;
import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;

/**
 * J14 §5.2 principal-C minter unit tests (offline): the scope basis ({@link ServiceTokenMinter#mint}) and
 * the authenticated context ({@link ServiceTokenMinter#authenticate}) a headless scheduled run is pinned
 * to and installed under — a NON-SYS, {@code isAuthenticated} {@link ContextAuthentication} whose
 * effective client is the campaign's own and whose {@link com.modlix.saas.commons2.security.jwt.ContextUser}
 * carries the EDIT authority.
 */
class ServiceTokenMinterTest {

    private static final ULong CID = ULong.valueOf(100);
    private static final ULong OTHER = ULong.valueOf(200);
    private static final String CLIENT = "CLI0";
    private static final String OTHER_CLIENT = "CLI9";

    private final ServiceTokenMinter minter = new ServiceTokenMinter();

    private static CampaignPlan campaign(ULong id, String client) {
        CampaignPlan plan = new CampaignPlan().setClientCode(client);
        plan.setId(id);
        return plan;
    }

    @Test
    void mint_scopeBasisPinnedToCampaignAndClient() {
        ScopedContext ctx = this.minter.mint(campaign(CID, CLIENT));

        assertEquals(CLIENT, ctx.clientCode());
        assertEquals(ScopedContext.CAMPAIGN_OPTIMIZE, ctx.scope());
        assertTrue(ctx.authorizes(CID));
        assertFalse(ctx.authorizes(OTHER), "a context minted for campaign A must not authorize campaign B");
        assertTrue(ctx.authorizesClient(CLIENT));
        assertFalse(ctx.authorizesClient(OTHER_CLIENT), "the context is pinned to the campaign's own client");
    }

    @Test
    void authenticate_buildsNonSystemAuthenticatedCampaignScopedContext() {
        ContextAuthentication ca = this.minter.authenticate(campaign(CID, CLIENT));

        // Authenticated, but deliberately NOT the system client — so the downstream tenant checks apply.
        assertTrue(ca.isAuthenticated());
        assertFalse(ca.isSystemClient(), "scheduled principal must not be the system client (tenant checks must apply)");
        assertNotEquals(ContextAuthentication.CLIENT_TYPE_SYSTEM, ca.getClientTypeCode());
        assertNotNull(ca.getClientTypeCode());

        // Effective client is the campaign's own; app code is adzump (for the header-only Core connection call).
        assertEquals(CLIENT, ca.getLoggedInFromClientCode());
        assertEquals(CLIENT, ca.getClientCode());
        assertEquals(ServiceTokenMinter.APP_CODE, ca.getUrlAppCode());
        assertEquals(ServiceTokenMinter.APP_CODE, ca.getVerifiedAppCode());
        assertEquals("adzump", ca.getUrlAppCode());

        // The EDIT authority lives on the ContextUser as the exact stored string (prefix included).
        assertNotNull(ca.getUser());
        assertTrue(ca.getUser().getStringAuthorities().contains("Authorities.Campaign_MANAGE"));
        assertEquals(ServiceTokenMinter.SCHEDULER_USER_NAME, ca.getUser().getUserName());

        // Non-null user flags mirroring the security service anonymous-user construction.
        assertTrue(ca.getUser().isAccountNonExpired());
        assertTrue(ca.getUser().isAccountNonLocked());
        assertTrue(ca.getUser().isCredentialsNonExpired());
    }

    @Test
    void authenticate_effectiveClientTracksTheGivenCampaignsClient() {
        // Two different campaigns produce contexts scoped to their OWN clients — never a shared/user client.
        ContextAuthentication a = this.minter.authenticate(campaign(CID, CLIENT));
        ContextAuthentication b = this.minter.authenticate(campaign(OTHER, OTHER_CLIENT));

        assertEquals(CLIENT, a.getLoggedInFromClientCode());
        assertEquals(OTHER_CLIENT, b.getLoggedInFromClientCode());
        assertNotEquals(a.getLoggedInFromClientCode(), b.getLoggedInFromClientCode());
    }
}
