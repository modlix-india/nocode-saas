package com.modlix.saas.adzump.service.schedule;

import java.math.BigInteger;
import java.util.List;

import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.jwt.ContextUser;

/**
 * Principal-C minter (J14 §5.2) — the injectable seam for the no-user scheduled run. It produces both
 * halves of the principal-C basis:
 *
 * <ol>
 * <li>{@link #mint(CampaignPlan)} &rarr; a {@link ScopedContext}: the coarse <b>scope basis</b> a run is
 * pinned to (clientCode + campaignId + scope). It is not a token and not a {@code SecurityContext}; its
 * {@link ScopedContext#authorizes(org.jooq.types.ULong)} / {@link ScopedContext#authorizesClient(String)}
 * guards make it impossible for a context minted for one campaign to drive a run against another.</li>
 * <li>{@link #authenticate(CampaignPlan)} &rarr; a fully-formed {@link ContextAuthentication}: the actual
 * authenticated principal {@link ScheduleService} installs on the running thread so the EDIT-gated
 * downstream services ({@code FeedbackService} / {@code OptimizationEngine} / {@code ActionApplier}) and
 * the {@code ca}-reads ({@code loggedInFromClientCode} / {@code urlAppCode}, e.g.
 * {@code ConnectionService.resolve}) resolve under the campaign's own client with no user present.</li>
 * </ol>
 *
 * <p><b>Scope guarantee.</b> The campaign's own {@code clientCode} is the only client the resulting run
 * may touch; the campaign row is the principal. The context is deliberately <b>NON-SYS</b>
 * ({@link ContextAuthentication#isSystemClient()} stays {@code false}) so the downstream tenant checks
 * still apply — a scheduled run is not a super-user.
 *
 * <p><b>Same-JVM only.</b> The installed {@link ContextAuthentication} authorizes an <b>in-process</b>
 * headless run (both the {@code @PreAuthorize} authority match and the {@code ca}-reads). It carries no
 * {@code accessToken}. A LIVE run that reads the leadzump CRM through the entity-processor still needs a
 * forwardable bearer for that EP call — mint a service JWT via the platform issuer vs an EP header-only
 * internal endpoint — which is a <b>P4.5 decision (J11 §9)</b>, deferred because the EP leadzump CRM read
 * endpoint is not built yet (J11 TODO). Do not add the {@code accessToken} here for that until J11 lands.
 */
@Service
public class ServiceTokenMinter {

    /** The adzump app code — the {@code urlAppCode}/{@code verifiedAppCode} a headless run authorizes under. */
    public static final String APP_CODE = "adzump";

    /** Sentinel service-identity username stamped on the principal-C {@link ContextUser}. */
    public static final String SCHEDULER_USER_NAME = "_adzump_scheduler";

    /** The single EDIT authority the downstream loop services gate on (exact string match, incl. prefix). */
    public static final String CAMPAIGN_MANAGE = "Authorities.Campaign_MANAGE";

    /** Sentinel id for the no-user scheduled principal (mirrors the security anonymous user's {@code ZERO} id). */
    private static final BigInteger SCHEDULER_USER_ID = BigInteger.ZERO;

    /**
     * A concrete <b>NON-SYS</b> client type for the scheduled context. Any non-{@code "SYS"} value keeps
     * {@link ContextAuthentication#isSystemClient()} {@code false} so the tenant checks still apply;
     * {@code "BUS"} is the platform's ordinary (business/tenant) client type — never the system type.
     */
    private static final String SCHEDULER_CLIENT_TYPE = "BUS";

    /**
     * Mints the campaign-scoped scope basis. The campaign's own {@code clientCode} is the only client the
     * resulting run may touch; {@code campaignId} pins it to exactly this campaign.
     */
    public ScopedContext mint(CampaignPlan campaign) {
        return new ScopedContext(campaign.getClientCode(), campaign.getId(), ScopedContext.CAMPAIGN_OPTIMIZE);
    }

    /**
     * Builds the authenticated principal-C {@link ContextAuthentication} for a headless scheduled run of
     * {@code campaign}: a {@link ContextUser} carrying the sentinel service identity + the EDIT authority +
     * the non-null user flags (mirroring the security service anonymous-user construction), wrapped in an
     * {@code isAuthenticated} context whose effective client is the campaign's own client and whose app
     * code is {@link #APP_CODE}. NON-SYS by construction, so the downstream tenant checks still apply.
     *
     * <p>{@code clientId} is left unset on the {@link ContextUser}: the {@link CampaignPlan} carries only
     * the client <i>code</i> (not a numeric id), and the scheduled loop never reads
     * {@code ca.getUser().getClientId()} — the effective client flows explicitly as the campaign
     * {@code clientCode} through the loop, not via a user-client lookup.
     */
    public ContextAuthentication authenticate(CampaignPlan campaign) {

        String clientCode = campaign.getClientCode();

        ContextUser user = new ContextUser()
                .setId(SCHEDULER_USER_ID)
                .setUserName(SCHEDULER_USER_NAME)
                .setFirstName("Adzump")
                .setLastName("Scheduler")
                .setLocaleCode("en")
                .setAccountNonExpired(true)
                .setAccountNonLocked(true)
                .setCredentialsNonExpired(true)
                .setStringAuthorities(List.of(CAMPAIGN_MANAGE));

        ContextAuthentication ca = new ContextAuthentication();
        ca.setUser(user);
        ca.setAuthenticated(true);
        ca.setLoggedInFromClientCode(clientCode);
        ca.setClientCode(clientCode);
        ca.setUrlClientCode(clientCode);
        ca.setClientTypeCode(SCHEDULER_CLIENT_TYPE);
        ca.setUrlAppCode(APP_CODE);
        ca.setVerifiedAppCode(APP_CODE);
        return ca;
    }
}
