package com.modlix.saas.adzump.service.campaign;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.modlix.saas.adzump.jooq.enums.AdzumpCampaignPlanStatus;
import com.modlix.saas.adzump.platform.RunState;

/**
 * The campaign-plan status machine (J8 §5.3). Enforces the legal transitions over the generated
 * {@link AdzumpCampaignPlanStatus} enum so no lifecycle operation can move a plan into an
 * incoherent state:
 *
 * <pre>
 *   DRAFT -&gt; VALIDATED -&gt; LAUNCHING -&gt; LIVE_PAUSED -&gt; (activate) ACTIVE &lt;-&gt; PAUSED -&gt; ARCHIVED
 * </pre>
 *
 * plus {@code PARTIALLY_LAUNCHED} (some platforms launched, others failed — retryable) and
 * {@code FAILED} (nothing launched — retryable). {@code ARCHIVED} is terminal.
 *
 * <p>Pure and static; the mutation services ({@link CampaignService}) consult it before persisting.
 */
public final class StatusMachine {

    private static final Map<AdzumpCampaignPlanStatus, Set<AdzumpCampaignPlanStatus>> ALLOWED =
            new EnumMap<>(AdzumpCampaignPlanStatus.class);

    /**
     * The states from which a (re-)launch may start. Launch is atomic in P1 (it does not persist the
     * transient {@code LAUNCHING} state, though the machine models it for future async actors), so it
     * gates on this set rather than on a {@code current -> LAUNCHING} edge, then writes the terminal
     * launch status ({@code LIVE_PAUSED} / {@code PARTIALLY_LAUNCHED} / {@code FAILED}) directly.
     * {@code PARTIALLY_LAUNCHED} and {@code FAILED} are here so a retry completes the missing
     * platforms (idempotent resume, §5.4).
     */
    private static final Set<AdzumpCampaignPlanStatus> LAUNCH_STARTABLE = EnumSet.of(
            AdzumpCampaignPlanStatus.DRAFT,
            AdzumpCampaignPlanStatus.VALIDATED,
            AdzumpCampaignPlanStatus.PARTIALLY_LAUNCHED,
            AdzumpCampaignPlanStatus.FAILED);

    static {
        ALLOWED.put(AdzumpCampaignPlanStatus.DRAFT, EnumSet.of(
                AdzumpCampaignPlanStatus.VALIDATED, AdzumpCampaignPlanStatus.LAUNCHING,
                AdzumpCampaignPlanStatus.FAILED, AdzumpCampaignPlanStatus.ARCHIVED));

        ALLOWED.put(AdzumpCampaignPlanStatus.VALIDATED, EnumSet.of(
                AdzumpCampaignPlanStatus.LAUNCHING, AdzumpCampaignPlanStatus.DRAFT,
                AdzumpCampaignPlanStatus.FAILED, AdzumpCampaignPlanStatus.ARCHIVED));

        ALLOWED.put(AdzumpCampaignPlanStatus.LAUNCHING, EnumSet.of(
                AdzumpCampaignPlanStatus.LIVE_PAUSED, AdzumpCampaignPlanStatus.PARTIALLY_LAUNCHED,
                AdzumpCampaignPlanStatus.FAILED));

        ALLOWED.put(AdzumpCampaignPlanStatus.LIVE_PAUSED, EnumSet.of(
                AdzumpCampaignPlanStatus.ACTIVE, AdzumpCampaignPlanStatus.PAUSED,
                AdzumpCampaignPlanStatus.ARCHIVED));

        ALLOWED.put(AdzumpCampaignPlanStatus.ACTIVE, EnumSet.of(
                AdzumpCampaignPlanStatus.PAUSED, AdzumpCampaignPlanStatus.ARCHIVED));

        ALLOWED.put(AdzumpCampaignPlanStatus.PAUSED, EnumSet.of(
                AdzumpCampaignPlanStatus.ACTIVE, AdzumpCampaignPlanStatus.ARCHIVED));

        ALLOWED.put(AdzumpCampaignPlanStatus.PARTIALLY_LAUNCHED, EnumSet.of(
                AdzumpCampaignPlanStatus.LAUNCHING, AdzumpCampaignPlanStatus.LIVE_PAUSED,
                AdzumpCampaignPlanStatus.ACTIVE, AdzumpCampaignPlanStatus.PAUSED,
                AdzumpCampaignPlanStatus.ARCHIVED, AdzumpCampaignPlanStatus.FAILED));

        ALLOWED.put(AdzumpCampaignPlanStatus.FAILED, EnumSet.of(
                AdzumpCampaignPlanStatus.LAUNCHING, AdzumpCampaignPlanStatus.DRAFT,
                AdzumpCampaignPlanStatus.ARCHIVED));

        // ARCHIVED is terminal.
        ALLOWED.put(AdzumpCampaignPlanStatus.ARCHIVED, EnumSet.noneOf(AdzumpCampaignPlanStatus.class));
    }

    private StatusMachine() {
    }

    /** Whether {@code from -> to} is a legal status transition. */
    public static boolean canTransition(AdzumpCampaignPlanStatus from, AdzumpCampaignPlanStatus to) {
        if (from == null || to == null)
            return false;
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** Whether a (re-)launch may start from {@code status}. */
    public static boolean canLaunchFrom(AdzumpCampaignPlanStatus status) {
        return status != null && LAUNCH_STARTABLE.contains(status);
    }

    /**
     * Maps the neutral {@link RunState} a caller asks for (activate / pause / archive) to the plan
     * status it lands in.
     */
    public static AdzumpCampaignPlanStatus toPlanStatus(RunState runState) {
        return switch (runState) {
            case ACTIVE -> AdzumpCampaignPlanStatus.ACTIVE;
            case PAUSE -> AdzumpCampaignPlanStatus.PAUSED;
            case ARCHIVED -> AdzumpCampaignPlanStatus.ARCHIVED;
        };
    }
}
