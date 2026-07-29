package com.modlix.saas.adzump.service.optimize;

import java.util.List;
import java.util.Map;

import com.modlix.saas.adzump.enums.MatchType;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;

/**
 * The typed change payload of an {@link Action} (J12 §5.1) — one record per action type, so a
 * consumer (the approval rail, A5's narration, J13's apply) reads a strongly-typed change, not an
 * untyped bag. Recommend-mode only: these describe <b>what would change</b>; nothing is applied in P3.
 */
public sealed interface ActionChange
        permits ActionChange.BudgetShift, ActionChange.BidChange, ActionChange.AudienceRefinement,
        ActionChange.NegativeKeyword, ActionChange.Pause, ActionChange.CreativeRotation,
        ActionChange.VariantRequest {

    /**
     * SHIFT_BUDGET — move spend from a low-{@code blendedScore} grain to a high one, within the caps
     * (J12 §5.2 budget). The {@code amount} is the money to move per run; {@code pctOfSource} is that
     * amount as a fraction of the source grain's window spend (bounded by
     * {@code maxBudgetChangePctPerRun}).
     */
    record BudgetShift(AdGrainId fromGrain, AdGrainId toGrain, Money amount, double pctOfSource)
            implements ActionChange {
    }

    /**
     * ADJUST_BID — raise or lower the target cost-per-outcome for a grain vs its measured cost
     * (J12 §5.2 bid). {@code direction} is {@code "RAISE"} or {@code "LOWER"}.
     */
    record BidChange(String direction, Money currentTargetCpa, Money proposedTargetCpa, String strategyHint)
            implements ActionChange {
    }

    /**
     * REFINE_AUDIENCE — narrow / exclude / expand a targeting segment (J12 §5.2 audience). Under a
     * HOUSING special-ad-category the demographic dimensions are locked, so only interest / placement /
     * geo refinements are ever proposed. {@code operation} is {@code "NARROW"|"EXCLUDE"|"EXPAND"}.
     */
    record AudienceRefinement(String operation, String dimension, String detail) implements ActionChange {
    }

    /**
     * ADD_NEGATIVE_KEYWORD — the cheapest, safest Google action: exclude wasteful search traffic
     * (J12 §5.2 keyword). {@code terms} may be empty in P3 when the exact wasteful terms are resolved
     * from the search-term report at apply time (the snapshot carries no search-term grain yet — see
     * {@link KeywordAnalyzer}); {@code basis} records why the ad group was flagged.
     */
    record NegativeKeyword(MatchType matchType, List<String> terms, String basis) implements ActionChange {
    }

    /**
     * PAUSE_ENTITY — pause an ad-grain. A pause of a <b>converter</b> ({@code kill = true}) is only
     * proposed on MATURE signal (the gate enforces it); a pause of obvious zero-outcome waste
     * ({@code kill = false}) is a low-risk trim allowed on fast signal. {@code reason} carries the
     * diagnosis.
     */
    record Pause(boolean kill, String reason) implements ActionChange {
    }

    /**
     * ROTATE_CREATIVE — rotate out a low-outcome creative for a fresh one (J12 §5.2 creative).
     * {@code creativeId} is the plan creative behind the ad-grain when it can be resolved.
     */
    record CreativeRotation(String creativeId, String replacementHint) implements ActionChange {
    }

    /**
     * REQUEST_VARIANT — ask A4/J21 to generate new variants from a winning creative (J12 §5.2
     * creative). {@code winningAttributes} are the winner's attributes to exploit (from J20 when
     * available).
     */
    record VariantRequest(String winnerCreativeId, Map<String, String> winningAttributes) implements ActionChange {
    }
}
