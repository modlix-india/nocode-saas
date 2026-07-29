package com.modlix.saas.adzump.service.apply;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.platform.RunState;
import com.modlix.saas.adzump.service.optimize.Action;
import com.modlix.saas.adzump.service.optimize.ActionChange;

/**
 * J13 §5.3 — translates an {@link Action} into the before/after audit payloads that the
 * {@link ActionApplier} both records and <b>replays through the one mutation spine</b> (J8
 * {@code editLive} / {@code setStatus}). Every payload is self-contained and directly replayable, which is
 * what makes an applied action reversible (§5.5): applying uses the {@code after} payload, reversing uses
 * the {@code before} payload.
 *
 * <p>Payload shape:
 * <ul>
 * <li><b>structural</b> (budget / bid / audience / negative-keyword / creative): {@code {"target":{grain},
 * "patch":{&lt;plan-body RFC-7386 merge patch&gt;}}} — replayed via {@code editLive(planId, patch)}.</li>
 * <li><b>pause</b>: {@code {"target":{grain}, "runState":"PAUSE"|"ACTIVE"}} — replayed via
 * {@code setStatus(planId, runState)}.</li>
 * </ul>
 *
 * <p><b>P1 scope note (mirrors {@code CampaignService.editLive}).</b> {@code editLive} currently applies
 * the campaign daily-budget lever to the platform SPI; a {@code SHIFT_BUDGET} is therefore translated into
 * a precise campaign daily-budget body patch (reversible to the prior budget). The bid / audience /
 * negative-keyword / creative levers are routed through the same spine as an additive, reversible record
 * under {@code verticalExtensions.appliedAdjustments} (so the change is persisted + the plan revision
 * bumps + a recompile fires) pending the object-level plan diff that will drive their specific SPI levers
 * (the same TODO {@code editLive} already carries).
 */
final class ActionPatchTranslator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TARGET = "target";
    static final String PATCH = "patch";
    static final String RUN_STATE = "runState";

    private ActionPatchTranslator() {
    }

    /** The before/after audit payloads for an action, given the campaign's current (live) state. */
    static PatchPair translate(Action action, CampaignState state) {

        JsonNode target = action.target() == null ? null : MAPPER.valueToTree(action.target());

        return switch (action.type()) {
            case PAUSE_ENTITY -> new PatchPair(
                    pausePayload(target, RunState.ACTIVE),
                    pausePayload(target, RunState.PAUSE));
            case SHIFT_BUDGET -> budgetPair(action, state, target);
            default -> adjustmentPair(action, target);
        };
    }

    /** {@code {"target":..., "runState":...}}. */
    private static ObjectNode pausePayload(JsonNode target, RunState runState) {
        ObjectNode node = MAPPER.createObjectNode();
        node.set(TARGET, target == null ? JsonNodeFactory.instance.nullNode() : target);
        node.put(RUN_STATE, runState.name());
        return node;
    }

    /** Budget: a precise campaign daily-budget body patch, before = current, after = current + shift. */
    private static PatchPair budgetPair(Action action, CampaignState state, JsonNode target) {

        Money current = state.currentDailyBudget();
        double currentAmount = amount(current);

        double delta = 0.0d;
        if (action.change() instanceof ActionChange.BudgetShift shift)
            delta = amount(shift.amount());

        String currency = current != null && current.getCurrency() != null
                ? current.getCurrency()
                : currencyOf(action);

        JsonNode beforePatch = dailyBudgetPatch(currentAmount, currency);
        JsonNode afterPatch = dailyBudgetPatch(currentAmount + delta, currency);

        return new PatchPair(structuralPayload(target, beforePatch), structuralPayload(target, afterPatch));
    }

    /** Non-budget structural lever: reversible record under {@code verticalExtensions.appliedAdjustments}. */
    private static PatchPair adjustmentPair(Action action, JsonNode target) {

        String key = action.type().getLiteral() + ":" + CampaignState.grainKey(action.target());
        JsonNode change = action.change() == null
                ? JsonNodeFactory.instance.nullNode()
                : MAPPER.valueToTree(action.change());

        JsonNode afterPatch = adjustmentPatch(key, change);
        // Reverse deletes the key (RFC 7386 null = delete), restoring the prior absence.
        JsonNode beforePatch = adjustmentPatch(key, JsonNodeFactory.instance.nullNode());

        return new PatchPair(structuralPayload(target, beforePatch), structuralPayload(target, afterPatch));
    }

    private static ObjectNode structuralPayload(JsonNode target, JsonNode patch) {
        ObjectNode node = MAPPER.createObjectNode();
        node.set(TARGET, target == null ? JsonNodeFactory.instance.nullNode() : target);
        node.set(PATCH, patch);
        return node;
    }

    private static ObjectNode dailyBudgetPatch(double amount, String currency) {
        ObjectNode money = MAPPER.createObjectNode();
        money.put("amount", BigDecimal.valueOf(amount));
        if (currency != null)
            money.put("currency", currency);
        ObjectNode budget = MAPPER.createObjectNode();
        budget.set("dailyBudget", money);
        ObjectNode patch = MAPPER.createObjectNode();
        patch.set("budget", budget);
        return patch;
    }

    private static ObjectNode adjustmentPatch(String key, JsonNode value) {
        ObjectNode adjustments = MAPPER.createObjectNode();
        adjustments.set(key, value);
        ObjectNode vext = MAPPER.createObjectNode();
        vext.set("appliedAdjustments", adjustments);
        ObjectNode patch = MAPPER.createObjectNode();
        patch.set("verticalExtensions", vext);
        return patch;
    }

    // ---- replay extraction -------------------------------------------------------------------

    /** The {@code editLive} body merge patch from a structural payload, or {@code null}. */
    static JsonNode patchOf(JsonNode payload) {
        if (payload == null)
            return null;
        JsonNode patch = payload.get(PATCH);
        return patch == null || patch.isNull() ? null : patch;
    }

    /** The {@link RunState} from a pause payload, or {@code null}. */
    static RunState runStateOf(JsonNode payload) {
        if (payload == null)
            return null;
        JsonNode rs = payload.get(RUN_STATE);
        if (rs == null || !rs.isTextual())
            return null;
        try {
            return RunState.valueOf(rs.asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String currencyOf(Action action) {
        if (action.change() instanceof ActionChange.BudgetShift shift
                && shift.amount() != null && shift.amount().getCurrency() != null)
            return shift.amount().getCurrency();
        return null;
    }

    private static double amount(Money money) {
        return money == null || money.getAmount() == null ? 0.0d : money.getAmount().doubleValue();
    }

    /** The before/after audit payloads produced for an action. */
    record PatchPair(JsonNode before, JsonNode after) {
    }
}
