package com.modlix.saas.adzump.service.optimize;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;

/**
 * A caller-proposed candidate action, the body of {@code POST /plans/{id}/actions/propose} (A5's
 * {@code propose_action}, J12 §5). A5 uses this when it wants a genuinely new action J12's analyzers
 * did not surface: the engine runs it through the <b>same</b> {@link SignificanceGate} + {@link Objective}
 * as an analyzer-born candidate and returns it gated or suppressed — never around the gates.
 *
 * <p>The four core fields mirror the contract ({@code type}, {@code target}/{@code targetId},
 * {@code change}, {@code rationale}); {@code expectedDelta}/{@code confidence}/{@code risk} are optional
 * hints the engine defaults when absent. The statistical context the gate judges on (volume, maturity,
 * whether it kills a converter) is <b>not</b> trusted from the caller — the engine derives it from the
 * target's row in the latest snapshot, so a caller cannot talk an action past the gate.
 *
 * @param type          the action type (one of the seven J12 verbs).
 * @param target        the ad-grain the change is aimed at (accepts the wire alias {@code targetId}).
 * @param change        the typed change payload as JSON; deserialized per {@link #type} into the
 *                      matching {@link ActionChange} subtype for the echoed proposal (never applied in P3).
 * @param rationale     the caller's human-readable "why".
 * @param expectedDelta optional objective-delta hint (defaults to 0.0 — a caller-proposed delta is not
 *                      trusted for ranking a single proposal).
 * @param confidence    optional caller confidence 0..1 (defaults to 0.6).
 * @param risk          optional blast-radius hint (defaults to {@link Risk#MED}).
 */
public record ProposedAction(
        AdzumpActionAuditActionType type,
        @JsonAlias("targetId") AdGrainId target,
        JsonNode change,
        String rationale,
        Double expectedDelta,
        Double confidence,
        Risk risk) {
}
