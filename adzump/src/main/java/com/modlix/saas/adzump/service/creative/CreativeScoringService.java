package com.modlix.saas.adzump.service.creative;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.dao.CreativeAttributeDao;
import com.modlix.saas.adzump.dto.CreativeAttributeRow;
import com.modlix.saas.adzump.model.Creative;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.feedback.FeedbackService;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.StringUtil;

/**
 * J20 §5.5 — the creative-intelligence service seam. Exposes the three reads A4/J12/J21 use (the
 * creative score, the account attribute map, the pre-spend predict gate) and the authorized
 * (re)compute/persist path.
 *
 * <p><b>Security &amp; tenancy.</b> Reads carry <b>no {@code @PreAuthorize}</b> and are tenant-scoped at
 * runtime: {@link #getScore} rides the plan's managed-client gate via {@link FeedbackService}, and
 * {@link #getAttributeMap}/{@link #predict} resolve an effective client (own by default; a differing
 * target only for the system client or a managing client) so <b>the attribute map is tenant-private</b> —
 * one account's attribute wins never leak to another (only J19 market priors are shared). The
 * (re)compute/persist path carries {@code EDIT}, mirroring {@code FeedbackService.getSnapshot}.
 */
@Service
public class CreativeScoringService {

    private static final String EDIT = "hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')";

    private static final String CLIENT = "client";
    private static final String CREATIVE_ID = "creativeId";

    private final CreativeScorer creativeScorer;
    private final AttributeAttributor attributor;
    private final CreativePredictor predictor;
    private final CreativeAttributeDao creativeAttributeDao;
    private final FeedbackService feedbackService;
    private final FeignAuthenticationService securityService;
    private final AdzumpMessageResourceService msgService;

    public CreativeScoringService(CreativeScorer creativeScorer, AttributeAttributor attributor,
            CreativePredictor predictor, CreativeAttributeDao creativeAttributeDao, FeedbackService feedbackService,
            FeignAuthenticationService securityService, AdzumpMessageResourceService msgService) {

        this.creativeScorer = creativeScorer;
        this.attributor = attributor;
        this.predictor = predictor;
        this.creativeAttributeDao = creativeAttributeDao;
        this.feedbackService = feedbackService;
        this.securityService = securityService;
        this.msgService = msgService;
    }

    /**
     * The creative-grain score for {@code creativeId} within {@code campaignId}, off the latest snapshot.
     * No {@code @PreAuthorize}: tenant-scoped by {@link FeedbackService#readLatest} (the plan's
     * managed-client gate). Returns a no-signal score when no snapshot has been built yet.
     */
    public CreativeScore getScore(ULong campaignId, String creativeId, String targetClientCode) {

        PerformanceSnapshot latest = this.feedbackService.readLatest(campaignId, targetClientCode);
        if (latest == null)
            return CreativeScore.empty(creativeId);

        return this.creativeScorer.score(creativeId, latest);
    }

    /**
     * The account's attribute performance map for {@code vertical} over {@code window} (the exploit/
     * explore surface). No {@code @PreAuthorize}: tenant-private, scoped to the resolved effective client.
     */
    public AttributeAttribution getAttributeMap(String vertical, SnapshotWindow window, String targetClientCode) {

        String clientCode = this.resolveEffectiveClientCode(targetClientCode, ca());
        return this.attributor.attribute(clientCode, vertical, window);
    }

    /**
     * The pre-spend predict gate: scores {@code draft} against the account's current attribute map. No
     * {@code @PreAuthorize} (a read A4 calls before a creative enters a launchable plan); tenant-private.
     */
    public CreativePrediction predict(Creative draft, String vertical, SnapshotWindow window,
            String targetClientCode) {

        AttributeAttribution attribution = this.getAttributeMap(vertical, window, targetClientCode);
        return this.predictor.predict(draft, attribution);
    }

    /** Predicts against an already-resolved (tenant-scoped) map, for a caller that holds one. */
    public CreativePrediction predict(Creative draft, AttributeAttribution attribution) {
        return this.predictor.predict(draft, attribution);
    }

    /**
     * Persists a tagged creative's {@code axis -> value} attributes into {@code adzump_creative_attribute}
     * — the durable write that keeps the account's living attribute map current. Mutating &rarr;
     * {@code EDIT}. Client scope is pinned to the resolved effective client, never trusted from a body.
     * Returns the persisted rows.
     */
    @PreAuthorize(EDIT)
    public List<CreativeAttributeRow> recordCreativeAttributes(String creativeId, Creative creative,
            String targetClientCode) {

        if (creativeId == null || creativeId.isBlank())
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, CREATIVE_ID);

        String clientCode = this.resolveEffectiveClientCode(targetClientCode, ca());

        Map<String, String> attributes = creative == null ? null : creative.getAttributes();
        List<CreativeAttributeRow> rows = new ArrayList<>();
        if (attributes != null) {
            for (Map.Entry<String, String> attr : attributes.entrySet()) {
                if (attr.getKey() == null || attr.getKey().isBlank()
                        || attr.getValue() == null || attr.getValue().isBlank())
                    continue;
                rows.add(new CreativeAttributeRow()
                        .setClientCode(clientCode)
                        .setCreativeId(creativeId)
                        .setAxis(attr.getKey())
                        .setValue(attr.getValue()));
            }
        }

        this.creativeAttributeDao.replaceForCreative(clientCode, creativeId, rows);
        return this.creativeAttributeDao.findByCreativeId(creativeId);
    }

    /**
     * The authorized (re)compute trigger for {@code vertical} over {@code window} — the loop's scheduler
     * or an Owner refreshing the map. Mutating authority ({@code EDIT}/system) even though the numeric
     * decomposition is computed on demand: the recompute is the point at which the account's living map
     * is re-derived.
     *
     * <p>TODO(P4): persist the numeric aggregates (lift/volume/confidence/junk-correlation) once the
     * schema carries columns for them. The current {@code adzump_creative_attribute} table stores only
     * the {@code axis -> value} assignments (the substrate); P3 recomputes the map from that substrate +
     * snapshots each call and returns it in memory (no schema change in P3).
     */
    @PreAuthorize(EDIT)
    public AttributeAttribution recompute(String vertical, SnapshotWindow window, String targetClientCode) {

        String clientCode = this.resolveEffectiveClientCode(targetClientCode, ca());
        return this.attributor.attribute(clientCode, vertical, window);
    }

    // ==============================================================================================

    private static ContextAuthentication ca() {
        return SecurityContextUtil.getUsersContextAuthentication();
    }

    /**
     * Resolves the effective client code, mirroring
     * {@code AutonomyConfigService.resolveEffectiveClientCode} / {@code FilesAccessPathService}. Defaults
     * to the caller's own client; a differing target is allowed only for the system client or a managing
     * client administering it.
     */
    private String resolveEffectiveClientCode(String targetClientCode, ContextAuthentication ca) {

        String own = ca.getLoggedInFromClientCode();

        if (targetClientCode == null || targetClientCode.isBlank()
                || StringUtil.safeEquals(targetClientCode.trim(), own))
            return own;

        String target = targetClientCode.trim();
        BigInteger targetClientId = this.securityService.getClientIdByCode(target);

        boolean allowed = ca.isSystemClient()
                || Boolean.TRUE.equals(this.securityService.isUserClientManageClient(ca.getUrlAppCode(),
                        ca.getUser().getId(), ca.getUser().getClientId(), targetClientId));

        if (!allowed)
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.FORBIDDEN_PERMISSION, CLIENT);

        return target;
    }
}
